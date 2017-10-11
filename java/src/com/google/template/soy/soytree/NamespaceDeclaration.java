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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.error.ErrorReporter;
import java.util.List;
import javax.annotation.Nullable;

/** A {@code {namespace ..}} declaration. */
public final class NamespaceDeclaration {
  /** The default autoescape mode if none is specified in the command text. */
  private static final AutoescapeMode DEFAULT_FILE_WIDE_DEFAULT_AUTOESCAPE_MODE =
      AutoescapeMode.STRICT;

  private final Identifier namespace;
  @Nullable private final AutoescapeMode autoescapeMode;
  @Nullable private final SourceLocation autoescapeModeLocation;
  private final ImmutableList<String> requiredCssNamespaces;
  private final String cssBaseNamespace;

  final ImmutableList<CommandTagAttribute> attrs;

  public NamespaceDeclaration(
      Identifier namespace, List<CommandTagAttribute> attrs, ErrorReporter errorReporter) {
    AutoescapeMode autoescapeMode = null;
    SourceLocation autoescapeModeLocation = null;
    ImmutableList<String> requiredCssNamespaces = ImmutableList.of();
    String cssBaseNamespace = null;
    for (CommandTagAttribute attr : attrs) {
      switch (attr.getName().identifier()) {
        case "autoescape":
          autoescapeMode = attr.valueAsAutoescapeMode(errorReporter);
          autoescapeModeLocation = attr.getValueLocation();
          break;
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
              ImmutableList.of("autoescape", "cssbase", "requirecss"));
          break;
      }
    }

    this.namespace = namespace;
    this.autoescapeMode = autoescapeMode;
    this.autoescapeModeLocation = autoescapeModeLocation;
    this.requiredCssNamespaces = requiredCssNamespaces;
    this.cssBaseNamespace = cssBaseNamespace;
    this.attrs = ImmutableList.copyOf(attrs);
  }

  public AutoescapeMode getDefaultAutoescapeMode() {
    return autoescapeMode == null ? DEFAULT_FILE_WIDE_DEFAULT_AUTOESCAPE_MODE : autoescapeMode;
  }

  /**
   * Returns the location of {@code autoescape} attribute.
   *
   * @throws IllegalStateException if there is no autoescape attribute.
   */
  public SourceLocation getAutoescapeModeLocation() {
    checkState(autoescapeModeLocation != null, "there is no autoescape attribute");
    return autoescapeModeLocation;
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
