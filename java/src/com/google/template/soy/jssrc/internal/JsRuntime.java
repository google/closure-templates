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

import static com.google.template.soy.jssrc.dsl.Expression.dottedIdNoRequire;
import static com.google.template.soy.jssrc.dsl.Expression.id;

import com.google.common.collect.ImmutableMap;
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
import com.google.template.soy.types.SoyProtoType;

/**
 * Constants for commonly used js runtime functions and objects.
 *
 * <p>Unlike {@code JsExprUtils}, this is only intended for use by the compiler itself and deals
 * exclusively with the {@link CodeChunk} api.
 */
public final class JsRuntime {
  private static final GoogRequire GOOG_ARRAY = GoogRequire.create("goog.array");
  private static final GoogRequire GOOG_ASSERTS = GoogRequire.create("goog.asserts");
  private static final GoogRequire GOOG_STRING = GoogRequire.create("goog.string");

  private static final GoogRequire SOY = GoogRequire.create("soy");
  private static final GoogRequire SOY_MAP = GoogRequire.create("soy.map");
  private static final GoogRequire SOY_NEWMAPS = GoogRequire.create("soy.newmaps");
  private static final GoogRequire SOY_ASSERTS = GoogRequire.create("soy.asserts");
  public static final GoogRequire SOY_VELOG = GoogRequire.create("soy.velog");
  public static final GoogRequire GOOG_SOY_ALIAS =
      GoogRequire.createWithAlias("goog.soy", "$googSoy");

  private static final GoogRequire SOY_TEMPLATES = GoogRequire.create("soy.templates");

  public static final GoogRequire GOOG_SOY = GoogRequire.create("goog.soy");

  private static final GoogRequire XID_REQUIRE = GoogRequire.create("xid");

  private JsRuntime() {}

  public static final Expression GOOG_ARRAY_MAP = GOOG_ARRAY.reference().dotAccess("map");

  public static final Expression GOOG_ASSERTS_ASSERT = GOOG_ASSERTS.reference().dotAccess("assert");

  public static final Expression GOOG_DEBUG = dottedIdNoRequire("goog.DEBUG");

  public static final Expression GOOG_GET_CSS_NAME = dottedIdNoRequire("goog.getCssName");

  public static final Expression GOOG_GET_MSG = dottedIdNoRequire("goog.getMsg");

  public static final Expression ARRAY_IS_ARRAY = dottedIdNoRequire("Array.isArray");

  public static final Expression GOOG_IS_FUNCTION = SOY.dotAccess("$$isFunction");

  public static final Expression SOY_EQUALS = SOY.dotAccess("$$equals");

  public static final Expression SOY_FILTER_AND_MAP = SOY.dotAccess("$$filterAndMap");

  public static final Expression GOOG_IS_OBJECT = dottedIdNoRequire("goog.isObject");

  public static final Expression GOOG_REQUIRE = dottedIdNoRequire("goog.require");

  public static final Expression GOOG_SOY_DATA_SANITIZED_CONTENT =
      GoogRequire.create("goog.soy.data.SanitizedContent").reference();

  public static final Expression GOOG_HTML_SAFE_HTML =
      GoogRequire.create("goog.html.SafeHtml").reference();

  public static final Expression GOOG_STRING_UNESCAPE_ENTITIES =
      GOOG_STRING.dotAccess("unescapeEntities");

  public static final Expression GOOG_I18N_MESSAGE_FORMAT =
      GoogRequire.create("goog.i18n.MessageFormat").reference();

  public static final Expression SOY_ASSERTS_ASSERT_TYPE = SOY_ASSERTS.dotAccess("assertType");

  public static final Expression SOY_ASSIGN_DEFAULTS = SOY.dotAccess("$$assignDefaults");

  public static final Expression SOY_CHECK_NOT_NULL = SOY.dotAccess("$$checkNotNull");

  public static final Expression SERIALIZE_KEY = SOY.dotAccess("$$serializeKey");

  public static final Expression SOY_COERCE_TO_BOOLEAN = SOY.dotAccess("$$coerceToBoolean");

  public static final Expression SOY_ESCAPE_HTML = SOY.dotAccess("$$escapeHtml");

  public static final Expression SOY_GET_DELEGATE_FN = SOY.dotAccess("$$getDelegateFn");

  public static final Expression SOY_REGISTER_DELEGATE_FN = SOY.dotAccess("$$registerDelegateFn");

  public static final Expression SOY_GET_DELTEMPLATE_ID = SOY.dotAccess("$$getDelTemplateId");

  public static final Expression SOY_IS_LOCALE_RTL = SOY.dotAccess("$$IS_LOCALE_RTL");

  public static final Expression SOY_DEBUG_SOY_TEMPLATE_INFO =
      SOY.dotAccess("$$debugSoyTemplateInfo");

  public static final Expression SOY_MAP_POPULATE = SOY_MAP.dotAccess("$$populateMap");

  public static final Expression SOY_MAP_IS_SOY_MAP = SOY_MAP.dotAccess("$$isSoyMap");

  public static final Expression SOY_NEWMAPS_TRANSFORM_VALUES =
      SOY_NEWMAPS.dotAccess("$$transformValues");

  public static final Expression SOY_VISUAL_ELEMENT = SOY_VELOG.dotAccess("$$VisualElement");
  public static final Expression SOY_VISUAL_ELEMENT_DATA =
      SOY_VELOG.dotAccess("$$VisualElementData");

  public static final Expression WINDOW_CONSOLE_LOG = dottedIdNoRequire("window.console.log");

  public static final Expression XID = XID_REQUIRE.reference();

  /** A constant for the template parameter {@code opt_data}. */
  public static final Expression OPT_DATA = id("opt_data");

  /** A constant for the template parameter {@code opt_ijData}. */
  public static final Expression OPT_IJ_DATA = id("opt_ijData");

  public static final Expression EXPORTS = id("exports");

  public static final Expression MARK_TEMPLATE =
      SOY_TEMPLATES.googModuleGet().dotAccess("$$markTemplate");
  public static final Expression ASSERT_TEMPLATE =
      SOY_TEMPLATES.googModuleGet().dotAccess("$$assertTemplate");
  public static final Expression BIND_TEMPLATE_PARAMS =
      SOY_TEMPLATES.googModuleGet().dotAccess("$$bindTemplateParams");
  public static final Expression BIND_TEMPLATE_PARAMS_FOR_IDOM =
      SOY_TEMPLATES.googModuleGet().dotAccess("$$bindTemplateParamsForIdom");

  /** The JavaScript method to pack a sanitized object into a safe proto. */
  public static final ImmutableMap<String, Expression> JS_TO_PROTO_PACK_FN_BASE =
      ImmutableMap.<String, Expression>builder()
          .put(
              SafeScriptProto.getDescriptor().getFullName(),
              GoogRequire.create("soydata.packSanitizedJsToProtoSoyRuntimeOnly").reference())
          .put(
              SafeUrlProto.getDescriptor().getFullName(),
              GoogRequire.create("soydata.packSanitizedUriToProtoSoyRuntimeOnly").reference())
          .put(
              SafeStyleProto.getDescriptor().getFullName(),
              GoogRequire.create("soydata.packSanitizedCssToSafeStyleProtoSoyRuntimeOnly")
                  .reference())
          .put(
              SafeStyleSheetProto.getDescriptor().getFullName(),
              GoogRequire.create("soydata.packSanitizedCssToSafeStyleSheetProtoSoyRuntimeOnly")
                  .reference())
          .put(
              TrustedResourceUrlProto.getDescriptor().getFullName(),
              GoogRequire.create("soydata.packSanitizedTrustedResourceUriToProtoSoyRuntimeOnly")
                  .reference())
          .build();

  public static final ImmutableMap<String, Expression> JS_TO_PROTO_PACK_FN =
      ImmutableMap.<String, Expression>builder()
          .put(
              SafeHtmlProto.getDescriptor().getFullName(),
              GoogRequire.create("soydata.packSanitizedHtmlToProtoSoyRuntimeOnly").reference())
          .putAll(JS_TO_PROTO_PACK_FN_BASE)
          .build();

  /** Returns the field containing the extension object for the given field descriptor. */
  public static Expression extensionField(FieldDescriptor desc) {
    String jsExtensionImport = ProtoUtils.getJsExtensionImport(desc);
    String jsExtensionName = ProtoUtils.getJsExtensionName(desc);
    return symbolWithNamespace(jsExtensionImport, jsExtensionName);
  }

  /** Returns a function that can 'unpack' safe proto types into sanitized content types.. */
  public static Expression protoToSanitizedContentConverterFunction(Descriptor messageType) {
    return GoogRequire.create(NodeContentKinds.toJsUnpackFunction(messageType)).reference();
  }

  /**
   * Returns an 'ordainer' function that can be used wrap a {@code string} in a {@code
   * SanitizedContent} object with no escaping.
   */
  public static Expression sanitizedContentOrdainerFunction(SanitizedContentKind kind) {
    return symbolWithNamespace(
        NodeContentKinds.getJsImportForOrdainersFunctions(kind),
        NodeContentKinds.toJsSanitizedContentOrdainer(kind));
  }

  /**
   * Returns an 'ordainer' function that can be used wrap a {@code string} in a {@code
   * SanitizedContent} object with no escaping.
   */
  public static Expression sanitizedContentOrdainerFunctionForInternalBlocks(
      SanitizedContentKind kind) {
    return symbolWithNamespace(
        NodeContentKinds.getJsImportForOrdainersFunctions(kind),
        NodeContentKinds.toJsSanitizedContentOrdainerForInternalBlocks(kind));
  }

  /** Returns the constructor for the proto. */
  public static Expression protoConstructor(SoyProtoType type) {
    return GoogRequire.create(type.getNameForBackend(SoyBackendKind.JS_SRC)).reference();
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
  private static Expression symbolWithNamespace(String requireSymbol, String fullyQualifiedSymbol) {
    GoogRequire require = GoogRequire.create(requireSymbol);
    if (fullyQualifiedSymbol.equals(require.symbol())) {
      return require.reference();
    }
    String ident = fullyQualifiedSymbol.substring(require.symbol().length() + 1);
    return require.dotAccess(ident);
  }
}
