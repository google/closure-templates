/*
 * Copyright 2017 Google Inc.
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
package com.google.template.soy.base.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Ascii;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.TreeMap;

/**
 * Representation of the value of the {@code kind=\"...\"} parameter to soy templates and content
 * blocks.
 *
 * <p>This is the enum that should be used internally by the compiler. The analagous public
 * interface is {@code SanitizedContent.ContentKind} which should be used by the Java backends to
 * communicate kind information with callers.
 */
public enum SanitizedContentKind {

  /**
   * A snippet of HTML that does not start or end inside a tag, comment, entity, or DOCTYPE; and
   * that does not contain any executable code (JS, {@code <object>}s, etc.) from a different trust
   * domain.
   */
  HTML,

  /**
   * Executable Javascript code or expression, safe for insertion in a script-tag or event handler
   * context, known to be free of any attacker-controlled scripts. This can either be
   * side-effect-free Javascript (such as JSON) or Javascript that entirely under Google's control.
   */
  JS,

  /** A properly encoded portion of a URI. */
  URI,

  /** Resource URIs used in script sources, stylesheets, etc which are not in attacker control. */
  TRUSTED_RESOURCE_URI,

  /** An attribute name and value, such as {@code dir="ltr"}. */
  ATTRIBUTES,

  /** A CSS3 declaration, property, value or group of semicolon separated declarations. */
  CSS,

  /**
   * Unsanitized plain-text content.
   *
   * <p>This is effectively the "null" entry of this enum, and is sometimes used to explicitly mark
   * content that should never be used unescaped. Since any string is safe to use as text, being of
   * ContentKind.TEXT makes no guarantees about its safety in any other context such as HTML.
   *
   * <p>In the soy type system, {@code TEXT} is equivalent to the string type.
   */
  TEXT;

  private static final ImmutableMap<String, SanitizedContentKind> KINDS_BY_ATTRIBUTE_VALUE;

  static {
    TreeMap<String, SanitizedContentKind> kindsByAttributeValue = new TreeMap<>();
    for (SanitizedContentKind kind : values()) {
      kindsByAttributeValue.put(kind.attributeValue, kind);
    }
    KINDS_BY_ATTRIBUTE_VALUE = ImmutableMap.copyOf(kindsByAttributeValue);
  }

  private final String attributeValue;

  SanitizedContentKind() {
    this.attributeValue = Ascii.toLowerCase(name());
  }

  /** Returns the kind formatted as it would be for an attribute value. */
  public String asAttributeValue() {
    return attributeValue;
  }

  /** Returns the kind for the given attribute value. Or {@code null} if it is invalid. */
  public static Optional<SanitizedContentKind> fromAttributeValue(String attributeValue) {
    checkNotNull(attributeValue);
    return Optional.fromNullable(KINDS_BY_ATTRIBUTE_VALUE.get(attributeValue));
  }

  /** Returns all the valid attribute values. */
  public static ImmutableSet<String> attributeValues() {
    return KINDS_BY_ATTRIBUTE_VALUE.keySet();
  }
}
