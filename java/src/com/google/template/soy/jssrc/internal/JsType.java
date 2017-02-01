/*
 * Copyright 2016 Google Inc.
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

package com.google.template.soy.jssrc.internal;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.template.soy.jssrc.dsl.CodeChunk.dottedId;
import static com.google.template.soy.jssrc.dsl.CodeChunk.number;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.template.soy.base.SoyBackendKind;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.internalutils.NodeContentKinds;
import com.google.template.soy.jssrc.dsl.CodeChunk.Generator;
import com.google.template.soy.jssrc.dsl.CodeChunk.WithValue;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.aggregate.ListType;
import com.google.template.soy.types.aggregate.MapType;
import com.google.template.soy.types.aggregate.RecordType;
import com.google.template.soy.types.aggregate.UnionType;
import com.google.template.soy.types.primitive.SanitizedType;
import com.google.template.soy.types.proto.SoyProtoType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * A representation of a javascript type.
 *
 * <p>This is geared toward generating jscompiler compatible type expressions for the purpose of
 * declarations and cast operators.
 */
abstract class JsType {

  /**
   * In some cases we need to coerce a user provided value to something else to support
   * compatibility.
   *
   * <p>Currently we only support protos and null, and it might be difficult to add more options.
   */
  private enum ValueCoercionStrategy {
    /**
     * Means that the value is allowed to be null.
     *
     * <p>This can influence the order of other coercion operations
     */
    NULL,

    /**
     * For protos, in particular try to handle the toObject() representation in addition to the
     * normal runtime representation. Our type declarations specifically do not allow this but we
     * support it to make it easier for applications to migrate away from the toObject() jspb
     * representation.
     */
    PROTO;
  }

  // TODO(lukes): add a method to get goog.requires needed by a type expression
  // TODO(lukes): use this consistently throughout jssrc.  We should consider inserting type
  // expressions when extracting list items/map items/record items.  Also at all of those points we
  // should strongly consider using the value coercion logic.  The fact that we don't leads to many
  // inconsistencies especially w.r.t. how sanitized proto fields are handled.

  // NOTE: currently we allow null for any/?, in theory we shouldn't but it isn't clear how useful
  // the non-nullable type declarations would be for users.

  private static final JsType ANY_TYPE =
      new JsType("*") {
        @Override
        Optional<WithValue> getTypeAssertion(WithValue value, Generator codeGenerator) {
          return Optional.absent();
        }
      };

  private static final JsType UNKNOWN_TYPE =
      new JsType("?") {
        @Override
        Optional<WithValue> getTypeAssertion(WithValue value, Generator codeGenerator) {
          return Optional.absent();
        }
      };

  private static final JsType BOOLEAN_TYPE =
      new JsType("boolean") {
        @Override
        Optional<WithValue> getTypeAssertion(WithValue value, Generator codeGenerator) {
          // TODO(lukes): we shouldn't allow numbers here, see if anyone relies on this 'feature'.
          return Optional.of(
              dottedId("goog.isBoolean")
                  .call(value)
                  .or(value.tripleEquals(number(1)), codeGenerator)
                  .or(value.tripleEquals(number(0)), codeGenerator));
        }
      };

  private static final JsType NUMBER_TYPE =
      new JsType("number") {
        @Override
        Optional<WithValue> getTypeAssertion(WithValue value, Generator codeGenerator) {
          return Optional.of(dottedId("goog.isNumber").call(value));
        }
      };

  // TODO(lukes): does idom need a custom one that excludes sanitized content?
  private static final JsType STRING_OR_SANITIZED_CONTENT_TYPE =
      new JsType(ImmutableList.of("string", "!goog.soy.data.SanitizedContent")) {
        @Override
        Optional<WithValue> getTypeAssertion(WithValue value, Generator codeGenerator) {
          return Optional.of(
              dottedId("goog.isString")
                  .call(value)
                  .or(value.instanceof_("goog.soy.data.SanitizedContent"), codeGenerator));
        }
      };

  private static final JsType RAW_ARRAY_TYPE =
      new JsType("!Array") {
        @Override
        Optional<WithValue> getTypeAssertion(WithValue value, Generator codeGenerator) {
          return Optional.of(dottedId("goog.isArray").call(value));
        }
      };

  private static final JsType RAW_OBJECT_TYPE =
      new JsType("!Object") {
        @Override
        Optional<WithValue> getTypeAssertion(WithValue value, Generator codeGenerator) {
          return Optional.of(dottedId("goog.isObject").call(value));
        }
      };

  private static final JsType NULL_OR_UNDEFINED_TYPE =
      new JsType(
          ImmutableList.of("null", "undefined"), ImmutableList.of(ValueCoercionStrategy.NULL)) {
        @Override
        Optional<WithValue> getTypeAssertion(WithValue value, Generator codeGenerator) {
          return Optional.of(value.doubleEqualsNull());
        }
      };

  private static final ImmutableMap<ContentKind, JsType> STRICT_TYPES;

  private static final JsType IDOM_HTML_AND_ATTRIBUTES =
      new JsType("function()") {
        @Override
        Optional<WithValue> getTypeAssertion(WithValue value, Generator codeGenerator) {
          return Optional.of(dottedId("goog.isFunction").call(value));
        }
      };

  static {
    EnumMap<ContentKind, JsType> types = new EnumMap<>(ContentKind.class);
    for (ContentKind kind : ContentKind.values()) {
      types.put(kind, createSanitized(kind));
    }
    STRICT_TYPES = Maps.immutableEnumMap(types);
  }

  /**
   * Returns a {@link JsType} corresponding to the given {@link SoyType}
   *
   * @param soyType the soy type
   * @param isIncrementalDom whether or not this is for incremental dom.
   */
  static JsType forSoyType(SoyType soyType, boolean isIncrementalDom) {
    switch (soyType.getKind()) {
      case NULL:
        return NULL_OR_UNDEFINED_TYPE;

      case ANY:
        return ANY_TYPE;

      case UNKNOWN:
        return UNKNOWN_TYPE;

      case BOOL:
        return BOOLEAN_TYPE;

      case FLOAT:
      case PROTO_ENUM:
      case INT:
        return NUMBER_TYPE;

      case STRING:
        return STRING_OR_SANITIZED_CONTENT_TYPE;

      case ATTRIBUTES:
      case HTML:
        if (isIncrementalDom) {
          // idom has a different strategy for handling these
          return IDOM_HTML_AND_ATTRIBUTES;
        }
        // fall-through
      case CSS:
      case JS:
      case URI:
      case TRUSTED_RESOURCE_URI:
        return STRICT_TYPES.get(((SanitizedType) soyType).getContentKind());

      case LIST:
        ListType listType = (ListType) soyType;
        if (listType.getElementType().getKind() == SoyType.Kind.ANY) {
          return RAW_ARRAY_TYPE;
        }
        JsType element = forSoyType(listType.getElementType(), isIncrementalDom);
        // consider a cache?
        return new JsType("!Array<" + element.typeExpr() + ">") {
          @Override
          Optional<WithValue> getTypeAssertion(WithValue value, Generator codeGenerator) {
            return Optional.of(dottedId("goog.isArray").call(value));
          }
        };

      case MAP:
        MapType mapType = (MapType) soyType;
        if (mapType.getKeyType().getKind() == SoyType.Kind.ANY
            && mapType.getValueType().getKind() == SoyType.Kind.ANY) {
          return RAW_OBJECT_TYPE;
        }
        JsType keyTypeName = forSoyType(mapType.getKeyType(), isIncrementalDom);
        JsType valueTypeName = forSoyType(mapType.getValueType(), isIncrementalDom);
        return new JsType(
            "!Object<" + keyTypeName.typeExpr() + "," + valueTypeName.typeExpr() + ">") {
          @Override
          Optional<WithValue> getTypeAssertion(WithValue value, Generator codeGenerator) {
            return Optional.of(dottedId("goog.isObject").call(value));
          }
        };

      case PROTO:
        final String protoTypeName =
            ((SoyProtoType) soyType).getNameForBackend(SoyBackendKind.JS_SRC);
        // In theory his should be "!" + protoTypeName since we don't actually allow null, but it
        // isn't clear that this is very useful for users.
        return new JsType(protoTypeName, ValueCoercionStrategy.PROTO) {
          @Override
          Optional<WithValue> getTypeAssertion(WithValue value, Generator codeGenerator) {
            return Optional.of(value.instanceof_(protoTypeName));
          }
        };

      case RECORD:
        RecordType recordType = (RecordType) soyType;
        if (recordType.getMembers().isEmpty()) {
          return RAW_OBJECT_TYPE;
        }
        Map<String, String> members = new LinkedHashMap<>();
        for (Map.Entry<String, SoyType> member : recordType.getMembers().entrySet()) {
          members.put(
              member.getKey(),
              forSoyType(member.getValue(), isIncrementalDom).typeExprForRecordMember());
        }
        return new JsType("{" + Joiner.on(", ").withKeyValueSeparator(": ").join(members) + "}") {
          @Override
          Optional<WithValue> getTypeAssertion(WithValue value, Generator codeGenerator) {
            return Optional.of(dottedId("goog.isObject").call(value));
          }
        };

      case UNION:
        UnionType unionType = (UnionType) soyType;
        Set<String> typeExprs = new LinkedHashSet<>();
        Set<ValueCoercionStrategy> strategies = new LinkedHashSet<>();
        final Set<JsType> types = new LinkedHashSet<>();
        final boolean isNullable = unionType.isNullable();
        // handle null first so that if other type tests dereference the param they won't fail
        if (isNullable) {
          typeExprs.addAll(NULL_OR_UNDEFINED_TYPE.typeExpressions);
          strategies.add(ValueCoercionStrategy.NULL);
          types.add(NULL_OR_UNDEFINED_TYPE);
        }
        for (SoyType member : unionType.getMembers()) {
          if (member.getKind() == Kind.NULL) {
            continue; // handled above
          }
          JsType memberType = forSoyType(member, isIncrementalDom);
          typeExprs.addAll(memberType.typeExpressions);
          strategies.addAll(memberType.coercionStrategies);
          types.add(memberType);
        }
        return new JsType(typeExprs, strategies) {
          @Override
          Optional<WithValue> getTypeAssertion(WithValue value, Generator codeGenerator) {
            WithValue result = null;
            // TODO(lukes): this will cause reevaluations, resolve by conditionally bouncing into a
            // a temporary variable or augmenting the codechunk api to do this automatically.
            for (JsType memberType : types) {
              Optional<WithValue> typeAssertion = memberType.getTypeAssertion(value, codeGenerator);
              if (!typeAssertion.isPresent()) {
                return Optional.absent();
              }
              if (result == null) {
                result = typeAssertion.get();
              } else {
                result = result.or(typeAssertion.get(), codeGenerator);
              }
            }
            return Optional.of(result);
          }
        };

      default:
        throw new AssertionError("unhandled soytype: " + soyType);
    }
  }

  private final ImmutableSortedSet<String> typeExpressions;
  private final ImmutableSet<ValueCoercionStrategy> coercionStrategies;

  private JsType(String typeExpr) {
    this(ImmutableList.of(typeExpr), ImmutableSet.<ValueCoercionStrategy>of());
  }

  private JsType(Iterable<String> typeExprs) {
    this(typeExprs, ImmutableSet.<ValueCoercionStrategy>of());
  }

  private JsType(String typeExpr, ValueCoercionStrategy coercionStrategy) {
    this(ImmutableList.of(typeExpr), ImmutableSet.of(coercionStrategy));
  }

  private JsType(Iterable<String> typeExprs, Iterable<ValueCoercionStrategy> coercionStrategies) {
    // Sort for determinism, order doesn't matter.
    this.typeExpressions = ImmutableSortedSet.copyOf(typeExprs);
    checkArgument(!typeExpressions.isEmpty());
    EnumSet<ValueCoercionStrategy> strategies = EnumSet.noneOf(ValueCoercionStrategy.class);
    Iterables.addAll(strategies, coercionStrategies);
    this.coercionStrategies = Sets.immutableEnumSet(strategies);
  }

  /** Returns a type expression. */
  String typeExpr() {
    return Joiner.on('|').join(typeExpressions);
  }

  /**
   * Returns a type expression for a record member. In some cases this requires additional parens.
   */
  String typeExprForRecordMember() {
    if (typeExpressions.size() > 1 || typeExpressions.first().equals("?")) {
      // needs parens
      return "(" + typeExpr() + ")";
    }
    return typeExpr();
  }

  /**
   * Returns a code chunk that generates a 'test' for whether or not the given value has this type
   * or {@link Optional#absent()} if no test is necessary.
   */
  abstract Optional<WithValue> getTypeAssertion(WithValue value, Generator codeGenerator);

  /** Generates code to coerce the value, returns {@code null} if no coercion is necessary. */
  @Nullable
  final WithValue getValueCoercion(WithValue value, Generator codeGenerator) {
    boolean needsProtoCoercion = coercionStrategies.contains(ValueCoercionStrategy.PROTO);
    if (!needsProtoCoercion) {
      return null;
    }
    WithValue coercion = value.dotAccess("$jspbMessageInstance").or(value, codeGenerator);
    return coercionStrategies.contains(ValueCoercionStrategy.NULL)
        ? value.and(coercion, codeGenerator)
        : coercion;
  }

  private static JsType createSanitized(ContentKind kind) {
    final String type = NodeContentKinds.toJsSanitizedContentCtorName(kind);
    // All the sanitized types have an .isCompatibleWith method for testing for allowed types
    // NOTE: this actually allows 'string' to be passed, which is inconsistent with other backends
    // We allow string or unsanitized type to be passed where a
    // sanitized type is specified - it just means that the text will
    // be escaped.
    List<String> typeExprs = new ArrayList<>();
    typeExprs.add("!" + type);
    typeExprs.add("string");
    typeExprs.add("!goog.soy.data.UnsanitizedText");
    // add extra alternate types
    // TODO(lukes): instead of accepting alternates we should probably just coerce to sanitized
    // content.  using these wide unions everywhere is confusing.
    switch (kind) {
      case CSS:
        typeExprs.add("!goog.html.SafeStyle");
        break;
      case HTML:
        typeExprs.add("!goog.html.SafeHtml");
        break;
      case JS:
        typeExprs.add("!goog.html.SafeScript");
        break;
      case ATTRIBUTES:
      case TEXT:
        // nothing extra
        break;
      case TRUSTED_RESOURCE_URI:
        typeExprs.add("!goog.html.TrustedResourceUrl");
        break;
      case URI:
        typeExprs.add("!goog.html.TrustedResourceUrl");
        typeExprs.add("!goog.html.SafeUrl");
        typeExprs.add("!goog.Uri");
        break;
      default:
        throw new AssertionError("Unhandled content kind");
    }

    // TODO(lukes): consider eliminating the isCompatibleWith method and just inlining the
    // assertions, or as mentioned above we could just coerce to SanitizedContent consistently
    return new JsType(typeExprs) {
      @Override
      Optional<WithValue> getTypeAssertion(WithValue value, Generator codeGenerator) {
        return Optional.of(dottedId(type).dotAccess("isCompatibleWith").call(value));
      }
    };
  }
}
