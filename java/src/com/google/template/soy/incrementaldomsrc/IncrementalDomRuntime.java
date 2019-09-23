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
package com.google.template.soy.incrementaldomsrc;

import static com.google.template.soy.jssrc.dsl.Expression.id;
import static com.google.template.soy.jssrc.internal.JsRuntime.JS_TO_PROTO_PACK_FN_BASE;

import com.google.common.collect.ImmutableMap;
import com.google.common.html.types.SafeHtmlProto;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.jssrc.dsl.GoogRequire;

/**
 * Common runtime symbols for incrementaldom.
 *
 * <p>Unlike jssrc, incrementaldom declares {@code goog.module}s and therefore uses aliased {@code
 * goog.require} statements.
 */
final class IncrementalDomRuntime {

  static final GoogRequire INCREMENTAL_DOM_LIB =
      GoogRequire.createTypeRequireWithAlias(
          "google3.javascript.template.soy.api_idom", "incrementaldomlib");

  private static final GoogRequire SANITIZED_CONTENT_KIND =
      GoogRequire.createWithAlias("goog.soy.data.SanitizedContentKind", "SanitizedContentKind");

  static final GoogRequire SOY_IDOM =
      GoogRequire.createWithAlias("google3.javascript.template.soy.soyutils_idom", "soyIdom");

  public static final String INCREMENTAL_DOM_PARAM_NAME = "idomRenderer";
  public static final Expression INCREMENTAL_DOM = id(INCREMENTAL_DOM_PARAM_NAME);

  public static final Expression INCREMENTAL_DOM_OPEN = INCREMENTAL_DOM.dotAccess("open");

  public static final Expression INCREMENTAL_DOM_MAYBE_SKIP =
      INCREMENTAL_DOM.dotAccess("maybeSkip");

  public static final Expression INCREMENTAL_DOM_OPEN_SSR = INCREMENTAL_DOM.dotAccess("openSSR");

  public static final Expression INCREMENTAL_DOM_CLOSE = INCREMENTAL_DOM.dotAccess("close");

  public static final Expression INCREMENTAL_DOM_APPLY_STATICS =
      INCREMENTAL_DOM.dotAccess("applyStatics");

  public static final Expression INCREMENTAL_DOM_APPLY_ATTRS =
      INCREMENTAL_DOM.dotAccess("applyAttrs");

  public static final Expression INCREMENTAL_DOM_ENTER = INCREMENTAL_DOM.dotAccess("enter");

  public static final Expression INCREMENTAL_DOM_EXIT = INCREMENTAL_DOM.dotAccess("exit");

  public static final Expression INCREMENTAL_DOM_VERIFY_LOGONLY =
      INCREMENTAL_DOM.dotAccess("verifyLogOnly");

  public static final Expression INCREMENTAL_DOM_TODEFAULT =
      INCREMENTAL_DOM.dotAccess("toDefaultRenderer");

  public static final Expression INCREMENTAL_DOM_TONULL =
      INCREMENTAL_DOM.dotAccess("toNullRenderer");

  public static final Expression INCREMENTAL_DOM_TEXT = INCREMENTAL_DOM.dotAccess("text");

  public static final Expression INCREMENTAL_DOM_ATTR = INCREMENTAL_DOM.dotAccess("attr");

  public static final Expression INCREMENTAL_DOM_PUSH_KEY = INCREMENTAL_DOM.dotAccess("pushKey");
  public static final Expression INCREMENTAL_DOM_POP_KEY = INCREMENTAL_DOM.dotAccess("popKey");
  public static final Expression INCREMENTAL_DOM_PUSH_MANUAL_KEY =
      INCREMENTAL_DOM.dotAccess("pushManualKey");
  public static final Expression INCREMENTAL_DOM_POP_MANUAL_KEY =
      INCREMENTAL_DOM.dotAccess("popManualKey");

  public static final Expression SOY_IDOM_MAKE_HTML = SOY_IDOM.dotAccess("$$makeHtml");

  public static final Expression SOY_IDOM_TYPE_HTML = SANITIZED_CONTENT_KIND.dotAccess("HTML");
  public static final Expression SOY_IDOM_TYPE_ATTRIBUTE =
      SANITIZED_CONTENT_KIND.dotAccess("ATTRIBUTES");

  public static final Expression SOY_IDOM_MAKE_ATTRIBUTES = SOY_IDOM.dotAccess("$$makeAttributes");

  public static final Expression SOY_IDOM_CALL_DYNAMIC_ATTRIBUTES =
      SOY_IDOM.dotAccess("$$callDynamicAttributes");

  public static final Expression SOY_IDOM_CALL_DYNAMIC_CSS = SOY_IDOM.dotAccess("$$callDynamicCss");
  public static final Expression SOY_IDOM_CALL_DYNAMIC_JS = SOY_IDOM.dotAccess("$$callDynamicJs");
  public static final Expression SOY_IDOM_VISIT_HTML_COMMENT =
      SOY_IDOM.dotAccess("$$visitHtmlCommentNode");
  public static final Expression SOY_IDOM_CALL_DYNAMIC_TEXT =
      SOY_IDOM.dotAccess("$$callDynamicText");

  public static final Expression SOY_IDOM_CALL_DYNAMIC_HTML =
      SOY_IDOM.dotAccess("$$callDynamicHTML");

  public static final Expression SOY_IDOM_PRINT_DYNAMIC_ATTR =
      SOY_IDOM.dotAccess("$$printDynamicAttr");

  public static final Expression SOY_IDOM_IS_TRUTHY = SOY_IDOM.dotAccess("$$isTruthy");

  public static final Expression SOY_IDOM_PRINT = SOY_IDOM.dotAccess("$$print");

  public static final Expression INCREMENTAL_DOM_EVAL_LOG_FN =
      INCREMENTAL_DOM.dotAccess("evalLoggingFunction");

  /** Prefix for state vars of stateful template objects. */
  public static final String STATE_PREFIX = "state_";

  /** The JavaScript method to pack a sanitized object into a safe proto. */
  public static final ImmutableMap<String, Expression> IDOM_JS_TO_PROTO_PACK_FN =
      ImmutableMap.<String, Expression>builder()
          .putAll(JS_TO_PROTO_PACK_FN_BASE)
          .put(
              SafeHtmlProto.getDescriptor().getFullName(),
              GoogRequire.createWithAlias("soydata.converters.idom", "$soyDataConverters")
                  .reference()
                  .dotAccess("packSanitizedHtmlToProtoSoyRuntimeOnly"))
          .build();

  private IncrementalDomRuntime() {}
}
