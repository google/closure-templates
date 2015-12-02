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

import static com.google.template.soy.soytree.AutoescapeMode.parseAutoEscapeMode;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;

import java.util.List;

import javax.annotation.Nullable;

/**
 * A {@code {namespace ..}} declaration.
 */
public final class NamespaceDeclaration {
  /** The default autoescape mode if none is specified in the command text. */
  static final AutoescapeMode DEFAULT_FILE_WIDE_DEFAULT_AUTOESCAPE_MODE = 
      AutoescapeMode.STRICT;

  // A 'null' instance for classes with no namespace tag.
  public static final NamespaceDeclaration NULL = new NamespaceDeclaration();
  private static final SoyErrorKind UNSUPPORTED_ATTRIBUTE_KEY =
      SoyErrorKind.of("Unsupported attribute ''{0}'', expected one of [{1}].");

  private final String namespace;
  private final Optional<AutoescapeMode> namespaceAutoescapeMode;
  private final ImmutableList<String> requiredCssNamespaces;
  private final String cssBaseNamespace;

  public NamespaceDeclaration(
      String namespace, List<NameAttributePair> attrs, ErrorReporter errorReporter) {
    AutoescapeMode defaultAutoescapeMode = null;
    ImmutableList<String> requiredCssNamespaces = ImmutableList.of();
    String cssBaseNamespace = null;
    for (NameAttributePair attr : attrs) {
      switch (attr.getName()) {
        case "autoescape":
          defaultAutoescapeMode =
              parseAutoEscapeMode(attr.getValue(), attr.getLocation(), errorReporter);
          break;
        case "requirecss":
          requiredCssNamespaces = RequirecssUtils.parseRequirecssAttr(attr.getValue(),
              attr.getLocation());
          break;
        case "cssbase":
          cssBaseNamespace = attr.getValue();
          break;
        default:
          errorReporter.report(
              attr.getLocation(),
              UNSUPPORTED_ATTRIBUTE_KEY,
              attr.getName(),
              ImmutableList.of("autoescape", "requirecss", "cssbase"));
          break;
      }
    }

    this.namespace = namespace;
    this.namespaceAutoescapeMode = Optional.fromNullable(defaultAutoescapeMode);
    this.requiredCssNamespaces = requiredCssNamespaces;
    this.cssBaseNamespace = cssBaseNamespace;
  }

  private NamespaceDeclaration() {
    this.namespace = null;
    this.namespaceAutoescapeMode = Optional.absent();
    this.requiredCssNamespaces = ImmutableList.of();
    this.cssBaseNamespace = null;
  }
  
  public boolean isDefined() {
    return this != NULL;
  }

  public AutoescapeMode getDefaultAutoescapeMode() {
    return namespaceAutoescapeMode.or(DEFAULT_FILE_WIDE_DEFAULT_AUTOESCAPE_MODE);
  }

  public Optional<AutoescapeMode> getAutoescapeMode() {
    return namespaceAutoescapeMode;
  }

  @Nullable public String getNamespace() {
    return namespace;
  }

  public ImmutableList<String> getRequiredCssNamespaces() {
    return requiredCssNamespaces;
  }

  @Nullable public String getCssBaseNamespace() {
    return cssBaseNamespace;
  }

}
