/*
 * Copyright 2013 Google Inc.
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

package com.google.template.soy.types;

import com.google.template.soy.data.SoyValue;


/**
 * Interface for all classes that describe a data type in Soy. These types
 * are used to determine what kinds of values can be bound to a template or
 * function parameter.
 *
 * <p>Note that this type hierarchy only describes types that are visible from
 * a template author's perspective. Actual Soy values may have types which
 * fall outside this hierarchy. An example is Futures - a SoyValue may contain
 * a future of a type, but since the future is always resolved before the
 * value is used, the future type is invisible as far as the template is
 * concerned.
 *
 * <p>All type objects are immutable.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public interface SoyType {


  /** Enum that identifies the kind of type this is. */
  public enum Kind {

    // Special types

    ANY,        // Superclass of all types.
    UNKNOWN,    // Indicates that we don't know the type. A value with unknown
                // type is considered to be dynamically typed.
    ERROR,      // Placeholder which represents a type that failed to parse.

    // Primitive types

    NULL,       // The type of the "null" value
    BOOL,
    INT,
    FLOAT,
    STRING,

    // Sanitized types (subtypes of string}

    HTML,       // Possibly containing HTML markup
    ATTRIBUTES, // key="value" pairs
    JS,
    CSS,
    URI,
    TRUSTED_RESOURCE_URI,

    // Aggregate types

    LIST,       // Sequence of items indexed by integer.
    RECORD,     // Open-ended record type.
    MAP,        // Generalized map.
    OBJECT,     // A complex type having a specific identity, and zero or more fields.
    ENUM,       // An enumeration type.
    UNION,      // Used to indicate a parameter that can accept multiple
                // alternatives, e.g. a|b.
  }


  /**
   * Returns what kind of type this is.
   */
  Kind getKind();


  /**
   * Returns true if a parameter or field of this type can be assigned from
   * a value of {@code srcType}.
   *
   * @param srcType The type of the incoming value.
   * @return True if the assignment is valid.
   */
  boolean isAssignableFrom(SoyType srcType);


  /**
   * Returns true if the given value is an instance of this type. For generic types,
   * this only checks the overall shape of the type (list, map, etc) since Java type erasure
   * does not allow the type parameters to be checked. Also, in some cases the "instanceof"
   * test may be defined somewhat loosely - for example, sanitized types may be considered
   * instances of type string, since they are usable in any context where a string is
   * usable, even though internally they are not implemented as subclasses of string.
   * This test does not take into account automatic coercions, such as converting to
   * string or boolean.
   *
   * @param value The value to test.
   * @return True if the value is an instance of this type.
   */
  boolean isInstance(SoyValue value);
}
