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
import static com.google.template.soy.jssrc.dsl.CodeChunk.dottedIdWithCustomNamespace;
import static com.google.template.soy.jssrc.dsl.CodeChunk.dottedIdWithRequire;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.template.soy.base.SoyBackendKind;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.internalutils.NodeContentKinds;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.types.proto.Protos;
import com.google.template.soy.types.proto.SoyProtoType;

/**
 * Constants for commonly used js runtime functions and objects.
 *
 * <p>Unlike {@code JsExprUtils}, this is only intended for use by the compiler itself and deals
 * exclusively with the {@link CodeChunk} api.
 */
public final class JsRuntime {

  private JsRuntime() {}

  public static final CodeChunk.WithValue GOOG_ARRAY_MAP =
      dottedIdWithRequire("goog.array").dotAccess("map");

  public static final CodeChunk.WithValue GOOG_ASSERTS_ASSERT =
      dottedIdWithRequire("goog.asserts").dotAccess("assert");

  public static final CodeChunk.WithValue GOOG_GET_CSS_NAME = dottedIdNoRequire("goog.getCssName");

  public static final CodeChunk.WithValue GOOG_GET_MSG = dottedIdNoRequire("goog.getMsg");

  public static final CodeChunk.WithValue GOOG_IS_ARRAY = dottedIdNoRequire("goog.isArray");

  public static final CodeChunk.WithValue GOOG_IS_BOOLEAN = dottedIdNoRequire("goog.isBoolean");

  public static final CodeChunk.WithValue GOOG_IS_FUNCTION = dottedIdNoRequire("goog.isFunction");

  public static final CodeChunk.WithValue GOOG_IS_NUMBER = dottedIdNoRequire("goog.isNumber");

  public static final CodeChunk.WithValue GOOG_IS_OBJECT = dottedIdNoRequire("goog.isObject");

  public static final CodeChunk.WithValue GOOG_IS_STRING = dottedIdNoRequire("goog.isString");

  public static final CodeChunk.WithValue GOOG_SOY_DATA_SANITIZED_CONTENT =
      dottedIdWithRequire("goog.soy.data.SanitizedContent");
  public static final CodeChunk.WithValue GOOG_STRING_UNESCAPE_ENTITIES =
      dottedIdWithRequire("goog.string").dotAccess("unescapeEntities");

  public static final CodeChunk.WithValue GOOG_I18N_MESSAGE_FORMAT =
      dottedIdWithRequire("goog.i18n.MessageFormat");

  public static final CodeChunk.WithValue SOY_ASSERTS_ASSERT_TYPE =
      dottedIdWithRequire("soy.asserts").dotAccess("assertType");

  public static final CodeChunk.WithValue SOY_ASSIGN_DEFAULTS =
      dottedIdWithRequire("soy").dotAccess("$$assignDefaults");

  public static final CodeChunk.WithValue SOY_CHECK_MAP_KEY =
      dottedIdWithRequire("soy").dotAccess("$$checkMapKey");

  public static final CodeChunk.WithValue SOY_CHECK_NOT_NULL =
      dottedIdWithRequire("soy").dotAccess("$$checkNotNull");

  public static final CodeChunk.WithValue SOY_ESCAPE_HTML =
      dottedIdWithRequire("soy").dotAccess("$$escapeHtml");

  public static final CodeChunk.WithValue SOY_GET_DELEGATE_FN =
      dottedIdWithRequire("soy").dotAccess("$$getDelegateFn");
  
  public static final CodeChunk.WithValue SOY_REGISTER_DELEGATE_FN =
      dottedIdWithRequire("soy").dotAccess("$$registerDelegateFn");
  
  public static final CodeChunk.WithValue SOY_GET_DELTEMPLATE_ID =
      dottedIdWithRequire("soy").dotAccess("$$getDelTemplateId");

  public static final CodeChunk.WithValue WINDOW_CONSOLE_LOG =
      dottedIdNoRequire("window.console.log");

  public static final CodeChunk.WithValue XID = dottedIdWithRequire("xid");

  /** Returns the field containing the extension object for the given field descriptor. */
  public static CodeChunk.WithValue extensionField(FieldDescriptor desc) {
    return dottedIdWithCustomNamespace(
        Protos.getJsExtensionName(desc), Protos.getJsExtensionImport(desc));
  }

  /** Returns a function that can 'unpack' safe proto types into sanitized content types.. */
  public static CodeChunk.WithValue protoToSanitizedContentConverterFunction(
      Descriptor messageType) {
    return dottedIdWithRequire(NodeContentKinds.toJsUnpackFunction(messageType));
  }

  /** Returns a function that can 'unpack' safe proto types into sanitized content types.. */
  public static CodeChunk.WithValue sanitizedContentToProtoConverterFunction(
      Descriptor messageType) {
    return dottedIdWithRequire(NodeContentKinds.toJsPackFunction(messageType));
  }

  /**
   * Returns an 'ordainer' function that can be used wrap a {@code string} in a {@code
   * SanitizedContent} object with no escaping.
   */
  public static CodeChunk.WithValue sanitizedContentOrdainerFunction(ContentKind kind) {
    return dottedIdWithCustomNamespace(
        NodeContentKinds.toJsSanitizedContentOrdainer(kind),
        NodeContentKinds.getJsImportForOrdainersFunctions(kind));
  }

  /**
   * Returns an 'ordainer' function that can be used wrap a {@code string} in a {@code
   * SanitizedContent} object with no escaping.
   */
  public static CodeChunk.WithValue sanitizedContentOrdainerFunctionForInternalBlocks(
      ContentKind kind) {
    return dottedIdWithCustomNamespace(
        NodeContentKinds.toJsSanitizedContentOrdainerForInternalBlocks(kind),
        NodeContentKinds.getJsImportForOrdainersFunctions(kind));
  }

  /** Returns the constructor for the proto. */
  public static CodeChunk.WithValue protoConstructor(SoyProtoType type) {
    return dottedIdWithRequire(type.getNameForBackend(SoyBackendKind.JS_SRC));
  }

  /**
   * Returns the js type for the sanitized content object corresponding to the given ContentKind.
   */
  public static CodeChunk.WithValue sanitizedContentType(ContentKind kind) {
    return dottedIdWithRequire(NodeContentKinds.toJsSanitizedContentCtorName(kind));
  }
}
