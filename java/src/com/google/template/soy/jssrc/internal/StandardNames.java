/*
 * Copyright 2020 Google Inc.
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

/** A collection of standard names used throughout the code generator. */
public final class StandardNames {

  /**
   * Legacy name for the data parameter.
   *
   * <p>TODO(b/177856412): this should be replaced with {@link #DOLLAR_DATA}.
   */
  public static final String OPT_DATA = "opt_data";

  public static final String DOLLAR_DATA = "$data";
  /**
   * Legacy name for the ijdata parameter.
   *
   * <p>TODO(b/177856412): this should be replaced with {@link #DOLLAR_DATA}.
   */
  public static final String OPT_IJDATA = "opt_ijData";

  public static final String DOLLAR_IJDATA = "$ijData";
  public static final String ARE_YOU_AN_INTERNAL_CALLER = "$$areYouAnInternalCaller";

  private StandardNames() {}
}
