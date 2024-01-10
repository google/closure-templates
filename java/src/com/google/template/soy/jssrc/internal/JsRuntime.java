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
package com.google.template.soy.jssrc.internal;

import static com.google.template.soy.jssrc.dsl.Expressions.dottedIdNoRequire;
import static com.google.template.soy.jssrc.dsl.Expressions.id;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.html.types.SafeHtmlProto;
import com.google.common.html.types.SafeScriptProto;
import com.google.common.html.types.SafeStyleProto;
import com.google.common.html.types.SafeStyleSheetProto;
import com.google.common.html.types.SafeUrlProto;
import com.google.common.html.types.TrustedResourceUrlProto;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.template.soy.base.SoyBackendKind;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.data.internalutils.NodeContentKinds;
import com.google.template.soy.internal.proto.ProtoUtils;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.jssrc.dsl.GoogRequire;
import com.google.template.soy.types.SoyProtoEnumType;
import com.google.template.soy.types.SoyProtoType;

/**
 * Constants for commonly used js runtime functions and objects.
 *
 * <p>Unlike {@code JsExprUtils}, this is only intended for use by the compiler itself and deals
 * exclusively with the {@link CodeChunk} api.
 */
public final class JsRuntime {
  private static final GoogRequire GOOG_ARRAY =
      GoogRequire.createWithAlias("goog.array", "googArray");
  private static final GoogRequire GOOG_ASSERTS =
      GoogRequire.createWithAlias("goog.asserts", "googAsserts");
  private static final GoogRequire GOOG_STRING =
      GoogRequire.createWithAlias("goog.string", "googString");

  public static final GoogRequire SOY = GoogRequire.createWithAlias("soy", "soy");
  public static final GoogRequire SOY_MAP = GoogRequire.createWithAlias("soy.map", "soyMap");
  private static final GoogRequire SOY_NEWMAPS =
      GoogRequire.createWithAlias("soy.newmaps", "soyNewmaps");
  public static final GoogRequire SOY_VELOG = GoogRequire.createWithAlias("soy.velog", "soyVelog");
  public static final GoogRequire GOOG_SOY = GoogRequire.createWithAlias("goog.soy", "$googSoy");

  private static final GoogRequire SOY_TEMPLATES =
      GoogRequire.createWithAlias("soy.templates", "soyTemplates");

  public static final Expression SOY_EMPTY_OBJECT = SOY.dotAccess("$$EMPTY_OBJECT");
  public static final Expression SOY_INTERCEPT_SOY_TEMPLATES =
      SOY.dotAccess("INTERCEPT_SOY_TEMPLATES");
  public static final Expression SOY_STUBS_MAP = SOY.dotAccess("$$stubsMap");

  private static final GoogRequire XID_REQUIRE = GoogRequire.createWithAlias("xid", "xid");

  private JsRuntime() {}

  public static final Expression GOOG_ARRAY_MAP = GOOG_ARRAY.reference().dotAccess("map");

  public static final Expression GOOG_ASSERTS_ASSERT = GOOG_ASSERTS.reference().dotAccess("assert");

  public static final Expression GOOG_DEBUG = dottedIdNoRequire("goog.DEBUG");

  public static final Expression GOOG_GET_CSS_NAME = dottedIdNoRequire("goog.getCssName");

  public static final Expression GOOG_GET_MSG = dottedIdNoRequire("goog.getMsg");

  public static final Expression ARRAY_IS_ARRAY = dottedIdNoRequire("Array.isArray");

  public static final Expression GOOG_IS_FUNCTION = SOY.dotAccess("$$isFunction");

  public static final Expression SOY_EQUALS = SOY.dotAccess("$$equals");

  public static final Expression SHOULD_STUB =
      GOOG_SOY.dotAccess("shouldStub").and(GOOG_SOY.dotAccess("shouldStubAtRuntime").call(), null);

  public static final Expression SOY_MAKE_ARRAY = SOY.dotAccess("$$makeArray");

  public static final Expression SOY_AS_READONLY = SOY.dotAccess("$$asReadonlyArray");

  public static final Expression SOY_FILTER_AND_MAP = SOY.dotAccess("$$filterAndMap");

  public static final Expression GOOG_IS_OBJECT = dottedIdNoRequire("goog.isObject");
  public static final GoogRequire JSPB_MESSAGE =
      GoogRequire.createWithAlias("jspb.Message", "$JspbMessage");
  public static final GoogRequire SAFEVALUES =
      GoogRequire.createWithAlias("safevalues", "safevalues");
  public static final Expression GOOG_SOY_DATA_SANITIZED_CONTENT =
      GoogRequire.createWithAlias("goog.soy.data.SanitizedContent", "$SanitizedContent")
          .reference();

  public static final Expression SAFEVALUES_SAFEHTML = SAFEVALUES.dotAccess("SafeHtml");

  public static final GoogRequire GOOG_SOY_DATA_HTML =
      GoogRequire.createTypeRequireWithAlias("goog.soy.data.SanitizedHtml", "$SanitizedHtml");
  public static final GoogRequire GOOG_HTML_SAFE_ATTRIBUTE_REQUIRE =
      GoogRequire.createWithAlias(
          "goog.soy.data.SanitizedHtmlAttribute", "$SanitizedHtmlAttribute");
  public static final Expression GOOG_HTML_SAFE_ATTRIBUTE =
      GOOG_HTML_SAFE_ATTRIBUTE_REQUIRE.reference();

  public static final Expression GOOG_STRING_UNESCAPE_ENTITIES =
      GOOG_STRING.dotAccess("unescapeEntities");

  public static final Expression GOOG_I18N_MESSAGE_FORMAT =
      GoogRequire.createWithAlias("goog.i18n.MessageFormat", "$MessageFormat").reference();

  public static final Expression SOY_ASSERT_PARAM_TYPE = SOY.dotAccess("assertParamType");

  public static final Expression SOY_ASSIGN_DEFAULTS = SOY.dotAccess("$$assignDefaults");

  public static final Expression SOY_CHECK_NOT_NULL = SOY.dotAccess("$$checkNotNull");

  public static final Expression SERIALIZE_KEY = SOY.dotAccess("$$serializeKey");

  public static final Expression SOY_COERCE_TO_BOOLEAN = SOY.dotAccess("$$coerceToBoolean");

  public static final Expression SOY_IS_TRUTHY_NON_EMPTY = SOY.dotAccess("$$isTruthyNonEmpty");

  public static final Expression SOY_HAS_CONTENT = SOY.dotAccess("$$hasContent");

  public static final Expression SOY_ESCAPE_HTML = SOY.dotAccess("$$escapeHtml");

  public static final Expression SOY_GET_DELEGATE_FN = SOY.dotAccess("$$getDelegateFn");

  public static final Expression SOY_MAKE_EMPTY_TEMPLATE_FN =
      SOY.dotAccess("$$makeEmptyTemplateFn");

  public static final Expression SOY_REGISTER_DELEGATE_FN = SOY.dotAccess("$$registerDelegateFn");

  public static final Expression SOY_ALIAS_DELEGATE_ID = SOY.dotAccess("$$aliasDelegateId");

  public static final Expression SOY_GET_DELTEMPLATE_ID = SOY.dotAccess("$$getDelTemplateId");

  public static final Expression SOY_IS_LOCALE_RTL = SOY.dotAccess("$$IS_LOCALE_RTL");
  public static final Expression SOY_CREATE_CONST = SOY.dotAccess("$$createConst");
  public static final Expression SOY_GET_CONST = SOY.dotAccess("$$getConst");

  public static final Expression SOY_DEBUG_SOY_TEMPLATE_INFO =
      SOY.dotAccess("$$getDebugSoyTemplateInfo");

  public static final Expression SOY_ARE_YOU_AN_INTERNAL_CALLER =
      SOY.dotAccess("$$areYouAnInternalCaller");
  public static final Expression SOY_INTERNAL_CALL_MARKER =
      SOY.dotAccess("$$internalCallMarkerDoNotUse");

  public static final Expression SOY_MAP_IS_SOY_MAP = SOY_MAP.dotAccess("$$isSoyMap");

  public static final Expression SOY_NEWMAPS_TRANSFORM_VALUES =
      SOY_NEWMAPS.dotAccess("$$transformValues");
  public static final Expression SOY_NEWMAPS_NULL_SAFE_TRANSFORM_VALUES =
      SOY_NEWMAPS.dotAccess("$$nullSafeTransformValues");
  public static final Expression SOY_NEWMAPS_NULL_SAFE_ARRAY_MAP =
      SOY_NEWMAPS.dotAccess("$$nullSafeArrayMap");

  public static final Expression SOY_VISUAL_ELEMENT = SOY_VELOG.dotAccess("$$VisualElement");
  public static final Expression SOY_VISUAL_ELEMENT_DATA =
      SOY_VELOG.dotAccess("$$VisualElementData");

  public static final Expression WINDOW_CONSOLE_LOG = dottedIdNoRequire("window.console.log");

  public static final Expression XID = XID_REQUIRE.reference();

  public static final GoogRequire ELEMENT_LIB_IDOM =
      GoogRequire.createWithAlias(
          "google3.javascript.template.soy.element_lib_idom", "element_lib_idom");

  /**
   * A constant for the template parameter {@code opt_data}.
   *
   * <p>TODO(b/177856412): rename to something that doesn't begin with {@code opt_}
   */
  public static final Expression OPT_DATA = id(StandardNames.OPT_DATA);

  public static final Expression OPT_VARIANT = id(StandardNames.OPT_VARIANT);

  /** A constant for the template parameter {@code $ijData}. */
  public static final Expression IJ_DATA = id(StandardNames.DOLLAR_IJDATA);

  public static final Expression EXPORTS = id("exports");

  public static final Expression MARK_TEMPLATE = SOY_TEMPLATES.dotAccess("$$markTemplate");
  public static final Expression BIND_TEMPLATE_PARAMS =
      SOY_TEMPLATES.dotAccess("$$bindTemplateParams");
  public static final Expression BIND_TEMPLATE_PARAMS_FOR_IDOM =
      SOY_TEMPLATES.dotAccess("$$bindTemplateParamsForIdom");

  private static final GoogRequire SOY_CONVERTERS =
      GoogRequire.createWithAlias("soy.converters", "soyConverters");

  /** The JavaScript method to pack a sanitized object into a safe proto. */
  public static final ImmutableMap<String, Expression> JS_TO_PROTO_PACK_FN_BASE =
      ImmutableMap.<String, Expression>builder()
          .put(
              SafeScriptProto.getDescriptor().getFullName(),
              SOY_CONVERTERS.dotAccess("packSanitizedJsToProtoSoyRuntimeOnly"))
          .put(
              SafeUrlProto.getDescriptor().getFullName(),
              SOY_CONVERTERS.dotAccess("packSanitizedUriToProtoSoyRuntimeOnly"))
          .put(
              SafeStyleProto.getDescriptor().getFullName(),
              SOY_CONVERTERS.dotAccess("packSanitizedCssToSafeStyleProtoSoyRuntimeOnly"))
          .put(
              SafeStyleSheetProto.getDescriptor().getFullName(),
              SOY_CONVERTERS.dotAccess("packSanitizedCssToSafeStyleSheetProtoSoyRuntimeOnly"))
          .put(
              TrustedResourceUrlProto.getDescriptor().getFullName(),
              SOY_CONVERTERS.dotAccess("packSanitizedTrustedResourceUriToProtoSoyRuntimeOnly"))
          .buildOrThrow();

  public static final ImmutableMap<String, Expression> JS_TO_PROTO_PACK_FN =
      ImmutableMap.<String, Expression>builder()
          .put(
              SafeHtmlProto.getDescriptor().getFullName(),
              SOY_CONVERTERS.dotAccess("packSanitizedHtmlToProtoSoyRuntimeOnly"))
          .putAll(JS_TO_PROTO_PACK_FN_BASE)
          .buildOrThrow();

  private static GoogRequire createProtoRequire(String protoImport) {
    return GoogRequire.createWithAlias(protoImport, '$' + protoImport.replace('.', '$'));
  }

  /** Create and reference toggle for given path, name. */
  public static Expression getToggleRef(String path, String name) {
    // Translate './path/to/my.toggles' to 'google3.path.to.my$2etoggles' for ts_toggle_lib
    int extensionIndex = path.lastIndexOf(".toggles");
    if (extensionIndex != -1) {
      path = path.substring(0, extensionIndex);
    }
    String togglePathSymbol = "google3." + path.replace('/', '.') + "$2etoggles";
    // Map toggle path to unique string for toggle references
    String uniqueTogglePathSymbol = togglePathSymbol.replace('.', '_');
    GoogRequire ref = GoogRequire.createWithAlias(togglePathSymbol, uniqueTogglePathSymbol);
    // Prepend 'TOGGLE_' to toggle name for ts_toggle_lib naming requirement
    return ref.reference().dotAccess("TOGGLE_" + name);
  }

  /** Returns the field containing the extension object for the given field descriptor. */
  public static Expression extensionField(FieldDescriptor desc) {
    String jsExtensionImport = ProtoUtils.getJsExtensionImport(desc);
    String jsExtensionName = ProtoUtils.getJsExtensionName(desc);
    return symbolWithNamespace(createProtoRequire(jsExtensionImport), jsExtensionName);
  }

  /** Returns a function that can 'unpack' safe proto types into sanitized content types.. */
  public static Expression protoToSanitizedContentConverterFunction(Descriptor messageType) {
    return SOY_CONVERTERS.dotAccess(NodeContentKinds.toJsUnpackFunction(messageType));
  }

  /**
   * Returns a function that ensure that proto bytes fields are consistently converted oot base64.
   */
  public static Expression protoByteStringToBase64ConverterFunction() {
    return SOY_CONVERTERS.dotAccess("unpackByteStringToBase64String");
  }

  /** Returns a function that ensures that the values of bytes-values maps are coerced. */
  public static Expression protoBytesPackToByteStringFunction() {
    return SOY_CONVERTERS.dotAccess("packBase64StringToByteString");
  }

  /**
   * Returns an 'ordainer' function that can be used wrap a {@code string} in a {@code
   * SanitizedContent} object with no escaping.
   */
  public static Expression sanitizedContentOrdainerFunction(SanitizedContentKind kind) {
    return symbolWithNamespace(SOY, NodeContentKinds.toJsSanitizedContentOrdainer(kind));
  }

  /**
   * Returns an 'ordainer' function that can be used wrap a {@code string} in a {@code
   * SanitizedContent} object with no escaping.
   */
  public static Expression sanitizedContentOrdainerFunctionForInternalBlocks(
      SanitizedContentKind kind) {
    return symbolWithNamespace(
        SOY, NodeContentKinds.toJsSanitizedContentOrdainerForInternalBlocks(kind));
  }

  /** Returns the constructor for the proto. */
  public static Expression protoConstructor(SoyProtoType type) {
    return createProtoRequire(type.getJsName(ProtoUtils.MutabilityMode.MUTABLE)).reference();
  }

  /** Returns the constructor for the proto. */
  public static GoogRequire readonlyProtoType(SoyProtoType type) {
    return createProtoRequire(type.getJsName(ProtoUtils.MutabilityMode.READONLY)).toRequireType();
  }

  public static GoogRequire protoEnum(SoyProtoEnumType enumType) {
    return createProtoRequire(enumType.getNameForBackend(SoyBackendKind.JS_SRC));
  }

  /** Returns an expression that constructs an empty proto. */
  public static Expression emptyProto(SoyProtoType type) {
    return castAsReadonlyProto(SOY.dotAccess("$$emptyProto").call(protoConstructor(type)), type);
  }

  static Expression castAsReadonlyProto(Expression expr, SoyProtoType type) {
    var require = readonlyProtoType(type);
    String readonlyName = require.alias();
    return expr.castAs("!" + readonlyName, ImmutableSet.of(require));
  }

  /**
   * Returns the js type for the sanitized content object corresponding to the given ContentKind.
   */
  public static Expression sanitizedContentType(SanitizedContentKind kind) {
    return GoogRequire.create(NodeContentKinds.toJsSanitizedContentCtorName(kind)).reference();
  }

  /**
   * Returns a code chunk that accesses the given symbol.
   *
   * @param requireSymbol The symbol to {@code goog.require}
   * @param fullyQualifiedSymbol The symbol we want to access.
   */
  private static Expression symbolWithNamespace(GoogRequire require, String fullyQualifiedSymbol) {
    if (fullyQualifiedSymbol.equals(require.symbol())) {
      return require.reference();
    }
    String suffix = fullyQualifiedSymbol.substring(require.symbol().length() + 1);
    Expression e = require.reference();
    for (String ident : Splitter.on('.').splitToList(suffix)) {
      e = e.dotAccess(ident);
    }
    return e;
  }
}
