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

import static com.google.template.soy.jssrc.dsl.CodeChunk.dottedIdNoRequire;
import static com.google.template.soy.jssrc.dsl.CodeChunk.id;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.template.soy.base.SoyBackendKind;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.data.internalutils.NodeContentKinds;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.dsl.GoogRequire;
import com.google.template.soy.types.proto.ProtoUtils;
import com.google.template.soy.types.proto.SoyProtoType;

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
  private static final GoogRequire SOY_ASSERTS = GoogRequire.create("soy.asserts");

  private static final GoogRequire XID_REQUIRE = GoogRequire.create("xid");

  private JsRuntime() {}

  public static final CodeChunk.WithValue GOOG_ARRAY_MAP = GOOG_ARRAY.reference().dotAccess("map");

  public static final CodeChunk.WithValue GOOG_ASSERTS_ASSERT =
      GOOG_ASSERTS.reference().dotAccess("assert");

  public static final CodeChunk.WithValue GOOG_DEBUG = dottedIdNoRequire("goog.DEBUG");

  public static final CodeChunk.WithValue GOOG_GET_CSS_NAME = dottedIdNoRequire("goog.getCssName");

  public static final CodeChunk.WithValue GOOG_GET_MSG = dottedIdNoRequire("goog.getMsg");

  public static final CodeChunk.WithValue GOOG_IS_ARRAY = dottedIdNoRequire("goog.isArray");

  public static final CodeChunk.WithValue GOOG_IS_BOOLEAN = dottedIdNoRequire("goog.isBoolean");

  public static final CodeChunk.WithValue GOOG_IS_FUNCTION = dottedIdNoRequire("goog.isFunction");

  public static final CodeChunk.WithValue GOOG_IS_NUMBER = dottedIdNoRequire("goog.isNumber");

  public static final CodeChunk.WithValue GOOG_IS_OBJECT = dottedIdNoRequire("goog.isObject");

  public static final CodeChunk.WithValue GOOG_IS_STRING = dottedIdNoRequire("goog.isString");

  public static final CodeChunk.WithValue GOOG_REQUIRE = dottedIdNoRequire("goog.require");

  public static final CodeChunk.WithValue GOOG_SOY_DATA_SANITIZED_CONTENT =
      GoogRequire.create("goog.soy.data.SanitizedContent").reference();

  public static final CodeChunk.WithValue GOOG_STRING_UNESCAPE_ENTITIES =
      GOOG_STRING.dotAccess("unescapeEntities");

  public static final CodeChunk.WithValue GOOG_I18N_MESSAGE_FORMAT =
      GoogRequire.create("goog.i18n.MessageFormat").reference();

  public static final CodeChunk.WithValue SOY_ASSERTS_ASSERT_TYPE =
      SOY_ASSERTS.dotAccess("assertType");

  public static final CodeChunk.WithValue SOY_ASSIGN_DEFAULTS = SOY.dotAccess("$$assignDefaults");

  public static final CodeChunk.WithValue SOY_CHECK_MAP_KEY = SOY.dotAccess("$$checkMapKey");

  public static final CodeChunk.WithValue SOY_CHECK_NOT_NULL = SOY.dotAccess("$$checkNotNull");

  public static final CodeChunk.WithValue SOY_ESCAPE_HTML = SOY.dotAccess("$$escapeHtml");

  public static final CodeChunk.WithValue SOY_GET_DELEGATE_FN = SOY.dotAccess("$$getDelegateFn");

  public static final CodeChunk.WithValue SOY_REGISTER_DELEGATE_FN =
      SOY.dotAccess("$$registerDelegateFn");

  public static final CodeChunk.WithValue SOY_GET_DELTEMPLATE_ID =
      SOY.dotAccess("$$getDelTemplateId");

  public static final CodeChunk.WithValue WINDOW_CONSOLE_LOG =
      dottedIdNoRequire("window.console.log");

  public static final CodeChunk.WithValue XID = XID_REQUIRE.reference();

  /** A constant for the template parameter {@code opt_data}. */
  public static final CodeChunk.WithValue OPT_DATA = id("opt_data");

  /** A constant for the template parameter {@code opt_ijData}. */
  public static final CodeChunk.WithValue OPT_IJ_DATA = id("opt_ijData");

  /** Returns the field containing the extension object for the given field descriptor. */
  public static CodeChunk.WithValue extensionField(FieldDescriptor desc) {
    String jsExtensionImport = ProtoUtils.getJsExtensionImport(desc);
    String jsExtensionName = ProtoUtils.getJsExtensionName(desc);
    return symbolWithNamespace(jsExtensionImport, jsExtensionName);
  }

  /** Returns a function that can 'unpack' safe proto types into sanitized content types.. */
  public static CodeChunk.WithValue protoToSanitizedContentConverterFunction(
      Descriptor messageType) {
    return GoogRequire.create(NodeContentKinds.toJsUnpackFunction(messageType)).reference();
  }

  /** Returns a function that can 'unpack' safe proto types into sanitized content types.. */
  public static CodeChunk.WithValue sanitizedContentToProtoConverterFunction(
      Descriptor messageType) {
    return GoogRequire.create(NodeContentKinds.toJsPackFunction(messageType)).reference();
  }

  /**
   * Returns an 'ordainer' function that can be used wrap a {@code string} in a {@code
   * SanitizedContent} object with no escaping.
   */
  public static CodeChunk.WithValue sanitizedContentOrdainerFunction(SanitizedContentKind kind) {
    return symbolWithNamespace(
        NodeContentKinds.getJsImportForOrdainersFunctions(kind),
        NodeContentKinds.toJsSanitizedContentOrdainer(kind));
  }

  /**
   * Returns an 'ordainer' function that can be used wrap a {@code string} in a {@code
   * SanitizedContent} object with no escaping.
   */
  public static CodeChunk.WithValue sanitizedContentOrdainerFunctionForInternalBlocks(
      SanitizedContentKind kind) {
    return symbolWithNamespace(
        NodeContentKinds.getJsImportForOrdainersFunctions(kind),
        NodeContentKinds.toJsSanitizedContentOrdainerForInternalBlocks(kind));
  }

  /** Returns the constructor for the proto. */
  public static CodeChunk.WithValue protoConstructor(SoyProtoType type) {
    return GoogRequire.create(type.getNameForBackend(SoyBackendKind.JS_SRC)).reference();
  }

  /**
   * Returns the js type for the sanitized content object corresponding to the given ContentKind.
   */
  public static CodeChunk.WithValue sanitizedContentType(SanitizedContentKind kind) {
    return GoogRequire.create(NodeContentKinds.toJsSanitizedContentCtorName(kind)).reference();
  }

  /**
   * Returns a code chunk that accesses the given symbol.
   *
   * @param requireSymbol The symbol to {@code goog.require}
   * @param fullyQualifiedSymbol The symbol we want to access.
   */
  private static CodeChunk.WithValue symbolWithNamespace(
      String requireSymbol, String fullyQualifiedSymbol) {
    GoogRequire require = GoogRequire.create(requireSymbol);
    if (fullyQualifiedSymbol.equals(require.symbol())) {
      return require.reference();
    }
    String ident = fullyQualifiedSymbol.substring(require.symbol().length() + 1);
    return require.dotAccess(ident);
  }
}
