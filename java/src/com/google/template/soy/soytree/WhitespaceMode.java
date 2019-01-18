/*
 * Copyright 2019 Google Inc.
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

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableMap;

/** Whitespace handling mode. */
public enum WhitespaceMode {
  /**
   * Indicates that whitespace should be preserved in template output. An example of the definition:
   * "{template .foo whitespace="preserve"}".
   */
  PRESERVE,

  /**
   * Indicates that the whitespace should be optimized out (joined) in the template output. This is
   * a default behavior, for example: "{template .foo}".
   */
  JOIN;

  private final String attributeValue;

  /** Initializes a new instance of an object. */
  WhitespaceMode() {
    this.attributeValue = Ascii.toLowerCase(name());
  }

  /** Gets the value of this attribute. */
  public String getAttributeValue() {
    return attributeValue;
  }

  private static final ImmutableMap<String, WhitespaceMode> MODES_BY_ATTRIBUTE_VALUE;

  static {
    ImmutableMap.Builder<String, WhitespaceMode> builder = ImmutableMap.builder();

    for (WhitespaceMode value : WhitespaceMode.values()) {
      builder.put(value.getAttributeValue(), value);
    }

    MODES_BY_ATTRIBUTE_VALUE = builder.build();
  }

  /**
   * Returns an instance of {@code WhitespaceMode} that has the given attribute value.
   *
   * @param attributeValue Attribute value (either "preserve" or "join").
   * @return An instance of {@code WhitespaceMode} with the given attribute value or null if the
   *     enumeration does not contain the given attribute value.
   */
  public static WhitespaceMode forAttributeValue(String attributeValue) {
    return MODES_BY_ATTRIBUTE_VALUE.get(attributeValue);
  }

  /** Returns a list of all possible attribute values. */
  public static Iterable<String> getAttributeValues() {
    return MODES_BY_ATTRIBUTE_VALUE.keySet();
  }
}
