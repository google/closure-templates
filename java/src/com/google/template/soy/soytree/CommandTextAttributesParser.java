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
import com.google.common.collect.Sets;
import com.google.template.soy.base.SoySyntaxException;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * A class for parsing attributes out of command text.
 *
 */
class CommandTextAttributesParser {


  /** Regex pattern for an attribute in command text.
   *  Note group(1) is attribute name, group(2) is attribute value.
   *  E.g. data="$boo" parses into group(1)="data" and group(2)="$boo". */
  private static final Pattern ATTRIBUTE_TEXT =
      Pattern.compile("([a-z][a-z-]*) = \" ([^\"]*) \" \\s*", Pattern.COMMENTS);

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
  public CommandTextAttributesParser(String commandName, Attribute... supportedAttributes) {

    this.commandName = commandName;
    this.supportedAttributes = Sets.newHashSet(supportedAttributes);

    supportedAttributeNames = Sets.newHashSet();
    for (Attribute attribute : supportedAttributes) {
      supportedAttributeNames.add(attribute.name);

      // Sanity check that the default values are allowed values.
      if (attribute.allowedValues == Attribute.ALLOW_ALL_VALUES ||
          attribute.defaultValue == Attribute.NO_DEFAULT_VALUE_BECAUSE_REQUIRED) {
        continue;  // nothing to check
      }
      Preconditions.checkArgument(attribute.allowedValues.contains(attribute.defaultValue));
    }
  }


  /**
   * Parses a command text string into a map of attributes names to values. The command text is
   * assumed to be for the Soy command that this parser handles.
   *
   * @param commandText The command text to parse.
   * @return A map from attribute names to values.
   * @throws SoySyntaxException If a syntax error is encountered.
   */
  public Map<String, String> parse(String commandText) throws SoySyntaxException {

    Map<String, String> attributes = Maps.newHashMap();

    // --- Parse the attributes ---
    int i = 0;  // index in commandText that we've processed up to
    Matcher matcher = ATTRIBUTE_TEXT.matcher(commandText);
    while (matcher.find(i)) {

      if (matcher.start() != i) {
        throw new SoySyntaxException(
            "Malformed attributes in '" + commandName + "' command text \"" + commandText + "\".");
      }
      i = matcher.end();

      String name = matcher.group(1);
      String value = matcher.group(2);

      if (!supportedAttributeNames.contains(name)) {
        throw new SoySyntaxException(
            "Unsupported attribute '" + name + "' in '" + commandName + "' command text \"" +
            commandText + "\".");
      }
      if (attributes.containsKey(name)) {
        throw new SoySyntaxException(
            "Duplicate attribute '" + name + "' in '" + commandName + "' command text \"" +
            commandText + "\".");
      }
      attributes.put(name, value);
    }

    if (i != commandText.length()) {
      throw new SoySyntaxException(
          "Malformed attributes in '" + commandName + "' command text \"" + commandText + "\".");
    }

    // --- Apply default values or check correctness of supplied values ---
    for (Attribute supportedAttribute : supportedAttributes) {

      if (attributes.containsKey(supportedAttribute.name)) {
        // Check that the supplied value is allowed.
        if (supportedAttribute.allowedValues == Attribute.ALLOW_ALL_VALUES) {
          continue;  // nothing to check
        }
        if (!supportedAttribute.allowedValues.contains(attributes.get(supportedAttribute.name))) {
          throw new SoySyntaxException(
              "Invalid value for attribute '" + supportedAttribute.name + "' in '" +
              commandName + "' command text \"" + commandText + "\".");
        }

      } else {
        // Check that the attribute is not required.
        if (Attribute.NO_DEFAULT_VALUE_BECAUSE_REQUIRED.equals(supportedAttribute.defaultValue)) {
          throw new SoySyntaxException(
              "Missing required attribute '" + supportedAttribute.name + "' in '" +
              commandName + "' command text \"" + commandText + "\".");
        }
        // Apply default value.
        attributes.put(supportedAttribute.name, supportedAttribute.defaultValue);
      }
    }

    return attributes;
  }


  // -----------------------------------------------------------------------------------------------
  // Attribute record.


  /**
   * Record for a supported attribute.
   */
  public static class Attribute {


    /** Use this as the allowed values set when there is no fixed set of allowed values. */
    public static final Collection<String> ALLOW_ALL_VALUES = null;

    /** Use this as the allowed values set for a boolean attribute. */
    public static final Collection<String> BOOLEAN_VALUES = ImmutableSet.of("true", "false");

    /** Use this as the allowed values set for a boolean attribute that may be omitted. */
    public static final Collection<String> BOOLEAN_VALUES_AND_NULL =
        Collections.unmodifiableSet(Sets.newHashSet("true", "false", null));

    /** Use this as the default attribute value when there should not be a default because the
     *  attribute is required. (Non-required attributes must have default values.) */
    public static final String NO_DEFAULT_VALUE_BECAUSE_REQUIRED = "__NDVBR__";


    /** The attribute name. */
    final String name;

    /** The collection of allowed values, or {@link #ALLOW_ALL_VALUES} if there's no fixed set of
     *  allowed values. */
    final Collection<String> allowedValues;

    /** The default value, or {@link #NO_DEFAULT_VALUE_BECAUSE_REQUIRED} if the attribute is
     *  required. */
    final String defaultValue;


    /**
     * The definition of one supported attribute.
     * If there is no fixed set of allowed values, use {@link #ALLOW_ALL_VALUES}.
     * Non-required attributes must have default values. Required dattributes should map to a
     * default value of {@link #NO_DEFAULT_VALUE_BECAUSE_REQUIRED}.
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
