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
import static com.google.template.soy.jssrc.dsl.Expressions.id;
import static com.google.template.soy.jssrc.dsl.Expressions.stringLiteral;
import static com.google.template.soy.jssrc.internal.JsRuntime.ARRAY_IS_ARRAY;
import static com.google.template.soy.jssrc.internal.JsRuntime.ELEMENT_LIB_IDOM;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_HTML_SAFE_ATTRIBUTE;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_IS_FUNCTION;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_IS_OBJECT;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_SOY_DATA;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_SOY_DATA_SANITIZED_CONTENT;
import static com.google.template.soy.jssrc.internal.JsRuntime.SAFEVALUES_SAFEHTML;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_ASSERT_PARAM_TYPE;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_VELOG;
import static com.google.template.soy.jssrc.internal.JsRuntime.sanitizedContentType;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.template.soy.base.SoyBackendKind;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.base.internal.TemplateContentKind;
import com.google.template.soy.data.internalutils.NodeContentKinds;
import com.google.template.soy.internal.proto.ProtoUtils;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.dsl.CodeChunk.Generator;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.jssrc.dsl.Expressions;
import com.google.template.soy.jssrc.dsl.GoogRequire;
import com.google.template.soy.types.AbstractIterableType;
import com.google.template.soy.types.FunctionType;
import com.google.template.soy.types.IndexedType;
import com.google.template.soy.types.IntersectionType;
import com.google.template.soy.types.LegacyObjectMapType;
import com.google.template.soy.types.MapType;
import com.google.template.soy.types.NamedType;
import com.google.template.soy.types.RecordType;
import com.google.template.soy.types.SanitizedType;
import com.google.template.soy.types.SoyProtoEnumType;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.TemplateType;
import com.google.template.soy.types.UnionType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * A representation of a javascript type.
 *
 * <p>This is geared toward generating jscompiler compatible type expressions for the purpose of
 * declarations and cast operators.
 */
public final class JsType implements CodeChunk.HasRequires {

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
      builder().addType("boolean").setPredicate(typeofTypePredicate("boolean")).build();

  private static final JsType NUMBER_TYPE =
      builder().addType("number").setPredicate(typeofTypePredicate("number")).build();

  private static final JsType STRING_TYPE =
      builder().addType("string").setPredicate(typeofTypePredicate("string")).build();

  private static final JsType MESSAGE_TYPE =
      builder()
          .addType("!jspb.Message")
          .setPredicate(instanceofTypePredicate(GoogRequire.create("jspb.Message").reference()))
          .addRequire(GoogRequire.create("jspb.Message"))
          .build();
  private static final JsType RAW_ARRAY_TYPE =
      builder().addType("!Array").setPredicate(ARRAY_IS_ARRAY).build();

  private static final JsType RAW_OBJECT_TYPE =
      builder().addType("!Object").setPredicate(GOOG_IS_OBJECT).build();

  private static final JsType NULL_OR_UNDEFINED_TYPE =
      builder()
          .addType("null")
          .addType("undefined")
          .setPredicate((value, codeGenerator) -> Optional.of(value.doubleEqualsNull()))
          .build();

  private static final JsType NULL_TYPE =
      builder()
          .addType("null")
          .setPredicate(
              (value, codeGenerator) -> Optional.of(value.tripleEquals(Expressions.LITERAL_NULL)))
          .build();

  private static final JsType UNDEFINED_TYPE =
      builder()
          .addType("undefined")
          .setPredicate(
              (value, codeGenerator) ->
                  Optional.of(value.tripleEquals(Expressions.LITERAL_UNDEFINED)))
          .build();

  private static final JsType IDOM_ATTRIBUTES =
      builder()
          .addType("function()")
          .addType("!" + ELEMENT_LIB_IDOM.alias() + ".IdomFunction")
          .addRequire(ELEMENT_LIB_IDOM)
          .addType("!" + GOOG_SOY_DATA.alias() + ".SanitizedHtmlAttribute")
          .addRequire(GOOG_SOY_DATA)
          .setPredicate(
              (value, codeGenerator) ->
                  Optional.of(
                      GOOG_IS_FUNCTION
                          .call(value)
                          .or(value.instanceOf(GOOG_HTML_SAFE_ATTRIBUTE), codeGenerator)))
          .build();

  private static final Expression SANITIZED_CONTENT_KIND =
      GOOG_SOY_DATA.dotAccess("SanitizedContentKind");
  // This cannot use a goog.require() alias to avoid conflicts with legacy
  // FilterHtmlAttributesDirective
  private static final Expression IS_IDOM_FUNCTION_TYPE =
      GoogRequire.create("google3.javascript.template.soy.soyutils_directives")
          .googModuleGet()
          .dotAccess("$$isIdomFunctionType");

  private static final JsType IDOM_HTML =
      builder()
          .addType("!" + GOOG_SOY_DATA.alias() + ".SanitizedHtml")
          .addRequire(GOOG_SOY_DATA)
          .addType("!safevalues.SafeHtml")
          .addRequire(GoogRequire.createTypeRequire("safevalues"))
          .addType("!" + ELEMENT_LIB_IDOM.alias() + ".IdomFunction")
          .addRequire(ELEMENT_LIB_IDOM)
          .addType("function(!incrementaldomlib.IncrementalDomRenderer): undefined")
          .addRequire(
              GoogRequire.createWithAlias(
                  "google3.javascript.template.soy.api_idom", "incrementaldomlib"))
          .setPredicate(
              (value, codeGenerator) ->
                  Optional.of(
                      IS_IDOM_FUNCTION_TYPE
                          .call(value, SANITIZED_CONTENT_KIND.dotAccess("HTML"))
                          .or(value.instanceOf(SAFEVALUES_SAFEHTML), codeGenerator)
                          .or(value.instanceOf(GOOG_SOY_DATA_SANITIZED_CONTENT), codeGenerator)))
          .build();

  private static final JsType VE_TYPE =
      builder()
          .addType("!soy.velog.$$VisualElement")
          .addRequire(SOY_VELOG)
          .setPredicate(instanceofTypePredicate(JsRuntime.SOY_VISUAL_ELEMENT))
          .build();

  private static final JsType VE_DATA_TYPE =
      builder()
          .addType("!soy.velog.$$VisualElementData")
          .addRequire(SOY_VELOG)
          .setPredicate(instanceofTypePredicate(JsRuntime.SOY_VISUAL_ELEMENT_DATA))
          .build();

  private static final JsTypeProducer SANITIZED_LOOSE;
  private static final JsTypeProducer SANITIZED_STRICT;

  static {
    EnumMap<SanitizedContentKind, JsType> types = new EnumMap<>(SanitizedContentKind.class);
    EnumMap<SanitizedContentKind, JsType> typesStrict = new EnumMap<>(SanitizedContentKind.class);
    for (SanitizedContentKind kind : SanitizedContentKind.values()) {
      types.put(kind, createSanitized(kind, /* isStrict= */ false));
      typesStrict.put(kind, createSanitized(kind, /* isStrict= */ true));
    }
    SANITIZED_LOOSE =
        (type) -> Preconditions.checkNotNull(types.get(((SanitizedType) type).getContentKind()));
    SANITIZED_STRICT =
        (type) ->
            Preconditions.checkNotNull(typesStrict.get(((SanitizedType) type).getContentKind()));
  }

  /** Returns a JS type with looser rules, allowing 1/0 for bools. */
  public static RecursiveJsTypeProducer forJsSrc() {
    return new RecursiveJsTypeProducerImpl(
        JsTypeKind.JSSRC, /* isStrict= */ false, ArrayTypeMode.ARRAY_OR_READONLY_ARRAY);
  }

  /** Returns a JS type for internal type checks and assertions. */
  public static RecursiveJsTypeProducer forJsTypeCheck() {
    return new RecursiveJsTypeProducerImpl(
        JsTypeKind.JSSRC, /* isStrict= */ false, ArrayTypeMode.READONLY_ARRAY);
  }

  /** Returns a JS type with strict rules. */
  public static RecursiveJsTypeProducer forJsSrcStrict() {
    return new RecursiveJsTypeProducerImpl(
        JsTypeKind.JSSRC, /* isStrict= */ true, ArrayTypeMode.ARRAY_OR_READONLY_ARRAY);
  }

  /** Returns a JS type for idom with looser rules, allowing 1/0 for bools. */
  public static RecursiveJsTypeProducer forIdomSrc() {
    return new RecursiveJsTypeProducerImpl(
        JsTypeKind.IDOMSRC, /* isStrict= */ false, ArrayTypeMode.ARRAY_OR_READONLY_ARRAY);
  }

  /** Returns a JS type for idom with looser rules, allowing 1/0 for bools. */
  public static RecursiveJsTypeProducer forIdomSrcTypeChecks() {
    return new RecursiveJsTypeProducerImpl(
        JsTypeKind.IDOMSRC, /* isStrict= */ false, ArrayTypeMode.READONLY_ARRAY);
  }

  /** Returns a JS type for idom with strict rules. */
  public static RecursiveJsTypeProducer forIdomSrcGetters() {
    return new RecursiveJsTypeProducerImpl(
        JsTypeKind.IDOMSRC, /* isStrict= */ true, ArrayTypeMode.READONLY_ARRAY);
  }

  /** Returns a JS type for idom with strict rules. */
  public static RecursiveJsTypeProducer forIdomSrcSetters() {
    return new RecursiveJsTypeProducerImpl(
        JsTypeKind.IDOMSRC, /* isStrict= */ true, ArrayTypeMode.ARRAY_OR_READONLY_ARRAY);
  }

  /** Returns a JS type for idom template type decls. */
  public static RecursiveJsTypeProducer forIdomSrcDeclarations() {
    return new RecursiveJsTypeProducerImpl(
        JsTypeKind.IDOMSRC, /* isStrict= */ true, ArrayTypeMode.ARRAY_OR_READONLY_ARRAY);
  }

  /** Returns a JS type for idom with strict rules. */
  public static RecursiveJsTypeProducer forIdomSrcState() {
    return new RecursiveJsTypeProducerImpl(
        JsTypeKind.IDOMSRC, /* isStrict= */ true, ArrayTypeMode.READONLY_ARRAY);
  }

  private enum JsTypeKind {
    JSSRC,
    IDOMSRC,
  }

  /**
   * How we should type-annotate arrays, as readonly, mutable, or a union.
   *
   * <p>Note that the last option (Array<T>|ReadonlyArray<T>) exists to allow Closure to use weak
   * type matching (see yaqs/6756474763427708928).
   */
  private enum ArrayTypeMode {
    READONLY_ARRAY,
    MUTABLE_ARRAY,
    ARRAY_OR_READONLY_ARRAY;

    static String formatArrayType(ArrayTypeMode arrayTypeMode, String elementType) {
      switch (arrayTypeMode) {
        case MUTABLE_ARRAY:
          return String.format("!Array<%s>", elementType);
        case READONLY_ARRAY:
          return String.format("!ReadonlyArray<%s>", elementType);
        case ARRAY_OR_READONLY_ARRAY:
          return String.format("(!Array<%s>|!ReadonlyArray<%s>)", elementType, elementType);
      }
      throw new AssertionError("Invalid ArrayTypeMode");
    }
  }

  /** An object that converts {@link SoyType} to {@link JsType}. */
  public interface JsTypeProducer {
    JsType get(SoyType type);
  }

  /** A {@link JsTypeProducer} with special hooks to support composition with recursion. */
  public interface RecursiveJsTypeProducer extends JsTypeProducer {

    /**
     * A version of {@link #get(SoyType)} with pluggable recursion. The implementation of this
     * function should always call `forRecursion.get()` rather than `this.get()` to recurse. This
     * allows implementations of `JsTypeProducer` to support composition patterns.
     */
    JsType get(SoyType type, JsTypeProducer forRecursion);

    @Override
    default JsType get(SoyType type) {
      return get(type, this);
    }
  }

  static final class RecursiveJsTypeProducerImpl implements RecursiveJsTypeProducer {
    private final JsTypeKind kind;
    private final JsTypeProducer sanitizedProducer;
    private ArrayTypeMode arrayTypeMode;

    /**
     * @param kind JS backend type
     * @param isStrict If true, generates stricter sanitized content types.
     * @param arrayTypeMode describes our typing convention for arrays.
     */
    public RecursiveJsTypeProducerImpl(
        JsTypeKind kind, boolean isStrict, ArrayTypeMode arrayTypeMode) {
      this.kind = kind;
      this.sanitizedProducer = isStrict ? SANITIZED_STRICT : SANITIZED_LOOSE;
      this.arrayTypeMode = arrayTypeMode;
    }

    /**
     * Returns a {@link JsType} corresponding to the given {@link SoyType}
     *
     * <p>TODO(lukes): consider adding a cache for all the computed types. The same type is probably
     * accessed many many times.
     *
     * @param soyType the soy type
     */
    @Override
    public JsType get(SoyType soyType, JsTypeProducer forRecursion) {
      switch (soyType.getKind()) {
        case NULL:
          return NULL_TYPE;
        case UNDEFINED:
          return UNDEFINED_TYPE;

        case ANY:
          return ANY_TYPE;

        case UNKNOWN:
        case INTERSECTION: // Closure compiler can't model intersections.
          return UNKNOWN_TYPE;

        case BOOL:
          return BOOLEAN_TYPE;

        case PROTO_ENUM:
          SoyProtoEnumType enumType = (SoyProtoEnumType) soyType;
          String enumTypeName = enumType.getNameForBackend(SoyBackendKind.JS_SRC);
          JsType.Builder enumBuilder =
              builder()
                  .addType("!" + enumTypeName)
                  .addRequire(GoogRequire.createTypeRequire(enumTypeName))
                  .setPredicate(typeofTypePredicate("number"));
          return enumBuilder.build();

        case FLOAT:
        case INT:
        case NUMBER:
          return NUMBER_TYPE;

        case STRING:
          return STRING_TYPE;

        case ATTRIBUTES:
          if (kind == JsTypeKind.IDOMSRC) {
            // idom has a different strategy for handling these
            return IDOM_ATTRIBUTES;
          }
        // fall through
        case HTML:
        case ELEMENT:
          if (kind == JsTypeKind.IDOMSRC) {
            // idom has a different strategy for handling these
            return IDOM_HTML;
          }
        // fall-through
        case CSS:
        case JS:
        case URI:
        case TRUSTED_RESOURCE_URI:
          return sanitizedProducer.get(soyType);

        case ITERABLE:
          SoyType itElmType = ((AbstractIterableType) soyType).getElementType();
          JsType jsItElmType = forRecursion.get(itElmType);
          return builder()
              .addType(String.format("!Iterable<%s>", jsItElmType.typeExpr()))
              .addRequires(jsItElmType.googRequires())
              .setPredicate(JsRuntime.SOY_IS_ITERABLE)
              .build();

        case LIST:
          AbstractIterableType listType = (AbstractIterableType) soyType;
          if (listType.isEmpty() || listType.getElementType().getKind() == SoyType.Kind.ANY) {
            return RAW_ARRAY_TYPE;
          }
          JsType element = forRecursion.get(listType.getElementType());

          return builder()
              .addType(ArrayTypeMode.formatArrayType(arrayTypeMode, element.typeExpr()))
              .addRequires(element.googRequires())
              .setPredicate(ARRAY_IS_ARRAY)
              .build();

        case SET:
          SoyType elmType = ((AbstractIterableType) soyType).getElementType();
          JsType jsElmType = forRecursion.get(elmType);
          return builder()
              .addType(String.format("!Set<%s>", jsElmType.typeExpr()))
              .addRequires(jsElmType.googRequires())
              .setPredicate(instanceofTypePredicate(id("Set")))
              .build();

        case LEGACY_OBJECT_MAP:
          {
            LegacyObjectMapType mapType = (LegacyObjectMapType) soyType;
            if (mapType.getKeyType().getKind() == SoyType.Kind.ANY
                && mapType.getValueType().getKind() == SoyType.Kind.ANY) {
              return RAW_OBJECT_TYPE;
            }
            JsType keyTypeName = forRecursion.get(mapType.getKeyType());
            JsType valueTypeName = forRecursion.get(mapType.getValueType());
            return builder()
                .addType(
                    String.format(
                        "!Object<%s,%s>", keyTypeName.typeExpr(), valueTypeName.typeExpr()))
                .addRequires(keyTypeName.googRequires())
                .addRequires(valueTypeName.googRequires())
                .setPredicate(GOOG_IS_OBJECT)
                .build();
          }
        case MAP:
          {
            MapType mapType = (MapType) soyType;
            SoyType keyType = mapType.getKeyType();
            SoyType.Kind keyKind = keyType.getKind();
            // Soy key type of string should translate to a JS key type of string.
            // get(StringType.getInstance()) normally translates to
            // string|!goog.soy.data.UnsanitizedText, but ES6 Maps always use instance equality for
            // lookups. Using UnsanitizedText instances as keys in Soy maps would cause unexpected
            // behavior (usually a failed map lookup), so don't generate signatures that allow it.
            JsType keyTypeName =
                keyKind == SoyType.Kind.STRING ? STRING_TYPE : forRecursion.get(keyType);
            JsType valueTypeName = forRecursion.get(mapType.getValueType());
            return builder()
                .addType(
                    String.format(
                        "!ReadonlyMap<%s,%s>", keyTypeName.typeExpr(), valueTypeName.typeExpr()))
                .addRequires(keyTypeName.googRequires())
                .addRequires(valueTypeName.googRequires())
                .addRequire(GoogRequire.create("soy.map"))
                .setPredicate(instanceofTypePredicate(id("Map")))
                .build();
          }
        case MESSAGE:
          return MESSAGE_TYPE;
        case PROTO:
          SoyProtoType protoType = (SoyProtoType) soyType;
          String protoTypeName = protoType.getJsName(ProtoUtils.MutabilityMode.READONLY);
          return builder()
              .addType("!" + protoTypeName)
              .addRequire(GoogRequire.createTypeRequire(protoTypeName))
              .setPredicate(instanceofTypePredicate(JsRuntime.protoConstructor(protoType)))
              .build();

        case RECORD:
          {
            RecordType recordType = (RecordType) soyType;
            Builder builder = builder();
            Map<String, String> members = new LinkedHashMap<>();
            for (RecordType.Member member : recordType.getMembers()) {
              JsType memberType = forRecursion.get(member.checkedType());
              builder.addRequires(memberType.googRequires());
              members.put(
                  member.name(), memberType.typeExprForRecordMember(/* isOptional= */ false));
            }
            return builder
                .addType(
                    members.isEmpty()
                        ? "!Object"
                        : "{" + Joiner.on(", ").withKeyValueSeparator(": ").join(members) + ",}")
                .setPredicate(GOOG_IS_OBJECT)
                .build();
          }

        case UNION:
          {
            UnionType unionType = (UnionType) soyType;
            Builder builder = builder();
            Set<JsType> types = new LinkedHashSet<>();
            boolean isNullable = SoyTypes.isNullable(unionType);
            boolean isUndefinable = SoyTypes.isUndefinable(unionType);
            // handle null first so that if other type tests dereference the param they won't fail
            if (isNullable) {
              if (isUndefinable) {
                builder.addTypes(NULL_OR_UNDEFINED_TYPE.typeExpressions);
                types.add(NULL_OR_UNDEFINED_TYPE);
              } else {
                builder.addTypes(NULL_TYPE.typeExpressions);
                types.add(NULL_TYPE);
              }
            } else if (isUndefinable) {
              builder.addTypes(UNDEFINED_TYPE.typeExpressions);
              types.add(UNDEFINED_TYPE);
            }
            for (SoyType member : unionType.getMembers()) {
              if (member.isNullOrUndefined()) {
                continue; // handled above
              }
              JsType memberType = forRecursion.get(member);
              builder.addRequires(memberType.extraRequires);
              builder.addTypes(memberType.typeExpressions);
              types.add(memberType);
            }
            return builder
                .setPredicate(
                    (value, codeGenerator) -> {
                      Expression result = null;
                      // TODO(lukes): this will cause reevaluations, resolve by conditionally
                      // bouncing into a a temporary variable or augmenting the codechunk api to do
                      // this automatically.
                      for (JsType memberType : types) {
                        Optional<Expression> typeAssertion =
                            memberType.getTypeAssertion(value, codeGenerator);
                        if (!typeAssertion.isPresent()) {
                          return Optional.empty();
                        }
                        if (result == null) {
                          result = typeAssertion.get();
                        } else {
                          result = result.or(typeAssertion.get(), codeGenerator);
                        }
                      }
                      return Optional.of(result);
                    })
                .build();
          }
        case VE:
          return VE_TYPE;
        case VE_DATA:
          return VE_DATA_TYPE;
        case TEMPLATE:
          {
            TemplateType templateType = (TemplateType) soyType;
            Builder builder = builder();
            Map<String, String> parameters = new LinkedHashMap<>();
            for (TemplateType.Parameter parameter : templateType.getParameters()) {
              JsType forSoyType =
                  getWithArrayMode(ArrayTypeMode.ARRAY_OR_READONLY_ARRAY, parameter.getType());
              builder.addRequires(forSoyType.googRequires());
              parameters.put(
                  parameter.getName(),
                  forSoyType.typeExprForRecordMember(/* isOptional= */ !parameter.isRequired()));
            }
            JsType forReturnType = templateReturnType(templateType.getContentKind(), kind);
            builder.addRequires(forReturnType.googRequires());
            // Trailing comma is important to prevent parsing ambiguity for the unknown type
            String parametersType =
                parameters.isEmpty()
                    ? "null"
                    : "{" + Joiner.on(", ").withKeyValueSeparator(": ").join(parameters) + ",}";
            String returnType = forReturnType.typeExpr();
            if (kind == JsTypeKind.IDOMSRC
                && templateType.getContentKind().getSanitizedContentKind()
                    != SanitizedContentKind.TEXT) {
              builder.addRequire(
                  GoogRequire.createWithAlias(
                      "google3.javascript.template.soy.api_idom", "incrementaldomlib"));
              builder.addType(
                  String.format(
                      "function(!incrementaldomlib.IncrementalDomRenderer, %s, %s):(%s)",
                      parametersType, "?(goog.soy.IjData)=", returnType));
            } else {
              builder.addType(
                  String.format(
                      "function(%s, %s):(%s)", parametersType, "?(goog.soy.IjData)=", returnType));
              builder.addRequire(JsRuntime.GOOG_SOY.toRequireType());
            }
            builder.setPredicate(GOOG_IS_FUNCTION);
            return builder.build();
          }
        case FUNCTION:
          {
            FunctionType functionType = (FunctionType) soyType;
            Builder builder = builder();
            List<String> parameters = new ArrayList<>();
            for (FunctionType.Parameter parameter : functionType.getParameters()) {
              JsType forSoyType =
                  getWithArrayMode(ArrayTypeMode.ARRAY_OR_READONLY_ARRAY, parameter.getType());
              builder.addRequires(forSoyType.googRequires());
              parameters.add(forSoyType.typeExpr());
            }
            JsType forReturnType = get(functionType.getReturnType());
            builder.addRequires(forReturnType.googRequires());
            builder.addType(
                String.format(
                    "function(%s):(%s)", String.join(",", parameters), forReturnType.typeExpr()));
            builder.addRequire(JsRuntime.GOOG_SOY.toRequireType());
            builder.setPredicate(GOOG_IS_FUNCTION);
            return builder.build();
          }
        case NAMED:
          NamedType namedType = (NamedType) soyType;
          String namespace =
              kind == JsTypeKind.IDOMSRC
                  ? namedType.getNamespace() + ".incrementaldom"
                  : namedType.getNamespace();
          String fullName = namespace + "." + namedType.getName();
          return builder()
              .addType("!" + fullName)
              .addRequire(GoogRequire.create(namespace))
              .setPredicate(TypePredicate.NO_OP)
              .build();
        case INDEXED:
          IndexedType indexedType = (IndexedType) soyType;
          String type = "?";
          // Find the named type that originally declared this property. We need to do this because
          // the typedef for the member is only declared relative to the {typedef} in which it's
          // defined, not copied to every {typedef} that extends it.
          // If the same property is defined in multiple type defs the behavior of this algorithm is
          // undefined. Incompatible types will have been resolved as `never` and should have failed
          // compilation.
          Deque<SoyType> stack = new ArrayDeque<>();
          stack.add(indexedType.getType());
          WHILE:
          while (!stack.isEmpty()) {
            SoyType member = stack.removeLast();
            if (member.getKind() != Kind.NAMED) {
              continue;
            }
            NamedType namedMember = (NamedType) member;
            SoyType namedValue = namedMember.getType();
            ImmutableSet<SoyType> components = ImmutableSet.of();
            if (namedValue instanceof RecordType) {
              components = ImmutableSet.of(namedValue);
            } else if (namedValue instanceof IntersectionType) {
              components = ((IntersectionType) namedValue).getMembers();
            }

            for (SoyType component : components) {
              if (component instanceof NamedType) {
                stack.add(component);
              } else if (component instanceof RecordType) {
                RecordType.Member recMember =
                    ((RecordType) component).getMember(indexedType.getProperty());
                if (recMember != null) {
                  type =
                      matchNullishToBang(
                          IndexedType.jsSynthenticTypeDefName(
                              forRecursion.get(namedMember).typeExpr(), indexedType.getProperty()),
                          SoyTypes.isNullish(recMember.checkedType()));
                  break WHILE;
                }
              }
            }
          }
          JsType baseType = forRecursion.get(indexedType.getType());
          return builder()
              .addType(type)
              .addRequires(baseType.googRequires())
              .setPredicate(TypePredicate.NO_OP)
              .build();
        case NAMESPACE:
        case PROTO_TYPE:
        case PROTO_ENUM_TYPE:
        case PROTO_EXTENSION:
        case TEMPLATE_TYPE:
        case NEVER:
      }
      throw new AssertionError("unhandled soytype: " + soyType);
    }

    private JsType getWithArrayMode(ArrayTypeMode mode, SoyType type) {
      ArrayTypeMode oldMode = arrayTypeMode;
      arrayTypeMode = mode;
      JsType rv = get(type); // This breaks the recursion contract.
      arrayTypeMode = oldMode;
      return rv;
    }
  }

  static String matchNullishToBang(String type, boolean nullish) {
    if (nullish) {
      if (type.startsWith("!")) {
        return type.substring(1);
      }
    } else {
      if (!type.startsWith("!")) {
        return "!" + type;
      }
    }
    return type;
  }

  public static String toRecord(Map<String, String> record) {
    StringBuilder sb = new StringBuilder();
    sb.append("{\n *  ");
    Joiner.on(",\n *  ").withKeyValueSeparator(": ").appendTo(sb, record);
    // trailing comma in record is important in case the last record member is the
    // unknown type
    sb.append(",\n * }");
    return sb.toString();
  }

  public static JsType templateReturnTypeForIdom(SanitizedContentKind templateReturnType) {
    return templateReturnType(templateReturnType, JsTypeKind.IDOMSRC);
  }

  public static JsType templateReturnTypeForJsSrc(SanitizedContentKind templateReturnType) {
    return templateReturnType(templateReturnType, JsTypeKind.JSSRC);
  }

  private static JsType templateReturnType(
      TemplateContentKind templateReturnType, JsTypeKind kind) {
    return templateReturnType(templateReturnType.getSanitizedContentKind(), kind);
  }

  private static JsType templateReturnType(SanitizedContentKind contentKind, JsTypeKind kind) {
    switch (contentKind) {
      case TEXT:
        return STRING_TYPE;
      case ATTRIBUTES:
      case CSS:
      case HTML_ELEMENT:
      case HTML:
      case JS:
      case URI:
      case TRUSTED_RESOURCE_URI:
        Builder builder = builder();
        String type = NodeContentKinds.toJsSanitizedContentCtorName(contentKind);
        if (kind == JsTypeKind.IDOMSRC
            && (contentKind.isHtml() || contentKind == SanitizedContentKind.ATTRIBUTES)) {
          builder.addType("void");
        } else {
          builder.addType("!goog.soy.data." + type);
          builder.addRequire(GoogRequire.createTypeRequire("goog.soy.data"));
        }
        // Type predicate is not used for template return types.
        builder.setPredicate(TypePredicate.NO_OP);
        return builder.build();
    }
    throw new IllegalStateException(
        "Unsupported return type found for template type; this should have been caught earlier"
            + " in parsing.");
  }

  /** Can generate code chunks which validate the 'type' of a given code chunk. */
  private interface TypePredicate {
    TypePredicate NO_OP = (value, codeGenerator) -> Optional.empty();

    /**
     * Returns a code chunk that evaluates to {@code true} if the given chunk matches the predicate
     * and {@code false} otherwise. Returns {@link Optional#empty()} if there is no validation to be
     * done.
     */
    Optional<Expression> maybeCheck(Expression value, Generator codeGenerator);
  }

  /** Builds and returns a TypePredicate comparing a value's 'typeof' against the given 'type' */
  private static TypePredicate typeofTypePredicate(String type) {
    return (value, codeGenerator) ->
        Optional.of(value.typeOf().tripleEquals(Expressions.stringLiteral(type)));
  }

  private static TypePredicate instanceofTypePredicate(Expression constructor) {
    return (value, codeGenerator) -> Optional.of(value.instanceOf(constructor));
  }

  private final ImmutableSortedSet<String> typeExpressions;
  private final ImmutableSet<GoogRequire> extraRequires;
  private final TypePredicate predicate;

  private JsType(Builder builder) {
    // Sort for determinism, order doesn't matter.
    this.typeExpressions = builder.typeExpressions.build();
    checkArgument(!typeExpressions.isEmpty());
    this.extraRequires = builder.extraRequires.build();
    this.predicate = checkNotNull(builder.predicate);
  }

  /** Returns a type expression. */
  public String typeExpr() {
    return Joiner.on('|').join(typeExpressions);
  }

  public String typeExprForExtends() {
    // Cannot have "!" in "@extends T".
    Preconditions.checkState(typeExpressions.size() == 1);
    String type = Iterables.getOnlyElement(typeExpressions);
    return type.startsWith("!") ? type.substring(1) : type;
  }

  /**
   * Returns a type expression for a record member. In some cases this requires additional parens.
   */
  public String typeExprForRecordMember(boolean isOptional) {
    if (typeExpressions.size() > 1 || isOptional) {
      // Needs parens. Optional fields not supported:
      // https://github.com/google/closure-compiler/issues/126
      return "("
          + typeExpr()
          + (isOptional && !typeExpressions.contains("undefined") ? "|undefined" : "")
          + ")";
    }
    return typeExpr();
  }

  /** Returns a type expression suitable for a function return type */
  public String typeExprForFunctionReturn() {
    if (typeExpressions.size() > 1) {
      // needs parens
      return "(" + typeExpr() + ")";
    }
    return typeExpr();
  }

  @Override
  public ImmutableSet<GoogRequire> googRequires() {
    return extraRequires;
  }

  /**
   * Returns a code chunk that generates a 'test' for whether or not the given value has this type,
   * or {@link Optional#empty()} if no test is necessary.
   */
  Optional<Expression> getTypeAssertion(Expression value, Generator codeGenerator) {
    return predicate.maybeCheck(value, codeGenerator);
  }

  /**
   * Returns a Soy assertion expression that asserts that the given value has this type, or {@link
   * Optional#empty()} if no assertion is necessary.
   */
  public Optional<Expression> getSoyParamTypeAssertion(
      Expression value, String valueName, String paramKind, Generator codeGenerator) {
    Optional<Expression> typeAssertion = getTypeAssertion(value, codeGenerator);
    if (!typeAssertion.isPresent()) {
      return Optional.empty();
    }

    return Optional.of(
        SOY_ASSERT_PARAM_TYPE.call(
            typeAssertion.get(),
            stringLiteral(valueName),
            value,
            stringLiteral(paramKind),
            stringLiteral(typeExpr())));
  }

  private static JsType createSanitized(SanitizedContentKind kind, boolean isStrict) {
    if (kind == SanitizedContentKind.TEXT) {
      return STRING_TYPE;
    }
    String type = NodeContentKinds.toJsSanitizedContentCtorName(kind);
    // NOTE: we don't add goog.requires for all these alias types.  This is 'ok' since we never
    // invoke a method on them directly (instead they just get passed around and eventually get
    // consumed by an escaper function.
    // TODO(lukes): maybe we should define typedefs for these?
    Builder builder = builder();
    builder.addType("!soy.$$EMPTY_STRING_");
    builder.addRequire(JsRuntime.SOY);
    builder.addType("!goog.soy.data." + type);
    builder.addRequire(GoogRequire.createTypeRequire("goog.soy.data"));
    if (!isStrict) {
      // All the sanitized types have an .isCompatibleWith method for testing for allowed types
      // NOTE: this actually allows 'string' to be passed, which is inconsistent with other backends
      // We allow string or unsanitized type to be passed where a
      // sanitized type is specified - it just means that the text will
      // be escaped.
      builder.addType("string");
    } else {
      builder.addType("!soy.$$EMPTY_STRING_");
    }
    // add extra alternate types
    // TODO(lukes): instead of accepting alternates we should probably just coerce to sanitized
    // content.  using these wide unions everywhere is confusing.
    switch (kind) {
      case CSS:
        builder.addType("string");
        break;
      case HTML_ELEMENT:
      case HTML:
        builder.addType("!safevalues.SafeHtml");
        builder.addRequire(GoogRequire.createTypeRequire("safevalues"));
        break;
      case JS:
        builder.addType("!safevalues.SafeScript");
        builder.addRequire(GoogRequire.createTypeRequire("safevalues"));
        break;
      case ATTRIBUTES:
      case TEXT:
        // nothing extra
        break;
      case TRUSTED_RESOURCE_URI:
        builder.addType("!safevalues.TrustedResourceUrl");
        builder.addRequire(GoogRequire.createTypeRequire("safevalues"));
        break;
      case URI:
        builder.addType("!safevalues.TrustedResourceUrl");
        builder.addType("!safevalues.SafeUrl");
        builder.addRequire(GoogRequire.createTypeRequire("safevalues"));
        builder.addType("!goog.Uri");
        builder.addRequire(GoogRequire.createTypeRequire("goog.Uri"));
        break;
    }

    // TODO(lukes): consider eliminating the isCompatibleWith method and just inlining the
    // assertions, or as mentioned above we could just coerce to SanitizedContent consistently
    String compatibleWithString = isStrict ? "isCompatibleWithStrict" : "isCompatibleWith";
    return builder
        .setPredicate(
            (value, codeGenerator) ->
                Optional.of(sanitizedContentType(kind).dotAccess(compatibleWithString).call(value)))
        .build();
  }

  public static JsType localTypedef(String symbol) {
    return builder().addType(symbol).setPredicate(TypePredicate.NO_OP).build();
  }

  private static Builder builder() {
    return new Builder();
  }

  // TODO(b/72863178): make it harder to construct a JsType without a goog.require().
  private static final class Builder {
    final ImmutableSortedSet.Builder<String> typeExpressions = ImmutableSortedSet.naturalOrder();
    final ImmutableSet.Builder<GoogRequire> extraRequires = ImmutableSet.builder();
    @Nullable TypePredicate predicate;

    @CanIgnoreReturnValue
    Builder addType(String typeExpr) {
      typeExpressions.add(typeExpr);
      return this;
    }

    @CanIgnoreReturnValue
    Builder addTypes(Iterable<String> typeExpressions) {
      this.typeExpressions.addAll(typeExpressions);
      return this;
    }

    @CanIgnoreReturnValue
    Builder addRequire(GoogRequire symbol) {
      extraRequires.add(symbol);
      return this;
    }

    @CanIgnoreReturnValue
    Builder addRequires(Iterable<GoogRequire> symbols) {
      extraRequires.addAll(symbols);
      return this;
    }

    @CanIgnoreReturnValue
    Builder setPredicate(TypePredicate predicate) {
      checkState(this.predicate == null);
      this.predicate = checkNotNull(predicate);
      return this;
    }

    /** Sets a predicate which simply invokes the given function. */
    Builder setPredicate(Expression predicateFunction) {
      return setPredicate(
          (value, codeGenerator) -> Optional.of(checkNotNull(predicateFunction).call(value)));
    }

    JsType build() {
      return new JsType(this);
    }
  }
}
