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
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.base.internal.QuoteStyle;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import javax.annotation.Nullable;

/** A name-attribute pair (e.g. {@code <name>="<attribute>"}) as parsed from a soy command. */
public final class CommandTagAttribute {

  private static final SoyErrorKind DUPLICATE_ATTRIBUTE =
      SoyErrorKind.of("Attribute ''{0}'' was already specified.");
  private static final SoyErrorKind INVALID_ATTRIBUTE =
      SoyErrorKind.of("Invalid value for attribute ''{0}'', expected {1}.");
  private static final SoyErrorKind INVALID_ATTRIBUTE_LIST =
      SoyErrorKind.of("Invalid value for attribute ''{0}'', expected one of {1}.");
  private static final SoyErrorKind INVALID_CSS_BASE_NAMESPACE_NAME =
      SoyErrorKind.of("Invalid CSS base namespace name ''{0}''.");
  private static final SoyErrorKind INVALID_REQUIRE_CSS_ATTRIBUTE =
      SoyErrorKind.of("Invalid required CSS namespace name ''{0}'', expected an identifier.");
  public static final SoyErrorKind MISSING_ATTRIBUTE =
      SoyErrorKind.of("Missing required attribute ''{0}'' in ''{1}''.");
  public static final SoyErrorKind UNSUPPORTED_ATTRIBUTE_KEY =
      SoyErrorKind.of("Unsupported attribute ''{0}'' for ''{1}'' tag, expected one of {2}.");
  public static final SoyErrorKind UNSUPPORTED_ATTRIBUTE_KEY_SINGLE =
      SoyErrorKind.of("Unsupported attribute ''{0}'' for ''{1}'' tag, expected ''{2}''.");
  private static final SoyErrorKind EXPECTED_A_SINGLE_EXPRESSION =
      SoyErrorKind.of("Expected a single expression for a {0} attribute.");
  static final SoyErrorKind NAMESPACE_STRICTHTML_ATTRIBUTE =
      SoyErrorKind.of("''stricthtml=\"false\"'' can only be set on individual templates.");
  static final SoyErrorKind EXPLICIT_DEFAULT_ATTRIBUTE =
      SoyErrorKind.of("''{0}=\"{1}\"'' is the default, no need to set it.");
  static final SoyErrorKind CSS_PREFIX_AND_CSS_BASE =
      SoyErrorKind.of("Cssbase and cssprefix cannot both be set at the same time.");

  private static final Splitter SPLITTER = Splitter.on(',').trimResults();

  /**
   * A node that contains command tag attributes. Some examples of this include calls, templates,
   * msg, etc.
   */
  public static interface CommandTagAttributesHolder extends SoyNode {
    List<CommandTagAttribute> getAttributes();

    SourceLocation getOpenTagLocation();
  }

  /**
   * Identifies duplicate attributes, reports an error for each one, and removes them from the
   * {@link Iterable}.
   */
  @SuppressWarnings("unused") // used by parser
  public static void removeDuplicatesAndReportErrors(
      Iterable<CommandTagAttribute> attrs, ErrorReporter errorReporter) {
    Set<String> seenAttributes = new HashSet<>();
    for (Iterator<CommandTagAttribute> iterator = attrs.iterator(); iterator.hasNext(); ) {
      CommandTagAttribute attr = iterator.next();
      Identifier name = attr.getName();
      if (!seenAttributes.add(name.identifier())) {
        errorReporter.report(name.location(), DUPLICATE_ATTRIBUTE, name.identifier());
        iterator.remove();
      }
    }
  }

  private final Identifier key;
  private final SourceLocation valueLocation;
  private final QuoteStyle quoteStyle;
  private final SourceLocation sourceLocation;
  // either value or valueExprList must be set, but not both.
  @Nullable private final String value;
  @Nullable private final ImmutableList<ExprRootNode> valueExprList;

  public CommandTagAttribute(
      Identifier key,
      QuoteStyle quoteStyle,
      String value,
      SourceLocation valueLocation,
      SourceLocation wholeAttributeLocation) {
    checkArgument(
        key.type() == Identifier.Type.SINGLE_IDENT, "expected a single identifier, got: %s", key);
    this.key = checkNotNull(key);
    this.quoteStyle = checkNotNull(quoteStyle);
    this.sourceLocation = wholeAttributeLocation;
    this.valueLocation = checkNotNull(valueLocation);
    this.value = checkNotNull(value);
    this.valueExprList = null;
  }

  public CommandTagAttribute(
      Identifier key,
      QuoteStyle quoteStyle,
      ImmutableList<ExprNode> valueExprList,
      SourceLocation sourceLocation) {
    checkArgument(
        key.type() == Identifier.Type.SINGLE_IDENT, "expected a single identifier, got: %s", key);
    checkArgument(valueExprList.size() >= 1);
    this.key = checkNotNull(key);
    this.quoteStyle = checkNotNull(quoteStyle);
    this.sourceLocation = sourceLocation;
    this.valueLocation =
        valueExprList
            .get(0)
            .getSourceLocation()
            .extend(Iterables.getLast(valueExprList).getSourceLocation());
    this.value = null;
    this.valueExprList = ExprRootNode.wrap(valueExprList);
  }

  public CommandTagAttribute copy(CopyState copyState) {
    if (this.value == null) {
      return new CommandTagAttribute(
          key,
          quoteStyle,
          valueExprList.stream()
              .map(expr -> expr.getRoot().copy(copyState))
              .collect(toImmutableList()),
          sourceLocation);
    }
    return new CommandTagAttribute(key, quoteStyle, value, valueLocation, sourceLocation);
  }

  /** Returns the name. It is guaranteed to be a single identifier. */
  public Identifier getName() {
    return key;
  }

  /** Returns true if the attribute name is equal to the given string. */
  public boolean hasName(String name) {
    return key.identifier().equals(name);
  }

  /** Gets the source location of the attribute. */
  public SourceLocation getSourceLocation() {
    return sourceLocation;
  }

  /** Returns the string value. Do not call on an expression attribute. */
  public String getValue() {
    return checkNotNull(value);
  }

  public SourceLocation getValueLocation() {
    return valueLocation;
  }

  public QuoteStyle getQuoteStyle() {
    return quoteStyle;
  }

  public int valueAsInteger(ErrorReporter errorReporter, int defaultValue) {
    checkState(valueExprList == null);

    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      errorReporter.report(valueLocation, INVALID_ATTRIBUTE, key.identifier(), "an integer");
      return defaultValue;
    }
  }

  public OptionalLong valueAsOptionalLong(ErrorReporter errorReporter) {
    checkState(valueExprList == null);

    try {
      return OptionalLong.of(Long.parseLong(value));
    } catch (NumberFormatException e) {
      errorReporter.report(valueLocation, INVALID_ATTRIBUTE, key.identifier(), "a number");
      return OptionalLong.empty();
    }
  }

  boolean valueAsEnabled(ErrorReporter errorReporter) {
    checkState(valueExprList == null);

    if ("true".equals(value)) {
      return true;
    } else if ("false".equals(value)) {
      errorReporter.report(valueLocation, EXPLICIT_DEFAULT_ATTRIBUTE, key.identifier(), "false");
    } else {
      errorReporter.report(valueLocation, INVALID_ATTRIBUTE, key.identifier(), "true");
    }
    return false;
  }

  boolean valueAsDisabled(ErrorReporter errorReporter) {
    checkState(valueExprList == null);

    if ("false".equals(value)) {
      return true;
    } else if ("true".equals(value)) {
      errorReporter.report(valueLocation, EXPLICIT_DEFAULT_ATTRIBUTE, key.identifier(), "true");
    } else {
      errorReporter.report(valueLocation, INVALID_ATTRIBUTE, key.identifier(), "false");
    }
    return false;
  }

  ImmutableList<String> valueAsRequireCss(ErrorReporter errorReporter) {
    checkState(valueExprList == null);

    Iterable<String> namespaces = SPLITTER.split(value);
    boolean hasError = false;
    for (String namespace : namespaces) {
      if (!BaseUtils.isDottedIdentifier(namespace)) {
        errorReporter.report(valueLocation, INVALID_REQUIRE_CSS_ATTRIBUTE, namespace);
        hasError = true;
      }
    }
    return hasError ? ImmutableList.of() : ImmutableList.copyOf(namespaces);
  }

  ImmutableList<String> valueAsRequireCssPath() {
    checkState(valueExprList == null);

    return ImmutableList.copyOf(SPLITTER.split(value));
  }

  @Nullable
  Visibility valueAsVisibility(ErrorReporter errorReporter) {
    checkState(valueExprList == null);

    Visibility visibility = Visibility.forAttributeValue(value);
    if (visibility == Visibility.PUBLIC) {
      errorReporter.report(
          valueLocation,
          EXPLICIT_DEFAULT_ATTRIBUTE,
          key.identifier(),
          Visibility.PUBLIC.getAttributeValue());
    } else if (visibility == null) {
      errorReporter.report(
          valueLocation,
          INVALID_ATTRIBUTE,
          key.identifier(),
          Visibility.PRIVATE.getAttributeValue());
    }
    return visibility;
  }

  @Nullable
  WhitespaceMode valueAsWhitespaceMode(ErrorReporter errorReporter) {
    checkState(valueExprList == null);

    WhitespaceMode whitespaceMode = WhitespaceMode.forAttributeValue(value);

    if (whitespaceMode == WhitespaceMode.JOIN) {
      errorReporter.report(
          valueLocation,
          EXPLICIT_DEFAULT_ATTRIBUTE,
          key.identifier(),
          WhitespaceMode.JOIN.getAttributeValue());
    } else if (whitespaceMode == null) {
      errorReporter.report(
          valueLocation,
          INVALID_ATTRIBUTE_LIST,
          key.identifier(),
          WhitespaceMode.getAttributeValues());
    }

    return whitespaceMode;
  }

  public Optional<SanitizedContentKind> valueAsContentKind(ErrorReporter errorReporter) {
    checkState(valueExprList == null);

    Optional<SanitizedContentKind> contentKind = SanitizedContentKind.fromAttributeValue(value);
    if (!contentKind.isPresent()) {
      errorReporter.report(
          valueLocation,
          INVALID_ATTRIBUTE_LIST,
          key.identifier(),
          SanitizedContentKind.attributeValues().asList());
    }
    return contentKind;
  }

  String valueAsCssBase(ErrorReporter errorReporter) {
    checkState(valueExprList == null);

    if (!BaseUtils.isDottedIdentifier(value)) {
      errorReporter.report(valueLocation, INVALID_CSS_BASE_NAMESPACE_NAME, value);
    }
    return value;
  }

  /** Returns the value as an expression. Only call on an expression attribute. */
  public ExprRootNode valueAsExpr(ErrorReporter reporter) {
    checkState(value == null);
    if (valueExprList.size() > 1) {
      reporter.report(
          valueExprList.get(1).getSourceLocation(), EXPECTED_A_SINGLE_EXPRESSION, key.identifier());
      // Return the first expr to avoid an NPE in CallNode ctor.
      return valueExprList.get(0);
    }
    return Iterables.getOnlyElement(valueExprList);
  }

  public boolean hasExprValue() {
    return valueExprList != null;
  }

  /** Returns the value as an expression list. Only call on an expression list attribute. */
  public ImmutableList<ExprRootNode> valueAsExprList() {
    checkState(value == null);
    return checkNotNull(valueExprList);
  }

  @Override
  public String toString() {
    String valueStr =
        (value != null)
            ? BaseUtils.escapeToSoyString(value, false, quoteStyle)
            : quoteStyle.getQuoteChar()
                + SoyTreeUtils.toSourceString(valueExprList)
                + quoteStyle.getQuoteChar();
    return key.identifier() + "=" + valueStr;
  }

  public String toSourceString() {
    String valueStr =
        quoteStyle.getQuoteChar()
            + ((value != null) ? value : SoyTreeUtils.toSourceString(valueExprList))
            + quoteStyle.getQuoteChar();
    return key.identifier() + "=" + valueStr;
  }
}
