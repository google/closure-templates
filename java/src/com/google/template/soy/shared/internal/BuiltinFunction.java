/*
 * Copyright 2009 Google Inc.
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

package com.google.template.soy.shared.internal;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.types.AnyType;
import com.google.template.soy.types.BoolType;
import com.google.template.soy.types.IterableType;
import com.google.template.soy.types.NullType;
import com.google.template.soy.types.SanitizedType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.StringType;
import com.google.template.soy.types.UndefinedType;
import com.google.template.soy.types.UnionType;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Enum of built-in functions supported in Soy expressions. */
public enum BuiltinFunction implements SoyFunction {
  CHECK_NOT_NULL("checkNotNull"),
  /**
   * Function for substituting CSS class names according to a lookup map.
   *
   * <p>Takes 1 or 2 arguments: an optional prefix (if present, this is the first arg), followed by
   * a string literal selector name.
   */
  CSS("css"),
  /**
   * Function for rewriting toggle import in Java / JS. Server side, we query activeModSelector in
   * RenderContext and client side we use ts_toggle_library output.
   */
  EVAL_TOGGLE("$evalToggle"),
  XID("xid"),
  RECORD_JS_ID("$recordJsId"),
  SOY_SERVER_KEY("$soyServerKey"),
  UNKNOWN_JS_GLOBAL("unknownJsGlobal"),
  REMAINDER("remainder"),
  MSG_WITH_ID("msgWithId"),
  VE_DATA("ve_data"),
  LEGACY_DYNAMIC_TAG("legacyDynamicTag"),
  IS_PRIMARY_MSG_IN_USE("$$isPrimaryMsgInUse"),
  TO_NUMBER("$$toNumber"),
  INT_TO_NUMBER("$$intToNumber"),
  DEBUG_SOY_TEMPLATE_INFO("$$debugSoyTemplateInfo"),
  PROTO_INIT("$$protoInit"),
  VE_DEF("ve_def"),
  EMPTY_TO_UNDEFINED("$$emptyToUndefined"),
  UNDEFINED_TO_NULL("undefinedToNullForMigration"),
  UNDEFINED_TO_NULL_SSR("undefinedToNullForSsrMigration"),
  BOOLEAN("Boolean"),
  HAS_CONTENT("hasContent"),
  IS_TRUTHY_NON_EMPTY("isTruthyNonEmpty"),
  NEW_SET("Set"),
  FLUSH_PENDING_LOGGING_ATTRIBUTES("$$flushPendingLoggingAttributes");

  /** The function name. */
  private final String functionName;

  BuiltinFunction(String name) {
    this.functionName = name;
  }

  @Override
  public String getName() {
    return functionName;
  }

  @Override
  public Set<Integer> getValidArgsSizes() {
    switch (this) {
      case CSS:
      case EVAL_TOGGLE:
      case VE_DATA:
        return ImmutableSet.of(1, 2);
      case IS_PRIMARY_MSG_IN_USE:
        return ImmutableSet.of(3);
      case DEBUG_SOY_TEMPLATE_INFO:
        return ImmutableSet.of(0);
      case SOY_SERVER_KEY:
      case CHECK_NOT_NULL:
      case XID:
      case RECORD_JS_ID:
      case UNKNOWN_JS_GLOBAL:
      case LEGACY_DYNAMIC_TAG:
      case REMAINDER:
      case MSG_WITH_ID:
      case TO_NUMBER:
      case INT_TO_NUMBER:
      case EMPTY_TO_UNDEFINED:
      case UNDEFINED_TO_NULL:
      case UNDEFINED_TO_NULL_SSR:
      case BOOLEAN:
      case IS_TRUTHY_NON_EMPTY:
      case HAS_CONTENT:
      case NEW_SET:
      case FLUSH_PENDING_LOGGING_ATTRIBUTES:
        return ImmutableSet.of(1);
      case PROTO_INIT:
        throw new UnsupportedOperationException();
      case VE_DEF:
        return ImmutableSet.of(2, 3, 4);
    }
    throw new AssertionError(this);
  }

  public Optional<List<SoyType>> getValidArgTypes() {
    switch (this) {
      case HAS_CONTENT:
        return Optional.of(
            ImmutableList.of(
                UnionType.of(
                    SanitizedType.AttributesType.getInstance(),
                    SanitizedType.ElementType.getInstance("?"),
                    SanitizedType.HtmlType.getInstance(),
                    SanitizedType.JsType.getInstance(),
                    SanitizedType.StyleType.getInstance(),
                    SanitizedType.TrustedResourceUriType.getInstance(),
                    SanitizedType.UriType.getInstance(),
                    StringType.getInstance(),
                    NullType.getInstance(),
                    UndefinedType.getInstance())));
      case FLUSH_PENDING_LOGGING_ATTRIBUTES:
        return Optional.of(ImmutableList.of(BoolType.getInstance()));
      case NEW_SET:
        // This is further constrained in ResolveExpressionTypesPass.
        return Optional.of(ImmutableList.of(IterableType.of(AnyType.getInstance())));
      default:
        return Optional.empty();
    }
  }

  /**
   * Whether or not this function is pure.
   *
   * <p>This is equivalent to annotating a function with {@link
   * com.google.template.soy.shared.restricted.SoyPureFunction}. See {@link
   * com.google.template.soy.shared.restricted.SoyPureFunction} for the definition of a pure
   * function.
   */
  @Override
  public boolean isPure() {
    switch (this) {
      case CHECK_NOT_NULL:
      case MSG_WITH_ID:
      case VE_DATA:
      case TO_NUMBER:
      case INT_TO_NUMBER:
      case PROTO_INIT:
      case EMPTY_TO_UNDEFINED:
      case UNDEFINED_TO_NULL:
      case UNDEFINED_TO_NULL_SSR:
      case BOOLEAN:
      case HAS_CONTENT:
      case IS_TRUTHY_NON_EMPTY:
      case NEW_SET:
        return true;
      case CSS: // implicitly depends on a renaming map or js compiler flag
      case EVAL_TOGGLE:
      case XID: // implicitly depends on a renaming map or js compiler flag
      case RECORD_JS_ID:
      case SOY_SERVER_KEY: // Relies on call stack dependent on rendering
      case UNKNOWN_JS_GLOBAL: // this is a black box from the compiler perspective
      case LEGACY_DYNAMIC_TAG: // this is a black box from the compiler perspective
      case REMAINDER: // implicitly depends on a plural value
      case IS_PRIMARY_MSG_IN_USE: // implicitly depends on a message bundle
      case DEBUG_SOY_TEMPLATE_INFO: // implicitly depends on a renderer param or js compiler flag
      case VE_DEF:
      case FLUSH_PENDING_LOGGING_ATTRIBUTES: // implicitly depends on ephemeral state
        return false;
    }
    throw new AssertionError(this);
  }

  public String deprecatedWarning() {
    return "";
  }
}
