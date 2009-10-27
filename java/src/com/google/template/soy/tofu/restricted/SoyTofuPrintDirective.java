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

package com.google.template.soy.tofu.restricted;

import com.google.template.soy.data.SoyData;
import com.google.template.soy.shared.restricted.SoyPrintDirective;

import java.util.List;


/**
 * Interface for a Soy print directive implemented for the Tofu backend (a.k.a. Java Object
 * backend).
 *
 * <p> Important: This may only be used in implementing print directive plugins.
 *
 * @author Kai Huang
 */
public interface SoyTofuPrintDirective extends SoyPrintDirective {


  /**
   * Applies this directive on the given string value.
   *
   * @param str The string value to apply the directive on.
   * @param args The directive's arguments, if any (usually none).
   * @return The resulting value.
   */
  public String applyForTofu(String str, List<SoyData> args);

}
