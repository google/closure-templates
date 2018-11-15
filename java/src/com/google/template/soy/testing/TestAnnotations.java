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

package com.google.template.soy.testing;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Annotations used for individual tests. */
public final class TestAnnotations {

  /**
   * Annotates a junit test method to enable a set of experimental features when running tests that
   * build a {@link com.google.template.soy.SoyFileSet}.
   *
   * <p>Example usage:
   *
   * <pre>{@code
   * @Test
   * @ExperimentalFeatures({"prop_vars", "no_stack_canary"})
   * public void good_template_prop_vars() throws Exception {
   *   runTest();
   * }
   * }</pre>
   */
  @Retention(RetentionPolicy.RUNTIME)
  public @interface ExperimentalFeatures {
    /** The list of experimental features to enable. */
    String[] value() default {};
  }
}
