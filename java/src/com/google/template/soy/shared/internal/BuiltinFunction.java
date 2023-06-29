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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.shared.restricted.SoyFunction;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Enum of built-in functions supported in Soy expressions.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public enum BuiltinFunction implements SoyFunction {
  CHECK_NOT_NULL("checkNotNull"),
  // TODO(b/144312362): try changing it to private $$isParamSet when hooking up with default value
  // desugaring so that it can be tested implicitly.
  IS_PARAM_SET("isParamSet"),
  /**
   * Function for substituting CSS class names according to a lookup map.
   *
   * <p>Takes 1 or 2 arguments: an optional prefix (if present, this is the first arg), followed by
   * a string literal selector name.
   */
  CSS("css"),
  XID("xid"),
  SOY_SERVER_KEY("$soyServerKey"),
  UNKNOWN_JS_GLOBAL("unknownJsGlobal"),
  REMAINDER("remainder"),
  MSG_WITH_ID("msgWithId"),
  VE_DATA("ve_data"),
  LEGACY_DYNAMIC_TAG("legacyDynamicTag"),
  IS_PRIMARY_MSG_IN_USE("$$isPrimaryMsgInUse"),
  TO_FLOAT("$$toFloat"),
  DEBUG_SOY_TEMPLATE_INFO("$$debugSoyTemplateInfo"),
  PROTO_INIT("$$protoInit"),
  VE_DEF("ve_def"),
  ID_HOLDER("idHolder"),
  UNIQUE_ATTRIBUTE("uniqueAttribute"),
  ;

  public static ImmutableSet<String> names() {
    return NONPLUGIN_FUNCTIONS_BY_NAME.keySet();
  }

  /** Map of NonpluginFunctions by function name. */
  private static final ImmutableMap<String, BuiltinFunction> NONPLUGIN_FUNCTIONS_BY_NAME;

  static {
    ImmutableMap.Builder<String, BuiltinFunction> mapBuilder = ImmutableMap.builder();
    for (BuiltinFunction nonpluginFn : values()) {
      mapBuilder.put(nonpluginFn.functionName, nonpluginFn);
    }
    NONPLUGIN_FUNCTIONS_BY_NAME = mapBuilder.build();
  }

  /**
   * Returns the NonpluginFunction for the given function name, or null if not found.
   *
   * @param functionName The function name to retrieve.
   */
  @Nullable
  public static BuiltinFunction forFunctionName(String functionName) {
    return NONPLUGIN_FUNCTIONS_BY_NAME.get(functionName);
  }

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
      case UNIQUE_ATTRIBUTE:
        return ImmutableSet.of(1, 2);
      case VE_DATA:
        return ImmutableSet.of(1, 2);
      case IS_PRIMARY_MSG_IN_USE:
        return ImmutableSet.of(3);
      case ID_HOLDER:
      case DEBUG_SOY_TEMPLATE_INFO:
        return ImmutableSet.of(0);
      case IS_PARAM_SET:
      case SOY_SERVER_KEY:
      case CHECK_NOT_NULL:
      case XID:
      case UNKNOWN_JS_GLOBAL:
      case LEGACY_DYNAMIC_TAG:
      case REMAINDER:
      case MSG_WITH_ID:
      case TO_FLOAT:
        return ImmutableSet.of(1);
      case PROTO_INIT:
        throw new UnsupportedOperationException();
      case VE_DEF:
        return ImmutableSet.of(2, 3, 4);
    }
    throw new AssertionError(this);
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
      case TO_FLOAT:
      case PROTO_INIT:
      case IS_PARAM_SET:
      case UNIQUE_ATTRIBUTE:
        return true;
      case CSS: // implicitly depends on a renaming map or js compiler flag
      case XID: // implicitly depends on a renaming map or js compiler flag
      case SOY_SERVER_KEY: // Relies on call stack dependent on rendering
      case UNKNOWN_JS_GLOBAL: // this is a black box from the compiler perspective
      case LEGACY_DYNAMIC_TAG: // this is a black box from the compiler perspective
      case REMAINDER: // implicitly depends on a plural value
      case IS_PRIMARY_MSG_IN_USE: // implicitly depends on a message bundle
      case DEBUG_SOY_TEMPLATE_INFO: // implicitly depends on a renderer param or js compiler flag
      case ID_HOLDER: // implicitly depends on UniqueAttributeFunctionRuntime to get unique id
      case VE_DEF:
        return false;
    }
    throw new AssertionError(this);
  }

  public String deprecatedWarning() {
    return "";
  }
}
