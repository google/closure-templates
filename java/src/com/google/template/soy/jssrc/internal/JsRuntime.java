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

  private static final GoogRequire XID_REQUIRE = GoogRequire.create("xid");

  private JsRuntime() {}

  public static final Expression GOOG_ARRAY_MAP = GOOG_ARRAY.reference().dotAccess("map");

  public static final Expression GOOG_ASSERTS_ASSERT = GOOG_ASSERTS.reference().dotAccess("assert");

  public static final Expression GOOG_DEBUG = dottedIdNoRequire("goog.DEBUG");

  public static final Expression GOOG_GET_CSS_NAME = dottedIdNoRequire("goog.getCssName");

  public static final Expression GOOG_GET_MSG = dottedIdNoRequire("goog.getMsg");

  public static final Expression GOOG_IS_ARRAY = dottedIdNoRequire("goog.isArray");

  public static final Expression GOOG_IS_BOOLEAN = dottedIdNoRequire("goog.isBoolean");

  public static final Expression GOOG_IS_FUNCTION = dottedIdNoRequire("goog.isFunction");

  public static final Expression SOY_EQUALS = SOY.dotAccess("$$equals");

  public static final Expression GOOG_IS_NUMBER = dottedIdNoRequire("goog.isNumber");

  public static final Expression GOOG_IS_OBJECT = dottedIdNoRequire("goog.isObject");

  public static final Expression GOOG_IS_STRING = dottedIdNoRequire("goog.isString");

  public static final Expression GOOG_REQUIRE = dottedIdNoRequire("goog.require");

  public static final Expression GOOG_SOY_DATA_SANITIZED_CONTENT =
      GoogRequire.create("goog.soy.data.SanitizedContent").reference();

  public static final Expression GOOG_SOY_DATA_UNSANITIZED_TEXT =
      GoogRequire.create("goog.soy.data.UnsanitizedText").reference();

  public static final Expression GOOG_STRING_UNESCAPE_ENTITIES =
      GOOG_STRING.dotAccess("unescapeEntities");

  public static final Expression GOOG_I18N_MESSAGE_FORMAT =
      GoogRequire.create("goog.i18n.MessageFormat").reference();

  public static final Expression SOY_ASSERTS_ASSERT_TYPE = SOY_ASSERTS.dotAccess("assertType");

  public static final Expression SOY_ASSIGN_DEFAULTS = SOY.dotAccess("$$assignDefaults");

  public static final Expression SOY_CHECK_NOT_NULL = SOY.dotAccess("$$checkNotNull");

  public static final Expression SOY_ESCAPE_HTML = SOY.dotAccess("$$escapeHtml");

  public static final Expression SOY_GET_DELEGATE_FN = SOY.dotAccess("$$getDelegateFn");

  public static final Expression SOY_REGISTER_DELEGATE_FN = SOY.dotAccess("$$registerDelegateFn");

  public static final Expression SOY_GET_DELTEMPLATE_ID = SOY.dotAccess("$$getDelTemplateId");

  public static final Expression SOY_MAP_POPULATE = SOY_MAP.dotAccess("$$populateMap");

  public static final Expression SOY_MAP_MAYBE_COERCE_KEY_TO_STRING =
      SOY_MAP.dotAccess("$$maybeCoerceKeyToString");

  public static final Expression SOY_MAP_IS_SOY_MAP = SOY_MAP.dotAccess("$$isSoyMap");

  public static final Expression SOY_NEWMAPS_TRANSFORM_VALUES =
      SOY_NEWMAPS.dotAccess("$$transformValues");

  public static final Expression WINDOW_CONSOLE_LOG = dottedIdNoRequire("window.console.log");

  public static final Expression XID = XID_REQUIRE.reference();

  /** A constant for the template parameter {@code opt_data}. */
  public static final Expression OPT_DATA = id("opt_data");

  /** A constant for the template parameter {@code opt_ijData}. */
  public static final Expression OPT_IJ_DATA = id("opt_ijData");

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

  /** Returns a function that can 'unpack' safe proto types into sanitized content types.. */
  public static Expression sanitizedContentToProtoConverterFunction(Descriptor messageType) {
    return GoogRequire.create(NodeContentKinds.toJsPackFunction(messageType)).reference();
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
