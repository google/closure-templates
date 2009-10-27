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
  private final ImmutableMap<String, ParamRequisiteness> params;


  /**
   * @param name The full template name.
   * @param params Map from each param to whether it's required for this template.
   */
  public SoyTemplateInfo(String name, ImmutableMap<String, ParamRequisiteness> params) {
    this.name = name;
    int lastDotPos = name.lastIndexOf('.');
    Preconditions.checkArgument(lastDotPos > 0);
    this.partialName = name.substring(lastDotPos);
    this.params = params;
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
    return params;
  }

}
