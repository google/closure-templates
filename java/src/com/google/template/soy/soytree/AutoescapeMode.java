/*
 * Copyright 2011 Google Inc.
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

import com.google.common.collect.ImmutableSet;

import java.util.Locale;
import java.util.Set;


/**
 * Specifies how the outputs of <code>{print}</code> commands that lack escaping directives are
 * encoded.
 *
 * @author Mike Samuel
 */
public enum AutoescapeMode {

  /** Auto-escaping is off for the template. */
  FALSE,
  /** Auto-escaping is on for the template so directiveless prints will be HTML escaped. */
  TRUE,
  /**
   * Contextual auto-escaping is on for the template so directiveless prints will be escaped based
   * on the surrounding context.
   */
  CONTEXTUAL,
  /**
   * Strict form of contextual autoescaping in which no autoescape-cancelling print directives nor
   * calls to non-strict templates are allowed.
   *
   * TODO: The initial implementation of strict mode does not allow any calls, and is only intended
   * for internal use when parsing {param} and {let} blocks of non-text kind. Once fully implemented
   * change the name.
   */
  STRICT,
  ;


  private final String attributeValue;


  AutoescapeMode() {
    this.attributeValue = name().toLowerCase(Locale.ENGLISH);
  }


  /**
   * Returns a form of this attribute's name suitable for use in a template attribute.
   */
  public String getAttributeValue() {
    return attributeValue;
  }


  /**
   * The set created by element-wise application of {@link #getAttributeValue} to all modes.
   */
  public static Set<String> getAttributeValues() {
    ImmutableSet.Builder<String> values = ImmutableSet.builder();
    for (AutoescapeMode value : AutoescapeMode.values()) {
      values.add(value.getAttributeValue());
    }
    return values.build();
  }


  /**
   * The value such that attributeValue.equals(value.getAttributeValue()).
   */
  public static AutoescapeMode forAttributeValue(String attributeValue) {
    return valueOf(attributeValue.toUpperCase(Locale.ENGLISH));
  }
}
