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

package com.google.template.soy.parseinfo;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;


/**
 * Parsed info about a template.
 *
 * @author Kai Huang
 */
public class SoyTemplateInfo {


  /**
   * Enum for whether a param is required or optional for a specific template.
   */
  public static enum ParamRequisiteness {
    REQUIRED,
    OPTIONAL;
  }


  /** The full template name. */
  private final String name;

  /** The partial template name (starting from the last dot). */
  private final String partialName;

  /** Map from each param to whether it's required for this template. */
  private final ImmutableMap<String, ParamRequisiteness> paramMap;

  /** Set of injected params used by this template (or a transitive callee). */
  private final ImmutableSortedSet<String> ijParamSet;

  /** Whether this template may have injected params indirectly used in external basic calls. */
  private final boolean mayHaveIjParamsInExternalCalls;

  /** Whether this template may have injected params indirectly used in external delegate calls. */
  private final boolean mayHaveIjParamsInExternalDelCalls;


  /**
   * Constructor for internal use only, for the case of a template that doesn't use injected data
   * (even transitively).
   *
   * <p> Important: Do not construct SoyTemplateInfo objects outside of Soy internal or
   * Soy-generated code. User code that constructs SoyTemplateInfo objects will be broken by future
   * Soy changes.
   *
   * @param name The full template name.
   * @param paramMap Map from each param to whether it's required for this template.
   * @deprecated Users should not be creating SoyTemplateInfo objects. If you're constructing
   *     SoyTemplateInfo objects from non-Soy-internal code, your code will be broken by future
   *     Soy changes.
   */
  @Deprecated
  public SoyTemplateInfo(String name, ImmutableMap<String, ParamRequisiteness> paramMap) {
    this(name, paramMap, ImmutableSortedSet.<String>of(), false, false);
  }


  /**
   * Constructor for internal use only, for the general case.
   *
   * <p> Important: Do not construct SoyTemplateInfo objects outside of Soy internal or
   * Soy-generated code. User code that constructs SoyTemplateInfo objects will be broken by future
   * Soy changes.
   *
   * @param name The full template name.
   * @param paramMap Map from each param to whether it's required for this template.
   * @param ijParamSet Set of injected params used by this template (or a transitive callee).
   * @param mayHaveIjParamsInExternalCalls Whether this template may have injected params
   *     indirectly used in external basic calls.
   * @param mayHaveIjParamsInExternalDelCalls Whether this template may have injected params
   *     indirectly used in external delegate calls.
   */
  public SoyTemplateInfo(
      String name, ImmutableMap<String, ParamRequisiteness> paramMap,
      ImmutableSortedSet<String> ijParamSet, boolean mayHaveIjParamsInExternalCalls,
      boolean mayHaveIjParamsInExternalDelCalls) {
    this.name = name;
    int lastDotPos = name.lastIndexOf('.');
    Preconditions.checkArgument(lastDotPos > 0);
    this.partialName = name.substring(lastDotPos);
    this.paramMap = paramMap;
    this.ijParamSet = ijParamSet;
    this.mayHaveIjParamsInExternalCalls = mayHaveIjParamsInExternalCalls;
    this.mayHaveIjParamsInExternalDelCalls = mayHaveIjParamsInExternalDelCalls;
  }


  /** Returns the full template name, e.g. {@code myNamespace.myTemplate}. */
  public String getName() {
    return name;
  }

  /** Returns the partial template name (starting from the last dot), e.g. {@code .myTemplate}. */
  public String getPartialName() {
    return partialName;
  }

  /** Returns a map from each param to whether it's required for this template. */
  public ImmutableMap<String, ParamRequisiteness> getParams() {
    return paramMap;
  }

  /**
   * Returns the set of injected params used by this template (or a transitive callee).
   * @see #mayHaveIjParamsInExternalCalls()
   * @see #mayHaveIjParamsInExternalDelCalls()
   */
  public ImmutableSortedSet<String> getUsedIjParams() {
    return ijParamSet;
  }

  /**
   * Returns whether this template may have injected params indirectly used in external basic calls
   * (i.e. calls to templates not defined in the bundle of Soy files being compiled together with
   * this template).
   */
  public boolean mayHaveIjParamsInExternalCalls() {
    return mayHaveIjParamsInExternalCalls;
  }

  /**
   * Returns whether this template may have injected params indirectly used in external delegate
   * calls (i.e. delegate calls that resolve to delegate implementations not defined in the bundle
   * of Soy files being compiled together with this template).
   */
  public boolean mayHaveIjParamsInExternalDelCalls() {
    return mayHaveIjParamsInExternalDelCalls;
  }

}
