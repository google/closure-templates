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
package com.google.template.soy.basicdirectives;

import com.google.template.soy.data.SoyValue;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class BasicEscapeDirectivesTest {

  @Test
  public void testStringOverloadsExistForAllDirectives() {
    for (SoyPrintDirective directive : BasicDirectives.directives()) {
      if (directive instanceof BasicEscapeDirective) {
        BasicEscapeDirective basicEscapeDirective = (BasicEscapeDirective) directive;
        // Ensures that the overloads both exist.
        basicEscapeDirective.javaSanitizer(SoyValue.class);
        basicEscapeDirective.javaSanitizer(String.class);
        if (directive instanceof BasicEscapeDirective.EscapeJsValue) {
          basicEscapeDirective.javaSanitizer(boolean.class);
          basicEscapeDirective.javaSanitizer(double.class);
        }
      }
    }
  }
}
