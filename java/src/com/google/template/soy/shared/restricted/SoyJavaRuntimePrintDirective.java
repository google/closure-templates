/*
 * Copyright 2011 Google Inc.
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

package com.google.template.soy.shared.restricted;

import com.google.template.soy.data.SoyData;

import java.util.List;


/**
 * Interface for a Soy print directive implemented for Java runtime rendering. The {@code apply()}
 * method of this interface should be the same as the {@code applyForTofu()} method of interface
 * {@code SoyTofuPrintDirective}. The difference is that directives implementing this interface may
 * be useful to the Soy compiler during optimization passes.
 *
 * <p> Important: This may only be used in implementing print directive plugins.
 *
 * @author Kai Huang
 */
public interface SoyJavaRuntimePrintDirective extends SoyPrintDirective {


  /**
   * Applies this directive on the given value.
   *
   * @param value The input to the directive.  This may have not yet been coerced to a string.
   * @param args The directive's arguments, if any (usually none).
   * @return The resulting value.
   */
  public String apply(SoyData value, List<SoyData> args);

}
