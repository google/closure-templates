/*
 * Copyright 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.template.soy.jssrc.internal;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.soytree.FileSetMetadata;
import com.google.template.soy.soytree.ImportNode.ImportType;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.TemplateDelegateNode;
import java.util.HashMap;
import java.util.Map;

final class AliasUtils {
  private AliasUtils() {}

  private static final class Aliases implements TemplateAliases {
    final ImmutableMap<String, String> aliasMapping;
    final ImmutableMap<String, String> namespaceAliases;

    Aliases(Map<String, String> aliasMapping, Map<String, String> namespaceAliases) {
      this.aliasMapping = ImmutableMap.copyOf(aliasMapping);
      this.namespaceAliases = ImmutableMap.copyOf(namespaceAliases);
    }

    @Override
    public String get(String fullyQualifiedName) {
      String alias = aliasMapping.get(fullyQualifiedName);
      if (alias != null) {
        return alias;
      }
      int lastDotPosition = fullyQualifiedName.lastIndexOf('.');
      checkState(lastDotPosition != -1);
      String namespace = fullyQualifiedName.substring(0, lastDotPosition);
      String relativeName = fullyQualifiedName.substring(lastDotPosition + 1);
      return getNamespaceAlias(namespace) + '.' + relativeName;
    }

    @Override
    public String getNamespaceAlias(String namespace) {
      return namespaceAliases.get(namespace);
    }
  }

  private static final String MODULE_ALIAS_PREFIX = "$soy$";

  static final TemplateAliases IDENTITY_ALIASES =
      new TemplateAliases() {
        @Override
        public String get(String fullyQualifiedName) {
          return fullyQualifiedName;
        }

        @Override
        public String getNamespaceAlias(String namespace) {
          throw new UnsupportedOperationException();
        }
      };

  static boolean isExternalFunction(String alias) {
    return alias.startsWith(MODULE_ALIAS_PREFIX);
  }

  /**
   * Creates a mapping for assigning and looking up the variable alias for templates both within a
   * file and referenced from external files. For local files, the alias is just the local
   * template's name. For external templates, the alias is an identifier that is unique for that
   * template.
   *
   * <p>For V1 templates, no alias is generated and the template should be called by the fully
   * qualified name.
   *
   * @param fileNode The {@link SoyFileNode} to create an alias mapping for.
   * @return A {@link TemplateAliases} to look up aliases with.
   */
  static TemplateAliases createTemplateAliases(
      SoyFileNode fileNode, FileSetMetadata fileSetMetadata) {
    Map<String, String> aliasMap = new HashMap<>();
    Map<String, String> foundNamespaces = new HashMap<>();

    // Index local templates by their FQN.
    fileNode.getTemplates().stream()
        .filter(t -> !(t instanceof TemplateDelegateNode))
        .forEach(
            templateNode -> {
              // Need to start the alias with something that cannot be a part of a reserved
              // JavaScript identifier like 'function' or 'catch'.
              String alias = "$" + templateNode.getLocalTemplateSymbol();
              aliasMap.put(templateNode.getTemplateName(), alias);
            });
    // Index local constants by their local name.
    fileNode
        .getConstants()
        .forEach(
            constNode -> aliasMap.put(constNode.getVar().name(), "$" + constNode.getVar().name()));

    fileNode.getImports().stream()
        .filter(i -> i.getImportType() == ImportType.TEMPLATE)
        .map(i -> fileSetMetadata.getNamespaceForPath(i.getSourceFilePath()))
        .distinct()
        .forEach(
            namespace -> {
              String alias = MODULE_ALIAS_PREFIX + namespace.replace('.', '$');
              foundNamespaces.put(namespace, alias);
            });

    return new Aliases(aliasMap, foundNamespaces);
  }
}
