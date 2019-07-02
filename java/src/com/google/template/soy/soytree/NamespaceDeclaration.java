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

package com.google.template.soy.soytree;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import java.util.List;
import javax.annotation.Nullable;

/** A {@code {namespace ..}} declaration. */
public final class NamespaceDeclaration {
  private final Identifier namespace;
  private final ImmutableList<String> requiredCssNamespaces;
  private final String cssBaseNamespace;

  final ImmutableList<CommandTagAttribute> attrs;

  public NamespaceDeclaration(
      Identifier namespace, List<CommandTagAttribute> attrs, ErrorReporter errorReporter) {
    ImmutableList<String> requiredCssNamespaces = ImmutableList.of();
    String cssBaseNamespace = null;
    for (CommandTagAttribute attr : attrs) {
      switch (attr.getName().identifier()) {
        case "requirecss":
          requiredCssNamespaces = attr.valueAsRequireCss(errorReporter);
          break;
        case "cssbase":
          cssBaseNamespace = attr.getValue();
          break;
        case "stricthtml":
          errorReporter.report(
              attr.getName().location(), CommandTagAttribute.NAMESPACE_STRICTHTML_ATTRIBUTE);
          break;
        default:
          errorReporter.report(
              attr.getName().location(),
              CommandTagAttribute.UNSUPPORTED_ATTRIBUTE_KEY,
              attr.getName().identifier(),
              "namespace",
              ImmutableList.of("cssbase", "requirecss"));
          break;
      }
    }

    this.namespace = namespace;
    this.requiredCssNamespaces = requiredCssNamespaces;
    this.cssBaseNamespace = cssBaseNamespace;
    this.attrs = ImmutableList.copyOf(attrs);
  }

  public NamespaceDeclaration copy(CopyState copyState) {
    return new NamespaceDeclaration(
        namespace,
        attrs.stream().map(attr -> attr.copy(copyState)).collect(ImmutableList.toImmutableList()),
        ErrorReporter.exploding());
  }

  public String getNamespace() {
    return namespace.identifier();
  }

  ImmutableList<String> getRequiredCssNamespaces() {
    return requiredCssNamespaces;
  }

  @Nullable
  String getCssBaseNamespace() {
    return cssBaseNamespace;
  }

  /** Returns an approximation of what the original source for this namespace looked like. */
  public String toSourceString() {
    return "{namespace "
        + namespace.identifier()
        + (attrs.isEmpty() ? "" : " " + Joiner.on(' ').join(attrs))
        + "}\n";
  }
}
