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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.ForOverride;
import com.google.errorprone.annotations.concurrent.LazyInit;
import com.google.template.soy.error.ErrorArg;
import com.google.template.soy.soytree.SoyTypeP;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Interface for all classes that describe a data type in Soy. These types are used to determine
 * what kinds of values can be bound to a template or function parameter.
 *
 * <p>Note that this type hierarchy only describes types that are visible from a template author's
 * perspective. Actual Soy values may have types which fall outside this hierarchy. An example is
 * Futures - a SoyValue may contain a future of a type, but since the future is always resolved
 * before the value is used, the future type is invisible as far as the template is concerned.
 *
 * <p>All type objects are immutable.
 */
public abstract class SoyType implements ErrorArg {

  /**
   * Enum that identifies the kind of type this is.
   *
   * <ul>
   *   <li>Special types:
   *       <ul>
   *         <li>ANY: Superclass of all types.
   *         <li>UNKNOWN: Indicates that we don't know the type. A value with unknown type is
   *             considered to be dynamically typed.
   *         <li>ERROR: Placeholder which represents a type that failed to parse.
   *       </ul>
   *   <li>Primitive types:
   *       <ul>
   *         <li>NULL: The type of the "null" value
   *         <li>UNDEFINED: The type of the "undefined" value
   *         <li>BOOL
   *         <li>INT
   *         <li>FLOAT
   *         <li>GBIGINT
   *         <li>STRING
   *       </ul>
   *   <li>Sanitized types (subtypes of string):
   *       <ul>
   *         <li>HTML: Possibly containing HTML markup
   *         <li>ATTRIBUTES: key="value" pairs
   *         <li>JS
   *         <li>CSS
   *         <li>URI
   *         <li>TRUSTED_RESOURCE_URI
   *       </ul>
   *   <li>Aggregate types:
   *       <ul>
   *         <li>LIST: Sequence of items indexed by integer.
   *         <li>RECORD: Open-ended record type.
   *         <li>LEGACY_OBJECT_MAP: Deprecated map type.
   *         <li>MAP: Map type that supports proto map (and ES6 map in JS backend).
   *         <li>BASE_PROTO: A generic proto, this type is mostly useful for plugins
   *         <li>PROTO: Protobuf object.
   *         <li>PROTO_ENUM: Protobuf enum object.
   *         <li>UNION: Used to indicate a parameter that can accept multiple alternatives, e.g.
   *             a|b.
   *         <li>VE: A VE ID.
   *       </ul>
   * </ul>
   */
  public enum Kind {
    // Special types
    ANY,
    UNKNOWN,
    NEVER,
    // Primitive types
    NULL,
    UNDEFINED,
    BOOL,
    NUMBER,
    INT,
    FLOAT,
    STRING,
    GBIGINT,
    // Sanitized types (subtypes of string)
    HTML,
    ELEMENT,
    ATTRIBUTES,
    JS,
    CSS,
    URI,
    TRUSTED_RESOURCE_URI,
    // Aggregate types
    ITERABLE,
    LIST,
    SET,
    RECORD,
    LEGACY_OBJECT_MAP,
    MAP,
    MESSAGE,
    PROTO,
    PROTO_ENUM,
    TEMPLATE,
    FUNCTION,
    VE,
    VE_DATA,
    // Resolvable types
    UNION,
    INTERSECTION,
    NAMED,
    INDEXED,
    // Imported symbol types
    NAMESPACE,
    PROTO_TYPE,
    PROTO_ENUM_TYPE,
    PROTO_EXTENSION,
    TEMPLATE_TYPE,
    ;

    public static final ImmutableSet<Kind> STRING_KINDS =
        Sets.immutableEnumSet(
            Kind.STRING,
            Kind.HTML,
            Kind.ELEMENT,
            Kind.ATTRIBUTES,
            Kind.JS,
            Kind.CSS,
            Kind.URI,
            Kind.TRUSTED_RESOURCE_URI);

    public static final ImmutableSet<Kind> ILLEGAL_OPERAND_KINDS_PLUS_OP =
        Sets.immutableEnumSet(
            Kind.ITERABLE, Kind.LIST, Kind.SET, Kind.LEGACY_OBJECT_MAP, Kind.MAP, Kind.RECORD);

    public static final ImmutableSet<Kind> HTML_KINDS =
        Sets.immutableEnumSet(Kind.HTML, Kind.ELEMENT);

    public boolean isHtml() {
      return HTML_KINDS.contains(this);
    }

    /** Returns true for SoyTypes that are plain strings or sanitized subtypes of strings. */
    public boolean isKnownStringOrSanitizedContent() {
      return STRING_KINDS.contains(this);
    }

    /** Returns true for SoyTypes that are sanitized subtypes of strings. */
    public boolean isKnownSanitizedContent() {
      return this != Kind.STRING && STRING_KINDS.contains(this);
    }

    /**
     * Returns true for SoyTypes that are not allowed to be operands of binary arithmetic operators.
     */
    public boolean isIllegalOperandForBinaryOps() {
      return ILLEGAL_OPERAND_KINDS_PLUS_OP.contains(this) || STRING_KINDS.contains(this);
    }
  }

  enum UnknownAssignmentPolicy {
    ALLOWED,
    DISALLOWED
  }

  // memoize the proto version.  SoyTypes are immutable so this is safe/correct and types are likely
  // to be serialized many times (think, 'string'), so we can save some work by not calculating it
  // repeatedly.
  @LazyInit private SoyTypeP protoDual;

  // restrict subtypes to this package
  SoyType() {}

  /** Returns what kind of type this is. */
  public abstract Kind getKind();

  /**
   * Returns true if a parameter or field of this type can be assigned from a value of {@code
   * srcType}.
   *
   * <p><i>loose</i> assignment means that the type is only possibly assignable. {@code ?} types are
   * considered to be possibly assignable. Use this in cases where the compiler may insert a runtime
   * test (as in {@code call} commands) or we expect some other part of the runtime to enforce types
   * (e.g. dispatching to plugin methods).
   *
   * @param srcType The type of the incoming value.
   * @return True if the assignment is valid.
   */
  public final boolean isAssignableFromLoose(SoyType srcType) {
    return isAssignableFromInternal(srcType, UnknownAssignmentPolicy.ALLOWED);
  }

  /**
   * Returns true if a parameter or field of this type can be strictly assigned from a value of
   * {@code srcType}.
   *
   * <p><i>strict</i> assignment means that the type is definitly assignable. {@code ?} types are
   * not considered to be definitely assignable. Use this in cases where we require certainty, such
   * as when selecting methods based on receiver types or when making code generation decisions.
   *
   * @param srcType The type of the incoming value.
   * @return True if the assignment is valid.
   */
  public final boolean isAssignableFromStrict(SoyType srcType) {
    return isAssignableFromInternal(srcType, UnknownAssignmentPolicy.DISALLOWED);
  }

  /** Internal helper method for assignment analysis. This should only be used by subclasses. */
  final boolean isAssignableFromInternal(SoyType soyType, UnknownAssignmentPolicy unknownPolicy) {
    soyType = soyType.getEffectiveType();
    if (unknownPolicy == UnknownAssignmentPolicy.ALLOWED && soyType == UnknownType.getInstance()) {
      return true;
    }
    if (soyType.getKind() != Kind.UNION) {
      return doIsAssignableFromNonUnionType(soyType, unknownPolicy);
    }
    // Handle unions here with template methods rather than forcing all subclasses to handle. A type
    // is assignable from a union if it is assignable from _all_ members.
    Deque<SoyType> stack = new ArrayDeque<>();
    stack.add(soyType);
    while (!stack.isEmpty()) {
      SoyType type = stack.removeLast();
      // If a named type is itself a union we need to resolve that here.
      if (type.getKind() == Kind.NAMED || type.getKind() == Kind.INDEXED) {
        SoyType effectiveType = type.getEffectiveType();
        if (effectiveType.getKind() == Kind.UNION) {
          type = effectiveType;
        }
      }
      if (type.getKind() == Kind.UNION) {
        stack.addAll(((UnionType) type).getMembers());
        continue;
      }
      if (!doIsAssignableFromNonUnionType(type, unknownPolicy)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Subclass integration point to implement assignablility.
   *
   * @param type The target type, guaranteed to <b>not be a union type</b>.
   * @param unknownPolicy How assignments from the unknown type should be treated. This should be
   *     passed along to {@link #isAssignableFromInternal} calls made on member types.
   */
  @ForOverride
  boolean doIsAssignableFromNonUnionType(SoyType type, UnknownAssignmentPolicy unknownPolicy) {
    return doIsAssignableFromNonUnionType(type);
  }

  @ForOverride
  boolean doIsAssignableFromNonUnionType(SoyType type) {
    throw new AbstractMethodError();
  }

  /**
   * Returns true if this type has no non-nullish component, i.e. if it's null, undefined, or
   * null|undefined.
   */
  public boolean isNullOrUndefined() {
    return false;
  }

  /** The type represented in a fully parseable format. */
  @Override
  public abstract String toString();

  @Override
  public final String toErrorArgString() {
    SoyType effective = getEffectiveType();
    if (effective != this) {
      return this + " (" + effective.toErrorArgString() + ")";
    }
    return toString();
  }

  /** The type represented in proto format. For template metadata protos. */
  public final SoyTypeP toProto() {
    SoyTypeP local = protoDual;
    if (local == null) {
      SoyTypeP.Builder builder = SoyTypeP.newBuilder();
      doToProto(builder);
      local = builder.build();
      protoDual = local;
    }
    return local;
  }

  @ForOverride
  abstract void doToProto(SoyTypeP.Builder builder);

  /**
   * Resolves NAMED, INDEXED, and INTERSECTION types. The returned value is a shallow resolution,
   * guaranteed not to be one of the listed types but which may contain those types.
   */
  public SoyType getEffectiveType() {
    return this;
  }
}
