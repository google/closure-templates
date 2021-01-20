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
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
  static TemplateAliases createTemplateAliases(SoyFileNode fileNode) {
    Map<String, String> aliasMap = new HashMap<>();
    Set<String> allAliases = new HashSet<>();

    // Go through templates first and just alias them to their local name.
    for (TemplateNode templateNode : fileNode.getTemplates()) {
      if (templateNode instanceof TemplateDelegateNode) {
        continue;
      }
      String partialName = templateNode.getLocalTemplateSymbol();
      String fullyQualifiedName = templateNode.getTemplateName();

      // Need to start the alias with something that cannot be a part of a reserved
      // JavaScript identifier like 'function' or 'catch'.
      String alias = "$" + partialName;
      aliasMap.put(fullyQualifiedName, alias);
      allAliases.add(alias);
    }

    // Go through all call sites looking for foreign template calls and create an alias for them.
    Map<String, String> foundNamespaces = new HashMap<>();
    for (TemplateLiteralNode templateLiteralNode :
        SoyTreeUtils.getAllNodesOfType(fileNode, TemplateLiteralNode.class)) {
      String fullyQualifiedName = templateLiteralNode.getResolvedName();

      // We could have either encountered the foreign fully qualified name before or it could belong
      // to a local template.
      if (aliasMap.containsKey(fullyQualifiedName)) {
        continue;
      }
      int lastDotPosition = fullyQualifiedName.lastIndexOf('.');
      checkState(lastDotPosition != -1);
      String namespace = fullyQualifiedName.substring(0, lastDotPosition);
      String alias = foundNamespaces.get(namespace);
      if (alias == null) {
        alias = MODULE_ALIAS_PREFIX + namespace.replace('.', '$');
        checkState(allAliases.add(alias)); // sanity check that all aliases are unique
        foundNamespaces.put(namespace, alias);
      }
      aliasMap.put(fullyQualifiedName, alias + fullyQualifiedName.substring(lastDotPosition));
    }

    return new Aliases(aliasMap, foundNamespaces);
  }
}
