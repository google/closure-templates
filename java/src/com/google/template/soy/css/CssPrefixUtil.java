/*
 * Copyright 2023 Google Inc.
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

package com.google.template.soy.css;

import com.google.common.base.CaseFormat;
import com.google.common.collect.Streams;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.TemplateNode;
import java.util.Objects;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** Utilities related to resolving the '%' prefix in the built-in css() function. */
public class CssPrefixUtil {

  private CssPrefixUtil() {}

  @Nullable
  public static String getNamespacePrefix(SoyFileNode file) {
    if (file.getCssPrefix() != null) {
      return file.getCssPrefix();
    } else if (file.getCssBaseNamespace() != null) {
      return toCamelCase(file.getCssBaseNamespace());
    } else if (!file.getRequiredCssNamespaces().isEmpty()) {
      return toCamelCase(file.getRequiredCssNamespaces().get(0));
    }
    return null;
  }

  @Nullable
  public static String getTemplatePrefix(TemplateNode template, @Nullable String namespacePrefix) {
    if (template.getCssBaseNamespace() != null) {
      return toCamelCase(template.getCssBaseNamespace());
    }
    return namespacePrefix;
  }

  public static String getTemplatePrefix(TemplateNode template) {
    return getTemplatePrefix(
        template, getNamespacePrefix(template.getNearestAncestor(SoyFileNode.class)));
  }

  public static Stream<String> getRequiredCssSymbols(TemplateNode template) {
    SoyFileNode fileNode = template.getNearestAncestor(SoyFileNode.class);
    return Streams.concat(
        template.getRequiredCssNamespaces().stream(),
        fileNode.getRequiredCssNamespaces().stream(),
        fileNode.getRequiredCssPaths().stream()
            .map(
                cssPath -> {
                  if (cssPath.resolvedPath().isPresent()) {
                    return cssPath.resolvedPath().get();
                  }
                  return null;
                })
            .filter(Objects::nonNull));
  }

  private static String toCamelCase(String packageName) {
    String packageNameWithDashes = packageName.replace('.', '-');
    return CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_CAMEL, packageNameWithDashes);
  }
}
