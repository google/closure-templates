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

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableMap;

import java.util.Map;


/**
 * Enum of nonplugin functions supported in Soy expressions.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public enum NonpluginFunction {


  IS_FIRST(1),
  IS_LAST(1),
  INDEX(1),
  HAS_DATA(0);


  /** Map of NonpluginFunctions by function name. */
  private static final Map<String, NonpluginFunction> NONPLUGIN_FUNCTIONS_BY_NAME;
  static {
    ImmutableMap.Builder<String, NonpluginFunction> mapBuilder = ImmutableMap.builder();
    for (NonpluginFunction nonpluginFn : values()) {
      mapBuilder.put(nonpluginFn.getFunctionName(), nonpluginFn);
    }
    NONPLUGIN_FUNCTIONS_BY_NAME = mapBuilder.build();
  }


  /**
   * Returns the NonpluginFunction for the given function name, or null if not found.
   * @param functionName The function name to retrieve.
   */
  public static NonpluginFunction forFunctionName(String functionName) {
    return NONPLUGIN_FUNCTIONS_BY_NAME.get(functionName);
  }


  /** The function name. */
  private final String functionName;

  /** The number of arguments. */
  private final int numArgs;


  private NonpluginFunction(int numArgs) {
    this.functionName = CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, name());
    this.numArgs = numArgs;
  }


  /** Returns the function name. */
  public String getFunctionName() {
    return functionName;
  }

  /** Returns the number of arguments. */
  public int getNumArgs() {
    return numArgs;
  }

}
