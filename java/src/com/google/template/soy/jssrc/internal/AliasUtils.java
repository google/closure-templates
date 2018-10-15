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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateBasicNode;
import com.google.template.soy.soytree.TemplateElementNode;
import com.google.template.soy.soytree.TemplateNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class AliasUtils {
  private AliasUtils() {}

  private static final class Aliases implements TemplateAliases {
    final ImmutableMap<String, String> aliasMapping;

    Aliases(Map<String, String> aliasMapping) {
      this.aliasMapping = ImmutableMap.copyOf(aliasMapping);
    }

    @Override
    public String get(String fullyQualifiedName) {
      String alias = aliasMapping.get(fullyQualifiedName);
      Preconditions.checkState(alias != null);
      return alias;
    }
  }

  private static final String TEMPLATE_ALIAS_PREFIX = "$templateAlias";

  static final TemplateAliases IDENTITY_ALIASES =
      new TemplateAliases() {
        @Override
        public String get(String fullyQualifiedName) {
          return fullyQualifiedName;
        }
      };

  static boolean isExternalFunction(String alias) {
    return alias.startsWith(TEMPLATE_ALIAS_PREFIX);
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
    Set<String> localTemplates = new HashSet<>();
    int counter = 0;

    ImmutableList.Builder<TemplateNode> templates = ImmutableList.builder();
    templates
        .addAll(SoyTreeUtils.getAllNodesOfType(fileNode, TemplateBasicNode.class))
        .addAll(SoyTreeUtils.getAllNodesOfType(fileNode, TemplateElementNode.class));
    // Go through templates first and just alias them to their local name.
    for (TemplateNode templateNode : templates.build()) {
      String partialName = templateNode.getPartialTemplateName();
      String fullyQualifiedName = templateNode.getTemplateName();
      localTemplates.add(fullyQualifiedName);

      Preconditions.checkState(partialName != null, "Aliasing not supported for V1 templates");

      // Need to start the alias with something that cannot be a part of a reserved
      // JavaScript identifier like 'function' or 'catch'.
      String alias = "$" + partialName.substring(1);
      aliasMap.put(fullyQualifiedName, alias);
    }

    // Go through all call sites looking for foreign template calls and create an alias for them.
    for (CallBasicNode callBasicNode :
        SoyTreeUtils.getAllNodesOfType(fileNode, CallBasicNode.class)) {
      String fullyQualifiedName = callBasicNode.getCalleeName();

      // We could have either encountered the foreign fully qualified name before or it could belong
      // to a local template.
      if (localTemplates.contains(fullyQualifiedName) || aliasMap.containsKey(fullyQualifiedName)) {
        continue;
      }

      String alias = TEMPLATE_ALIAS_PREFIX + (++counter);
      aliasMap.put(fullyQualifiedName, alias);
    }

    return new Aliases(aliasMap);
  }
}
