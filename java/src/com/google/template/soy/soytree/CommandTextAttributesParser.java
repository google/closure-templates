/*
 * Copyright 2008 Google Inc.
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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprparse.SoyParsingContext;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A class for parsing attributes out of command text.
 *
 */
public final class CommandTextAttributesParser {

  public static final SoyErrorKind MALFORMED_ATTRIBUTES =
      SoyErrorKind.of("Malformed attributes in ''{0}'' command text ({1}).");
  public static final SoyErrorKind UNSUPPORTED_ATTRIBUTE =
      SoyErrorKind.of("Unsupported attribute ''{0}'' in ''{1}'' command text ({2}).");
  private static final SoyErrorKind DUPLICATE_ATTRIBUTE =
      SoyErrorKind.of("Duplicate attribute ''{0}'' in ''{1}'' command text ({2}).");
  private static final SoyErrorKind INVALID_ATTRIBUTE_VALUE =
      SoyErrorKind.of(
          "Invalid value for attribute ''{0}'' in ''{1}'' command text ({2}). "
              + "Valid values are {3}.");
  private static final SoyErrorKind MISSING_REQUIRED_ATTRIBUTE =
      SoyErrorKind.of("Missing required attribute ''{0}'' in ''{1}'' command text ({2}).");

  /**
   * Regex pattern for an attribute in command text. Note group(1) is attribute name, group(2) is
   * attribute value. E.g. data="$boo" parses into group(1)="data" and group(2)="$boo".
   */
  private static final Pattern ATTRIBUTE_TEXT =
      Pattern.compile(
          "([a-zA-Z][a-zA-Z0-9-]*) \\s* = \\s* \" ( (?:[^\"\\\\]+ | \\\\.)*+ ) \" \\s*",
          Pattern.COMMENTS | Pattern.DOTALL);

  /**
   * Regexp pattern to unescape attribute values. group(1) holds the escaped character. The
   * backslash used for escaping is removed only if it is followed by a backslash or a quote. This
   * is to support templates created before introduction of escaping. a\b\c becomes a\b\c.
   */
  private static final Pattern ATTRIBUTE_VALUE_ESCAPE = Pattern.compile("\\\\([\"\\\\])");

  /** The name of the Soy command handled by this parser. Only used in error messages. */
  private final String commandName;

  /** The set of this parser's supported attributes. */
  private final Set<Attribute> supportedAttributes;

  /** The set of names of the supported attributes. */
  private final Set<String> supportedAttributeNames;

  /**
   * @param commandName The name of the Soy command that this parser handles. Only used in
   *     generating error messages when an exception is thrown.
   * @param supportedAttributes This parser's supported attributes.
   */
  CommandTextAttributesParser(String commandName, Attribute... supportedAttributes) {

    this.commandName = commandName;
    this.supportedAttributes = ImmutableSet.copyOf(supportedAttributes);

    ImmutableSet.Builder<String> supportedAttributeNamesBuilder = ImmutableSet.builder();
    for (Attribute attribute : supportedAttributes) {
      supportedAttributeNamesBuilder.add(attribute.name);

      // Sanity check that the default values are allowed values.
      if (attribute.allowedValues == Attribute.ALLOW_ALL_VALUES
          || attribute.defaultValue == null
          || Attribute.NO_DEFAULT_VALUE_BECAUSE_REQUIRED.equals(attribute.defaultValue)) {
        continue; // nothing to check
      }
      Preconditions.checkArgument(attribute.allowedValues.contains(attribute.defaultValue));
    }
    supportedAttributeNames = supportedAttributeNamesBuilder.build();
  }

  /**
   * Parses a command text string into a map of attributes names to values. The command text is
   * assumed to be for the Soy command that this parser handles.
   *
   * @param commandText The command text to parse.
   * @param context For reporting syntax errors.
   * @param sourceLocation A source location near the command text, for producing useful error
   *     reports.
   * @return A map from attribute names to values.
   */
  Map<String, String> parse(
      String commandText, SoyParsingContext context, SourceLocation sourceLocation) {
    return parse(commandText, context.errorReporter(), sourceLocation);
  }

  /**
   * Parses a command text string into a map of attributes names to values. The command text is
   * assumed to be for the Soy command that this parser handles.
   *
   * @param commandText The command text to parse.
   * @param errorReporter For reporting syntax errors.
   * @param sourceLocation A source location near the command text, for producing useful error
   *     reports.
   * @return A map from attribute names to values.
   */
  Map<String, String> parse(
      String commandText, ErrorReporter errorReporter, SourceLocation sourceLocation) {

    Map<String, String> attributes = Maps.newHashMap();

    // --- Parse the attributes ---
    int i = 0; // index in commandText that we've processed up to
    Matcher matcher = ATTRIBUTE_TEXT.matcher(commandText);
    while (matcher.find(i)) {
      if (matcher.start() != i) {
        errorReporter.report(sourceLocation, MALFORMED_ATTRIBUTES, commandName, commandText);
      }
      i = matcher.end();

      String name = matcher.group(1);
      String value = matcher.group(2);
      value = ATTRIBUTE_VALUE_ESCAPE.matcher(value).replaceAll("$1");

      if (!supportedAttributeNames.contains(name)) {
        errorReporter.report(sourceLocation, UNSUPPORTED_ATTRIBUTE, name, commandName, commandText);
      }
      if (attributes.containsKey(name)) {
        errorReporter.report(sourceLocation, DUPLICATE_ATTRIBUTE, name, commandName, commandText);
      }
      attributes.put(name, value);
    }

    if (i != commandText.length()) {
      errorReporter.report(sourceLocation, MALFORMED_ATTRIBUTES, commandName, commandText);
    }

    // --- Apply default values or check correctness of supplied values ---
    for (Attribute supportedAttribute : supportedAttributes) {

      if (attributes.containsKey(supportedAttribute.name)) {
        // Check that the supplied value is allowed.
        if (supportedAttribute.allowedValues == Attribute.ALLOW_ALL_VALUES) {
          continue; // nothing to check
        }
        if (!supportedAttribute.allowedValues.contains(attributes.get(supportedAttribute.name))) {
          errorReporter.report(
              sourceLocation,
              INVALID_ATTRIBUTE_VALUE,
              supportedAttribute.name,
              commandName,
              commandText,
              supportedAttribute.allowedValues.toString());
        }

      } else {
        // Check that the attribute is not required.
        if (Attribute.NO_DEFAULT_VALUE_BECAUSE_REQUIRED.equals(supportedAttribute.defaultValue)) {
          errorReporter.report(
              sourceLocation,
              MISSING_REQUIRED_ATTRIBUTE,
              supportedAttribute.name,
              commandName,
              commandText);
        }
        // Apply default value.
        attributes.put(supportedAttribute.name, supportedAttribute.defaultValue);
      }
    }

    return attributes;
  }

  // -----------------------------------------------------------------------------------------------
  // Attribute record.

  /** Record for a supported attribute. */
  public static class Attribute {

    /** Use this as the allowed values set when there is no fixed set of allowed values. */
    public static final Collection<String> ALLOW_ALL_VALUES = null;

    /** Use this as the allowed values set for a boolean attribute. */
    public static final Collection<String> BOOLEAN_VALUES = ImmutableSet.of("true", "false");

    /**
     * Use this as the default attribute value when there should not be a default because the
     * attribute is required. (Non-required attributes must have default values.)
     */
    public static final String NO_DEFAULT_VALUE_BECAUSE_REQUIRED = "__NDVBR__";

    /** The attribute name. */
    final String name;

    /**
     * The collection of allowed values, or {@link #ALLOW_ALL_VALUES} if there's no fixed set of
     * allowed values.
     */
    final Collection<String> allowedValues;

    /**
     * The default value, or {@link #NO_DEFAULT_VALUE_BECAUSE_REQUIRED} if the attribute is
     * required.
     */
    final String defaultValue;

    /**
     * The definition of one supported attribute. If there is no fixed set of allowed values, use
     * {@link #ALLOW_ALL_VALUES}. Non-required attributes must have default values. Required
     * dattributes should map to a default value of {@link #NO_DEFAULT_VALUE_BECAUSE_REQUIRED}.
     *
     * @param name The attribute name.
     * @param allowedValues The collection of allowed values, or {@link #ALLOW_ALL_VALUES} if
     *     there's no fixed set of allowed values.
     * @param defaultValue The default value, or {@link #NO_DEFAULT_VALUE_BECAUSE_REQUIRED} if the
     *     attribute is required.
     */
    public Attribute(String name, Collection<String> allowedValues, String defaultValue) {
      this.name = name;
      this.allowedValues = allowedValues;
      this.defaultValue = defaultValue;
    }
  }
}
