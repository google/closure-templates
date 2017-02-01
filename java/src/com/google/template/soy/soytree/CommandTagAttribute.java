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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.base.internal.Identifier.Type;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.internalutils.NodeContentKinds;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import javax.annotation.Nullable;

/** A name-attribute pair (e.g. {@code <name>="<attribute>"}) as parsed from a soy command. */
public final class CommandTagAttribute {
  private static final SoyErrorKind DUPLICATE_ATTRIBUTE =
      SoyErrorKind.of("Attribute was already specified.");

  static final SoyErrorKind UNSUPPORTED_ATTRIBUTE_KEY =
      SoyErrorKind.of("Unsupported attribute ''{0}'', expected one of {1}.");

  private static final SoyErrorKind INVALID_ATTRIBUTE =
      SoyErrorKind.of("Invalid attribute value, expected one of {1}.");

  private static final SoyErrorKind INVALID_REQUIRE_CSS_ATTRIBUTE =
      SoyErrorKind.of("Invalid required CSS namespace name ''{0}'', expected an identifier.");

  private static final SoyErrorKind INVALID_CSS_BASE_NAMESPACE_NAME =
      SoyErrorKind.of("Invalid CSS base namespace name ''{0}''");

  /**
   * Identifies duplicates and reports an error for each one and removes them from the {@link
   * Iterable}
   */
  public static void removeDuplicatesAndReportErrors(
      Iterable<CommandTagAttribute> attrs, ErrorReporter errorReporter) {
    Set<String> seenAttributes = new HashSet<>();
    for (Iterator<CommandTagAttribute> iterator = attrs.iterator(); iterator.hasNext(); ) {
      CommandTagAttribute attr = iterator.next();
      Identifier name = attr.getName();
      if (!seenAttributes.add(name.identifier())) {
        errorReporter.report(name.location(), CommandTagAttribute.DUPLICATE_ATTRIBUTE);
        iterator.remove();
      }
    }
  }

  private final SourceLocation valueLocation;
  private final Identifier key;
  private final String value;

  public CommandTagAttribute(Identifier key, String value, SourceLocation valueLocation) {
    checkArgument(key.type() == Type.SINGLE_IDENT, "expected a single identifier, got: %s", key);
    this.key = checkNotNull(key);
    this.value = checkNotNull(value);
    this.valueLocation = checkNotNull(valueLocation);
  }

  /** Returns the name. It is guaranteed to be a single identifier. */
  public Identifier getName() {
    return key;
  }

  public String getValue() {
    return value;
  }

  SourceLocation getValueLocation() {
    return valueLocation;
  }

  boolean valueAsBoolean(ErrorReporter errorReporter, boolean defaultValue) {
    if ("true".equals(value)) {
      return true;
    } else if ("false".equals(value)) {
      return false;
    } else {
      errorReporter.report(
          valueLocation, INVALID_ATTRIBUTE, key.identifier(), ImmutableList.of("true", "false"));
      return defaultValue;
    }
  }

  StrictHtmlMode valueAsStrictHtmlMode(ErrorReporter errorReporter) {
    if ("true".equals(value)) {
      return StrictHtmlMode.YES;
    } else if ("false".equals(value)) {
      return StrictHtmlMode.NO;
    } else {
      errorReporter.report(
          valueLocation, INVALID_ATTRIBUTE, key.identifier(), ImmutableList.of("true", "false"));
      return StrictHtmlMode.UNSET;
    }
  }

  ImmutableList<String> valueAsRequireCss(ErrorReporter errorReporter) {
    String[] namespaces = value.trim().split("\\s*,\\s*");
    boolean hasError = false;
    for (String namespace : namespaces) {
      if (!BaseUtils.isDottedIdentifier(namespace)) {
        errorReporter.report(valueLocation, INVALID_REQUIRE_CSS_ATTRIBUTE, namespace);
        hasError = true;
      }
    }
    return hasError ? ImmutableList.<String>of() : ImmutableList.copyOf(namespaces);
  }

  AutoescapeMode valueAsAutoescapeMode(ErrorReporter errorReporter) {
    AutoescapeMode mode = AutoescapeMode.forAttributeValue(value);
    if (mode == null) {
      mode = AutoescapeMode.STRICT; // default for unparsed
      errorReporter.report(
          valueLocation,
          INVALID_ATTRIBUTE,
          key.identifier(),
          ImmutableList.copyOf(AutoescapeMode.getAttributeValues()));
    }
    return mode;
  }

  @Nullable
  Visibility valueAsVisibility(ErrorReporter errorReporter) {
    Visibility visibility = Visibility.forAttributeValue(value);
    if (visibility == null) {
      errorReporter.report(
          valueLocation,
          INVALID_ATTRIBUTE,
          key.identifier(),
          ImmutableList.copyOf(Visibility.getAttributeValues()));
    }
    return visibility;
  }

  @Nullable
  ContentKind valueAsContentKind(ErrorReporter errorReporter) {
    ContentKind contentKind = NodeContentKinds.forAttributeValue(value);
    if (contentKind == null) {
      errorReporter.report(
          valueLocation,
          INVALID_ATTRIBUTE,
          key.identifier(),
          ImmutableList.copyOf(NodeContentKinds.getAttributeValues()));
    }
    return contentKind;
  }

  String valueAsCssBase(ErrorReporter errorReporter) {
    if (!BaseUtils.isDottedIdentifier(value)) {
      errorReporter.report(valueLocation, INVALID_CSS_BASE_NAMESPACE_NAME, value);
    }
    return value;
  }

  @Override
  public String toString() {
    return key.identifier() + "=\"" + value.replace("\"", "\\\"") + "\"";
  }
}
