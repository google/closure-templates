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
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.template.soy.jssrc.dsl.CodeChunk.number;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_IS_ARRAY;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_IS_BOOLEAN;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_IS_FUNCTION;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_IS_NUMBER;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_IS_OBJECT;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_IS_STRING;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_SOY_DATA_SANITIZED_CONTENT;
import static com.google.template.soy.jssrc.internal.JsRuntime.sanitizedContentType;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.template.soy.base.SoyBackendKind;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.internalutils.NodeContentKinds;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.dsl.CodeChunk.Generator;
import com.google.template.soy.jssrc.dsl.GoogRequire;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.aggregate.ListType;
import com.google.template.soy.types.aggregate.MapType;
import com.google.template.soy.types.aggregate.RecordType;
import com.google.template.soy.types.aggregate.UnionType;
import com.google.template.soy.types.primitive.SanitizedType;
import com.google.template.soy.types.proto.SoyProtoEnumType;
import com.google.template.soy.types.proto.SoyProtoType;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * A representation of a javascript type.
 *
 * <p>This is geared toward generating jscompiler compatible type expressions for the purpose of
 * declarations and cast operators.
 */
final class JsType {

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

  // TODO(lukes): use this consistently throughout jssrc.  We should consider inserting type
  // expressions when extracting list items/map items/record items.  Also at all of those points we
  // should strongly consider using the value coercion logic.  The fact that we don't leads to many
  // inconsistencies especially w.r.t. how sanitized proto fields are handled.

  // NOTE: currently we allow null for any/?, in theory we shouldn't but it isn't clear how useful
  // the non-nullable type declarations would be for users.

  private static final JsType ANY_TYPE =
      builder().addType("*").setPredicate(TypePredicate.NO_OP).build();

  private static final JsType UNKNOWN_TYPE =
      builder().addType("?").setPredicate(TypePredicate.NO_OP).build();

  private static final JsType BOOLEAN_TYPE =
      builder()
          .addType("boolean")
          .setPredicate(
              new TypePredicate() {
                @Override
                public Optional<CodeChunk.WithValue> maybeCheck(
                    CodeChunk.WithValue value, Generator codeGenerator) {
                  // TODO(lukes): we shouldn't allow numbers here, see if anyone relies on this
                  // 'feature'.
                  return Optional.of(
                      GOOG_IS_BOOLEAN
                          .call(value)
                          .or(value.tripleEquals(number(1)), codeGenerator)
                          .or(value.tripleEquals(number(0)), codeGenerator));
                }
              })
          .build();

  private static final JsType NUMBER_TYPE =
      builder().addType("number").setPredicate(GOOG_IS_NUMBER).build();

  // TODO(lukes): does idom need a custom one that excludes sanitized content?
  private static final JsType STRING_OR_SANITIZED_CONTENT_TYPE =
      builder()
          // TODO(lukes): should this contain unsanitized text?
          .addType("string")
          .addType("!goog.soy.data.SanitizedContent")
          .addRequire(GoogRequire.create("goog.soy.data.SanitizedContent"))
          .setPredicate(
              new TypePredicate() {
                @Override
                public Optional<CodeChunk.WithValue> maybeCheck(
                    CodeChunk.WithValue value, Generator codeGenerator) {
                  return Optional.of(
                      GOOG_IS_STRING
                          .call(value)
                          .or(value.instanceof_(GOOG_SOY_DATA_SANITIZED_CONTENT), codeGenerator));
                }
              })
          .build();

  private static final JsType RAW_ARRAY_TYPE =
      builder().addType("!Array").setPredicate(GOOG_IS_ARRAY).build();

  private static final JsType RAW_OBJECT_TYPE =
      builder().addType("!Object").setPredicate(GOOG_IS_OBJECT).build();

  private static final JsType NULL_OR_UNDEFINED_TYPE =
      builder()
          .addType("null")
          .addType("undefined")
          .setPredicate(
              new TypePredicate() {
                @Override
                public Optional<CodeChunk.WithValue> maybeCheck(
                    CodeChunk.WithValue value, Generator codeGenerator) {
                  return Optional.of(value.doubleEqualsNull());
                }
              })
          .build();

  private static final ImmutableMap<ContentKind, JsType> STRICT_TYPES;

  private static final JsType IDOM_HTML_AND_ATTRIBUTES =
      builder().addType("function()").setPredicate(GOOG_IS_FUNCTION).build();

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
   * <p>TODO(lukes): consider adding a cache for all the computed types. The same type is probably
   * accessed many many times.
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

      case PROTO_ENUM:
        SoyProtoEnumType enumType = (SoyProtoEnumType) soyType;
        String enumTypeName = enumType.getNameForBackend(SoyBackendKind.JS_SRC);
        return builder()
            // TODO(lukes): stop allowing number?, just allow the enum
            .addType("number")
            .addType(enumTypeName)
            .addRequire(GoogRequire.create(enumTypeName))
            .setPredicate(GOOG_IS_NUMBER)
            .build();

      case FLOAT:
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
        return builder()
            .addType("!Array<" + element.typeExpr() + ">")
            .addRequires(element.getGoogRequires())
            .setPredicate(GOOG_IS_ARRAY)
            .build();

      case MAP:
        MapType mapType = (MapType) soyType;
        if (mapType.getKeyType().getKind() == SoyType.Kind.ANY
            && mapType.getValueType().getKind() == SoyType.Kind.ANY) {
          return RAW_OBJECT_TYPE;
        }
        JsType keyTypeName = forSoyType(mapType.getKeyType(), isIncrementalDom);
        JsType valueTypeName = forSoyType(mapType.getValueType(), isIncrementalDom);
        return builder()
            .addType("!Object<" + keyTypeName.typeExpr() + "," + valueTypeName.typeExpr() + ">")
            .addRequires(keyTypeName.getGoogRequires())
            .addRequires(valueTypeName.getGoogRequires())
            .setPredicate(GOOG_IS_OBJECT)
            .build();

      case PROTO:
        final SoyProtoType protoType = (SoyProtoType) soyType;
        final String protoTypeName = protoType.getNameForBackend(SoyBackendKind.JS_SRC);
        // In theory his should be "!" + protoTypeName since we don't actually allow null, but it
        // isn't clear that this is very useful for users.
        return builder()
            .addType(protoTypeName)
            .addRequire(GoogRequire.create(protoTypeName))
            .addCoercionStrategy(ValueCoercionStrategy.PROTO)
            .setPredicate(
                new TypePredicate() {
                  @Override
                  public Optional<CodeChunk.WithValue> maybeCheck(
                      CodeChunk.WithValue value, Generator codeGenerator) {
                    return Optional.of(value.instanceof_(JsRuntime.protoConstructor(protoType)));
                  }
                })
            .build();

      case RECORD:
        {
          RecordType recordType = (RecordType) soyType;
          if (recordType.getMembers().isEmpty()) {
            return RAW_OBJECT_TYPE;
          }
          Builder builder = builder();
          Map<String, String> members = new LinkedHashMap<>();
          for (Map.Entry<String, SoyType> member : recordType.getMembers().entrySet()) {
            JsType forSoyType = forSoyType(member.getValue(), isIncrementalDom);
            builder.addRequires(forSoyType.getGoogRequires());
            members.put(member.getKey(), forSoyType.typeExprForRecordMember());
          }
          return builder
              .addType("{" + Joiner.on(", ").withKeyValueSeparator(": ").join(members) + "}")
              .setPredicate(GOOG_IS_OBJECT)
              .build();
        }

      case UNION:
        {
          UnionType unionType = (UnionType) soyType;
          Builder builder = builder();
          final Set<JsType> types = new LinkedHashSet<>();
          final boolean isNullable = unionType.isNullable();
          // handle null first so that if other type tests dereference the param they won't fail
          if (isNullable) {
            builder.addTypes(NULL_OR_UNDEFINED_TYPE.typeExpressions);
            builder.addCoercionStrategy(ValueCoercionStrategy.NULL);
            types.add(NULL_OR_UNDEFINED_TYPE);
          }
          for (SoyType member : unionType.getMembers()) {
            if (member.getKind() == Kind.NULL) {
              continue; // handled above
            }
            JsType memberType = forSoyType(member, isIncrementalDom);
            builder.addRequires(memberType.extraRequires);
            builder.addTypes(memberType.typeExpressions);
            builder.addCoercionStrategies(memberType.coercionStrategies);
            types.add(memberType);
          }
          return builder
              .setPredicate(
                  new TypePredicate() {
                    @Override
                    public Optional<CodeChunk.WithValue> maybeCheck(
                        CodeChunk.WithValue value, Generator codeGenerator) {
                      CodeChunk.WithValue result = null;
                      // TODO(lukes): this will cause reevaluations, resolve by conditionally
                      // bouncing into a a temporary variable or augmenting the codechunk api to do
                      // this automatically.
                      for (JsType memberType : types) {
                        Optional<CodeChunk.WithValue> typeAssertion =
                            memberType.getTypeAssertion(value, codeGenerator);
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
                  })
              .build();
        }

      default:
        throw new AssertionError("unhandled soytype: " + soyType);
    }
  }

  /** Can generate code chunks which validate the 'type' of a given code chunk. */
  private interface TypePredicate {
    final TypePredicate NO_OP =
        new TypePredicate() {
          @Override
          public Optional<CodeChunk.WithValue> maybeCheck(
              CodeChunk.WithValue value, Generator codeGenerator) {
            return Optional.absent();
          }
        };

    /**
     * Returns a code chunk that evaluates to {@code true} if the given chunk matches the predicate
     * and {@code false} otherwise. Returns {@link Optional#absent()} if there is no validation to
     * be done.
     */
    Optional<CodeChunk.WithValue> maybeCheck(CodeChunk.WithValue value, Generator codeGenerator);
  }

  private final ImmutableSortedSet<String> typeExpressions;
  private final ImmutableSet<GoogRequire> extraRequires;
  private final ImmutableSet<ValueCoercionStrategy> coercionStrategies;
  private final TypePredicate predicate;

  private JsType(Builder builder) {
    // Sort for determinism, order doesn't matter.
    this.typeExpressions = builder.typeExpressions.build();
    checkArgument(!typeExpressions.isEmpty());
    this.coercionStrategies = Sets.immutableEnumSet(builder.coercionStrategies);
    this.extraRequires = builder.extraRequires.build();
    this.predicate = checkNotNull(builder.predicate);
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

  final ImmutableSet<GoogRequire> getGoogRequires() {
    return extraRequires;
  }

  /**
   * Returns a code chunk that generates a 'test' for whether or not the given value has this type
   * or {@link Optional#absent()} if no test is necessary.
   */
  final Optional<CodeChunk.WithValue> getTypeAssertion(
      CodeChunk.WithValue value, Generator codeGenerator) {
    return predicate.maybeCheck(value, codeGenerator);
  }

  /** Generates code to coerce the value, returns {@code null} if no coercion is necessary. */
  @Nullable
  final CodeChunk.WithValue getValueCoercion(CodeChunk.WithValue value, Generator codeGenerator) {
    boolean needsProtoCoercion = coercionStrategies.contains(ValueCoercionStrategy.PROTO);
    if (!needsProtoCoercion) {
      return null;
    }
    CodeChunk.WithValue coercion = value.dotAccess("$jspbMessageInstance").or(value, codeGenerator);
    return coercionStrategies.contains(ValueCoercionStrategy.NULL)
        ? value.and(coercion, codeGenerator)
        : coercion;
  }

  private static JsType createSanitized(final ContentKind kind) {
    String type = NodeContentKinds.toJsSanitizedContentCtorName(kind);
    // All the sanitized types have an .isCompatibleWith method for testing for allowed types
    // NOTE: this actually allows 'string' to be passed, which is inconsistent with other backends
    // We allow string or unsanitized type to be passed where a
    // sanitized type is specified - it just means that the text will
    // be escaped.
    // NOTE: we don't add goog.requires for all these alias types.  This is 'ok' since we never
    // invoke a method on them directly (instead they just get passed around and eventually get
    // consumed by an escaper function.
    // TODO(lukes): maybe we should define typedefs for these?
    Builder builder = builder();
    builder.addType("!" + type);
    builder.addRequire(GoogRequire.create(type));
    builder.addType("string");
    builder.addType("!goog.soy.data.UnsanitizedText");
    builder.addRequire(GoogRequire.create("goog.soy.data.UnsanitizedText"));
    // add extra alternate types
    // TODO(lukes): instead of accepting alternates we should probably just coerce to sanitized
    // content.  using these wide unions everywhere is confusing.
    switch (kind) {
      case CSS:
        builder.addType("!goog.html.SafeStyle");
        break;
      case HTML:
        builder.addType("!goog.html.SafeHtml");
        break;
      case JS:
        builder.addType("!goog.html.SafeScript");
        break;
      case ATTRIBUTES:
      case TEXT:
        // nothing extra
        break;
      case TRUSTED_RESOURCE_URI:
        builder.addType("!goog.html.TrustedResourceUrl");
        break;
      case URI:
        builder.addType("!goog.html.TrustedResourceUrl");
        builder.addType("!goog.html.SafeUrl");
        builder.addType("!goog.Uri");
        break;
      default:
        throw new AssertionError("Unhandled content kind");
    }

    // TODO(lukes): consider eliminating the isCompatibleWith method and just inlining the
    // assertions, or as mentioned above we could just coerce to SanitizedContent consistently
    return builder
        .setPredicate(
            new TypePredicate() {
              @Override
              public Optional<CodeChunk.WithValue> maybeCheck(
                  CodeChunk.WithValue value, Generator codeGenerator) {
                return Optional.of(
                    sanitizedContentType(kind).dotAccess("isCompatibleWith").call(value));
              }
            })
        .build();
  }

  private static Builder builder() {
    return new Builder();
  }

  private static final class Builder {
    final ImmutableSortedSet.Builder<String> typeExpressions = ImmutableSortedSet.naturalOrder();
    final ImmutableSet.Builder<GoogRequire> extraRequires = ImmutableSet.builder();
    final Set<ValueCoercionStrategy> coercionStrategies =
        EnumSet.noneOf(ValueCoercionStrategy.class);
    @Nullable TypePredicate predicate;

    Builder addType(String typeExpr) {
      typeExpressions.add(typeExpr);
      return this;
    }

    Builder addTypes(Iterable<String> typeExpressions) {
      this.typeExpressions.addAll(typeExpressions);
      return this;
    }

    Builder addRequire(GoogRequire symbol) {
      extraRequires.add(symbol);
      return this;
    }

    Builder addRequires(Iterable<GoogRequire> symbols) {
      extraRequires.addAll(symbols);
      return this;
    }

    Builder addCoercionStrategy(ValueCoercionStrategy strategy) {
      coercionStrategies.add(strategy);
      return this;
    }

    Builder addCoercionStrategies(Iterable<ValueCoercionStrategy> strategies) {
      Iterables.addAll(coercionStrategies, strategies);
      return this;
    }

    Builder setPredicate(TypePredicate predicate) {
      checkState(this.predicate == null);
      this.predicate = checkNotNull(predicate);
      return this;
    }

    /** Sets a predicate which simply invokes the given function. */
    Builder setPredicate(final CodeChunk.WithValue predicateFunction) {
      return setPredicate(
          new TypePredicate() {
            @Override
            public Optional<CodeChunk.WithValue> maybeCheck(
                CodeChunk.WithValue value, Generator codeGenerator) {
              return Optional.of(checkNotNull(predicateFunction).call(value));
            }
          });
    }

    JsType build() {
      return new JsType(this);
    }
  }
}
