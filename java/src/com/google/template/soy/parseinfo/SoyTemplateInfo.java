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
import com.google.errorprone.annotations.Immutable;

/**
 * Parsed info about a template.
 *
 */
@Immutable
public class SoyTemplateInfo {

  /** Enum for whether a param is required or optional for a specific template. */
  public static enum ParamRequisiteness {
    REQUIRED,
    OPTIONAL;
  }

  /** The full template name. */
  private final String name;

  /** Map from each param to whether it's required for this template. */
  private final ImmutableMap<String, ParamRequisiteness> paramMap;

  /** Set of injected params used by this template (or a transitive callee). */
  private final ImmutableSortedSet<String> ijParamSet;

  /**
   * Constructor for internal use only, for the general case.
   *
   * <p>Important: Do not construct SoyTemplateInfo objects outside of Soy internal or Soy-generated
   * code. User code that constructs SoyTemplateInfo objects will be broken by future Soy changes.
   *
   * @param name The full template name.
   * @param paramMap Map from each param to whether it's required for this template.
   * @param ijParamSet Set of injected params used by this template (or a transitive callee).
   */
  public SoyTemplateInfo(
      String name,
      ImmutableMap<String, ParamRequisiteness> paramMap,
      ImmutableSortedSet<String> ijParamSet) {
    this.name = name;
    Preconditions.checkArgument(name.lastIndexOf('.') > 0);
    this.paramMap = paramMap;
    this.ijParamSet = ijParamSet;
  }

  /** Returns the full template name, e.g. {@code myNamespace.myTemplate}. */
  public String getName() {
    return name;
  }

  /** Returns the partial template name (starting from the last dot), e.g. {@code .myTemplate}. */
  public String getPartialName() {
    return name.substring(name.lastIndexOf('.'));
  }

  /** Returns a map from each param to whether it's required for this template. */
  public ImmutableMap<String, ParamRequisiteness> getParams() {
    return paramMap;
  }

  /** Returns the set of injected params used by this template (or a transitive callee). */
  public ImmutableSortedSet<String> getUsedIjParams() {
    return ijParamSet;
  }
}
