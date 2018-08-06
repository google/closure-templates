/*
 * Copyright 2018 Google Inc.
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

package com.google.template.soy.soytree.defn;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.types.SoyType;
import javax.annotation.Nullable;

/**
 * Interface for the definition of a template header variable, i.e. a value that is declared in a
 * template using @param or @state.
 */
public interface TemplateHeaderVarDefn {

  /** The variable's name. */
  String name();

  /** The data type of the value. */
  SoyType type();

  /** The location of the name in the template source. */
  @Nullable
  SourceLocation nameLocation();

  /** Returns whether the param is required. */
  boolean isRequired();

  /**
   * The variable description, provided via Soy doc comments. {@see
   * https://developers.google.com/closure/templates/docs/commands#param}.
   */
  @Nullable
  String desc();
}
