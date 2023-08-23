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
import static com.google.template.soy.jssrc.dsl.Expressions.stringLiteral;
import static com.google.template.soy.jssrc.internal.JsRuntime.ARRAY_IS_ARRAY;
import static com.google.template.soy.jssrc.internal.JsRuntime.ELEMENT_LIB_IDOM;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_HTML_SAFE_ATTRIBUTE;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_HTML_SAFE_HTML;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_IS_FUNCTION;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_IS_OBJECT;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_SOY_DATA_SANITIZED_CONTENT;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_ASSERT_PARAM_TYPE;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_MAP_IS_SOY_MAP;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_VELOG;
import static com.google.template.soy.jssrc.internal.JsRuntime.sanitizedContentType;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Maps;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.template.soy.base.SoyBackendKind;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.base.internal.TemplateContentKind;
import com.google.template.soy.data.internalutils.NodeContentKinds;
import com.google.template.soy.internal.proto.ProtoUtils;
import com.google.template.soy.jssrc.dsl.CodeChunk.Generator;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.jssrc.dsl.Expressions;
import com.google.template.soy.jssrc.dsl.GoogRequire;
import com.google.template.soy.types.LegacyObjectMapType;
import com.google.template.soy.types.ListType;
import com.google.template.soy.types.MapType;
import com.google.template.soy.types.RecordType;
import com.google.template.soy.types.SanitizedType;
import com.google.template.soy.types.SoyProtoEnumType;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.TemplateType;
import com.google.template.soy.types.UnionType;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
public final class JsType {

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
          .setPredicate(
              (value, codeGenerator) ->
                  Optional.of(value.instanceOf(GoogRequire.create("jspb.Message").reference())))
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

  private static final ImmutableMap<SanitizedContentKind, JsType> SANITIZED_TYPES;
  private static final ImmutableMap<SanitizedContentKind, JsType> SANITIZED_TYPES_STRICT;

  private static final JsType IDOM_ATTRIBUTES =
      builder()
          .addType("function()")
          .addType("!" + ELEMENT_LIB_IDOM.alias() + ".IdomFunction")
          .addType("!goog.soy.data.SanitizedHtmlAttribute")
          .addRequire(ELEMENT_LIB_IDOM)
          .addRequire(GoogRequire.createTypeRequire("goog.soy.data.SanitizedHtmlAttribute"))
          .setPredicate(
              (value, codeGenerator) ->
                  Optional.of(
                      GOOG_IS_FUNCTION
                          .call(value)
                          .or(value.instanceOf(GOOG_HTML_SAFE_ATTRIBUTE), codeGenerator)))
          .build();

  private static final GoogRequire SANITIZED_CONTENT_KIND =
      GoogRequire.createWithAlias("goog.soy.data.SanitizedContentKind", "SanitizedContentKind");
  // This cannot use a goog.require() alias to avoid conflicts with legacy
  // FilterHtmlAttributesDirective
  private static final Expression IS_IDOM_FUNCTION_TYPE =
      GoogRequire.create("google3.javascript.template.soy.soyutils_directives")
          .googModuleGet()
          .dotAccess("$$isIdomFunctionType");

  private static final JsType IDOM_HTML =
      builder()
          .addType("!goog.soy.data.SanitizedHtml")
          .addType("!goog.html.SafeHtml")
          .addRequire(GoogRequire.createTypeRequire("goog.html.SafeHtml"))
          .addRequire(GoogRequire.createTypeRequire("goog.soy.data.SanitizedHtml"))
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
                          .or(value.instanceOf(GOOG_HTML_SAFE_HTML), codeGenerator)
                          .or(value.instanceOf(GOOG_SOY_DATA_SANITIZED_CONTENT), codeGenerator)))
          .build();

  private static final JsType VE_TYPE =
      builder()
          .addType("!soy.velog.$$VisualElement")
          .addRequire(SOY_VELOG)
          .setPredicate(
              (value, codeGenerator) -> Optional.of(value.instanceOf(JsRuntime.SOY_VISUAL_ELEMENT)))
          .build();

  private static final JsType VE_DATA_TYPE =
      builder()
          .addType("!soy.velog.$$VisualElementData")
          .addRequire(SOY_VELOG)
          .setPredicate(
              (value, codeGenerator) ->
                  Optional.of(value.instanceOf(JsRuntime.SOY_VISUAL_ELEMENT_DATA)))
          .build();

  static {
    EnumMap<SanitizedContentKind, JsType> types = new EnumMap<>(SanitizedContentKind.class);
    EnumMap<SanitizedContentKind, JsType> typesStrict = new EnumMap<>(SanitizedContentKind.class);
    for (SanitizedContentKind kind : SanitizedContentKind.values()) {
      types.put(kind, createSanitized(kind, /* isStrict= */ false));
      typesStrict.put(kind, createSanitized(kind, /* isStrict= */ true));
    }
    SANITIZED_TYPES = Maps.immutableEnumMap(types);
    SANITIZED_TYPES_STRICT = Maps.immutableEnumMap(typesStrict);
  }

  /** Returns a JS type with looser rules, allowing 1/0 for bools or nullable protos. */
  public static JsType forJsSrc(SoyType soyType) {
    return forSoyType(
        soyType,
        JsTypeKind.JSSRC,
        /* isStrict= */ false,
        ArrayTypeMode.ARRAY_OR_READONLY_ARRAY,
        MessageTypeMode.READONLY,
        /* includeNullForMessages= */ true);
  }

  /** Returns a JS type for internal type checks and assertions. */
  public static JsType forJsTypeCheck(SoyType soyType) {
    return forSoyType(
        soyType,
        JsTypeKind.JSSRC,
        /* isStrict= */ false,
        ArrayTypeMode.READONLY_ARRAY,
        MessageTypeMode.READONLY,
        /* includeNullForMessages= */ false);
  }

  /** Returns a JS type with strict rules. */
  public static JsType forJsSrcStrict(SoyType soyType) {
    return forSoyType(
        soyType,
        JsTypeKind.JSSRC,
        /* isStrict= */ true,
        ArrayTypeMode.ARRAY_OR_READONLY_ARRAY,
        MessageTypeMode.READONLY,
        /* includeNullForMessages= */ false);
  }

  /** Returns a JS type for idom with looser rules, allowing 1/0 for bools or nullable protos. */
  public static JsType forIncrementalDom(SoyType soyType) {
    return forSoyType(
        soyType,
        JsTypeKind.IDOMSRC,
        /* isStrict= */ false,
        ArrayTypeMode.ARRAY_OR_READONLY_ARRAY,
        MessageTypeMode.READONLY,
        /* includeNullForMessages= */ true);
  }

  /** Returns a JS type for idom with looser rules, allowing 1/0 for bools or nullable protos. */
  public static JsType forIncrementalDomTypeChecks(SoyType soyType) {
    return forSoyType(
        soyType,
        JsTypeKind.IDOMSRC,
        /* isStrict= */ false,
        ArrayTypeMode.READONLY_ARRAY,
        MessageTypeMode.READONLY,
        /* includeNullForMessages= */ false);
  }

  /** Returns a JS type for idom with strict rules. */
  public static JsType forIncrementalDomGetters(SoyType soyType) {
    return forSoyType(
        soyType,
        JsTypeKind.IDOMSRC,
        /* isStrict= */ true,
        ArrayTypeMode.READONLY_ARRAY,
        MessageTypeMode.READONLY,
        /* includeNullForMessages= */ false);
  }

  /** Returns a JS type for idom with strict rules. */
  public static JsType forIncrementalDomSetters(SoyType soyType) {
    return forSoyType(
        soyType,
        JsTypeKind.IDOMSRC,
        /* isStrict= */ true,
        ArrayTypeMode.ARRAY_OR_READONLY_ARRAY,
        MessageTypeMode.READONLY,
        /* includeNullForMessages= */ false);
  }

  /** Returns a JS type for idom template type decls. */
  public static JsType forIncrementalDomDeclarations(SoyType soyType) {
    return forSoyType(
        soyType,
        JsTypeKind.IDOMSRC,
        /* isStrict= */ true,
        ArrayTypeMode.ARRAY_OR_READONLY_ARRAY,
        MessageTypeMode.READONLY,
        /* includeNullForMessages= */ false);
  }

  /** Returns a JS type for idom with strict rules. */
  public static JsType forIncrementalDomState(SoyType soyType) {
    return forSoyType(
        soyType,
        JsTypeKind.IDOMSRC,
        /* isStrict= */ true,
        ArrayTypeMode.READONLY_ARRAY,
        MessageTypeMode.READONLY,
        /* includeNullForMessages= */ false);
  }

  private enum JsTypeKind {
    JSSRC,
    IDOMSRC,
  }

  /** How we should type-annotate proto messages, as readonly or just mutable. */
  private enum MessageTypeMode {
    ONLY_MUTABLE,
    READONLY,
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

  /**
   * Returns a {@link JsType} corresponding to the given {@link SoyType}
   *
   * <p>TODO(lukes): consider adding a cache for all the computed types. The same type is probably
   * accessed many many times.
   *
   * @param soyType the soy type
   * @param kind JS backend type
   * @param isStrict If true, generates stricter types than default (e.g. boolean values cannot be 0
   *     or 1).
   * @param arrayTypeMode describes our typing convention for arrays.
   * @param messageTypeMode describes our typing convention for messages.
   */
  private static JsType forSoyType(
      SoyType soyType,
      JsTypeKind kind,
      boolean isStrict,
      ArrayTypeMode arrayTypeMode,
      MessageTypeMode messageTypeMode,
      boolean includeNullForMessages) {
    switch (soyType.getKind()) {
      case NULL:
      case UNDEFINED:
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
        JsType.Builder enumBuilder =
            builder()
                .addType("!" + enumTypeName)
                .addRequire(GoogRequire.createTypeRequire(enumTypeName))
                .setPredicate(typeofTypePredicate("number"));
        if (!isStrict) {
          // TODO(lukes): stop allowing number?, just allow the enum
          enumBuilder.addType("number");
        }
        return enumBuilder.build();

      case FLOAT:
      case INT:
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
        return isStrict
            ? SANITIZED_TYPES_STRICT.get(((SanitizedType) soyType).getContentKind())
            : SANITIZED_TYPES.get(((SanitizedType) soyType).getContentKind());

      case LIST:
        ListType listType = (ListType) soyType;
        if (listType.equals(ListType.EMPTY_LIST)
            || listType.getElementType().getKind() == SoyType.Kind.ANY) {
          return RAW_ARRAY_TYPE;
        }
        JsType element =
            forSoyType(
                listType.getElementType(),
                kind,
                isStrict,
                arrayTypeMode,
                messageTypeMode,
                includeNullForMessages);

        return builder()
            .addType(ArrayTypeMode.formatArrayType(arrayTypeMode, element.typeExpr()))
            .addRequires(element.getGoogRequires())
            .setPredicate(ARRAY_IS_ARRAY)
            .build();

      case LEGACY_OBJECT_MAP:
        {
          LegacyObjectMapType mapType = (LegacyObjectMapType) soyType;
          if (mapType.getKeyType().getKind() == SoyType.Kind.ANY
              && mapType.getValueType().getKind() == SoyType.Kind.ANY) {
            return RAW_OBJECT_TYPE;
          }
          JsType keyTypeName =
              forSoyType(
                  mapType.getKeyType(),
                  kind,
                  isStrict,
                  arrayTypeMode,
                  messageTypeMode,
                  includeNullForMessages);
          JsType valueTypeName =
              forSoyType(
                  mapType.getValueType(),
                  kind,
                  isStrict,
                  arrayTypeMode,
                  messageTypeMode,
                  includeNullForMessages);
          return builder()
              .addType(
                  String.format("!Object<%s,%s>", keyTypeName.typeExpr(), valueTypeName.typeExpr()))
              .addRequires(keyTypeName.getGoogRequires())
              .addRequires(valueTypeName.getGoogRequires())
              .setPredicate(GOOG_IS_OBJECT)
              .build();
        }
      case MAP:
        {
          MapType mapType = (MapType) soyType;
          SoyType keyType = mapType.getKeyType();
          SoyType.Kind keyKind = keyType.getKind();
          Preconditions.checkState(
              MapType.isAllowedKeyType(keyType), "%s is not allowed as a map key", keyType);
          // Soy key type of string should translate to a JS key type of string.
          // forSoyType(StringType.getInstance()) normally translates to
          // string|!goog.soy.data.UnsanitizedText, but ES6 Maps always use instance equality for
          // lookups. Using UnsanitizedText instances as keys in Soy maps would cause unexpected
          // behavior (usually a failed map lookup), so don't generate signatures that allow it.
          JsType keyTypeName =
              keyKind == SoyType.Kind.STRING
                  ? STRING_TYPE
                  : forSoyType(
                      keyType,
                      kind,
                      isStrict,
                      arrayTypeMode,
                      messageTypeMode,
                      includeNullForMessages);
          JsType valueTypeName =
              forSoyType(
                  mapType.getValueType(),
                  kind,
                  isStrict,
                  arrayTypeMode,
                  messageTypeMode,
                  includeNullForMessages);
          return builder()
              .addType(
                  String.format(
                      "!soy.map.Map<%s,%s>", keyTypeName.typeExpr(), valueTypeName.typeExpr()))
              .addRequires(keyTypeName.getGoogRequires())
              .addRequires(valueTypeName.getGoogRequires())
              .addRequire(GoogRequire.create("soy.map"))
              .setPredicate(SOY_MAP_IS_SOY_MAP)
              .build();
        }
      case MESSAGE:
        return MESSAGE_TYPE;
      case PROTO:
        SoyProtoType protoType = (SoyProtoType) soyType;
        String protoTypeName =
            protoType.getJsName(
                /* mutabilityMode= */ messageTypeMode == MessageTypeMode.READONLY
                    ? ProtoUtils.MutabilityMode.READONLY
                    : ProtoUtils.MutabilityMode.MUTABLE);
        return builder()
            .addType((isStrict || !includeNullForMessages ? "!" : "?") + protoTypeName)
            .addRequire(GoogRequire.createTypeRequire(protoTypeName))
            .setPredicate(
                (value, codeGenerator) ->
                    Optional.of(value.instanceOf(JsRuntime.protoConstructor(protoType))))
            .build();

      case RECORD:
        {
          RecordType recordType = (RecordType) soyType;
          Preconditions.checkArgument(!recordType.getMembers().isEmpty());
          Builder builder = builder();
          Map<String, String> members = new LinkedHashMap<>();
          for (RecordType.Member member : recordType.getMembers()) {
            JsType forSoyType =
                forSoyType(
                    member.checkedType(),
                    kind,
                    isStrict,
                    arrayTypeMode,
                    messageTypeMode,
                    includeNullForMessages);
            builder.addRequires(forSoyType.getGoogRequires());
            members.put(member.name(), forSoyType.typeExprForRecordMember(/* isOptional= */ false));
          }
          return builder
              // trailing comma is important to prevent parsing ambiguity for the unknown type
              .addType("{" + Joiner.on(", ").withKeyValueSeparator(": ").join(members) + ",}")
              .setPredicate(GOOG_IS_OBJECT)
              .build();
        }

      case UNION:
        {
          UnionType unionType = (UnionType) soyType;
          Builder builder = builder();
          Set<JsType> types = new LinkedHashSet<>();
          boolean isNullable = unionType.isNullable();
          // handle null first so that if other type tests dereference the param they won't fail
          if (isNullable) {
            builder.addTypes(NULL_OR_UNDEFINED_TYPE.typeExpressions);
            types.add(NULL_OR_UNDEFINED_TYPE);
          }
          for (SoyType member : unionType.getMembers()) {
            if (member.getKind() == Kind.NULL) {
              continue; // handled above
            }
            JsType memberType =
                forSoyType(
                    member, kind, isStrict, arrayTypeMode, messageTypeMode, includeNullForMessages);
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
                forSoyType(
                    parameter.getType(),
                    kind,
                    isStrict,
                    ArrayTypeMode.ARRAY_OR_READONLY_ARRAY,
                    MessageTypeMode.READONLY,
                    includeNullForMessages);
            builder.addRequires(forSoyType.getGoogRequires());
            parameters.put(
                parameter.getName(),
                forSoyType.typeExprForRecordMember(/* isOptional= */ !parameter.isRequired()));
          }
          JsType forReturnType = templateReturnType(templateType.getContentKind(), kind);
          builder.addRequires(forReturnType.getGoogRequires());
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
      case CSS_TYPE:
      case CSS_MODULE:
      case PROTO_TYPE:
      case PROTO_ENUM_TYPE:
      case PROTO_EXTENSION:
      case PROTO_MODULE:
      case TEMPLATE_TYPE:
      case TEMPLATE_MODULE:
      case FUNCTION:
    }
    throw new AssertionError("unhandled soytype: " + soyType);
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
          builder.addType("!" + type);
          builder.addRequire(GoogRequire.createTypeRequire(type));
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
     * and {@code false} otherwise. Returns {@link Optional#absent()} if there is no validation to
     * be done.
     */
    Optional<Expression> maybeCheck(Expression value, Generator codeGenerator);
  }

  /** Builds and returns a TypePredicate comparing a value's 'typeof' against the given 'type' */
  private static TypePredicate typeofTypePredicate(String type) {
    return (value, codeGenerator) ->
        Optional.of(value.typeOf().tripleEquals(Expressions.stringLiteral(type)));
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

  /**
   * Returns a type expression for a record member. In some cases this requires additional parens.
   */
  public String typeExprForRecordMember(boolean isOptional) {
    if (typeExpressions.size() > 1 || isOptional) {
      // needs parens
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

  public ImmutableSet<GoogRequire> getGoogRequires() {
    return extraRequires;
  }

  /**
   * Returns a code chunk that generates a 'test' for whether or not the given value has this type,
   * or {@link Optional#absent()} if no test is necessary.
   */
  Optional<Expression> getTypeAssertion(Expression value, Generator codeGenerator) {
    return predicate.maybeCheck(value, codeGenerator);
  }

  /**
   * Returns a Soy assertion expression that asserts that the given value has this type, or {@link
   * Optional#absent()} if no assertion is necessary.
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
    builder.addType("!" + type);
    builder.addRequire(GoogRequire.createTypeRequire(type));
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
        builder.addType("!goog.html.SafeStyle");
        builder.addRequire(GoogRequire.createTypeRequire("goog.html.SafeStyle"));
        break;
      case HTML_ELEMENT:
      case HTML:
        builder.addType("!goog.html.SafeHtml");
        builder.addRequire(GoogRequire.createTypeRequire("goog.html.SafeHtml"));
        break;
      case JS:
        builder.addType("!goog.html.SafeScript");
        builder.addRequire(GoogRequire.createTypeRequire("goog.html.SafeScript"));
        break;
      case ATTRIBUTES:
      case TEXT:
        // nothing extra
        break;
      case TRUSTED_RESOURCE_URI:
        builder.addType("!goog.html.TrustedResourceUrl");
        builder.addRequire(GoogRequire.createTypeRequire("goog.html.TrustedResourceUrl"));
        break;
      case URI:
        builder.addType("!goog.html.TrustedResourceUrl");
        builder.addRequire(GoogRequire.createTypeRequire("goog.html.TrustedResourceUrl"));
        builder.addType("!goog.html.SafeUrl");
        builder.addRequire(GoogRequire.createTypeRequire("goog.html.SafeUrl"));
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
