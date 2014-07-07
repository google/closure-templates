/*
 * Copyright 2014 Google Inc.
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

package com.google.template.soy.sharedpasses;

import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.shared.internal.SharedTestUtils;

import junit.framework.TestCase;

/**
 * Unit tests for {@link CheckTemplateVisibility}.
 *
 * @author brndn@google.com (Brendan Linn)
 */
public class CheckTemplateVisibilityTest extends TestCase {

  public void testCallPrivateTemplateFromSameFile() {
    assertNoVisibilityError("{namespace ns autoescape=\"strict\"}\n"
        + "/** Private template. */\n"
        + "{template .foo visibility=\"private\"}\n"
        + "oops!\n"
        + "{/template}\n"
        + "/** Public template. */\n"
        + "{template .bar}\n"
        + "{call .foo /}\n"
        + "{/template}");
  }

  public void testCallPrivateTemplateFromSameNamespaceButDifferentFile() {
    assertVisibilityError(
        "{namespace ns autoescape=\"strict\"}\n"
        + "/** Private template. */\n"
        + "{template .foo visibility=\"private\"}\n"
        + "oops!\n"
        + "{/template}",

        "{namespace ns autoescape=\"strict\"}\n"
        + "/** Public template. */\n"
        + "{template .bar}\n"
        + "{call .foo /}\n"
        + "{/template}");
  }

  public void testCallPrivateTemplateFromSameNamespaceAndDifferentFile() {
    assertVisibilityError(
        "{namespace ns autoescape=\"strict\"}\n"
        + "/** Private template. */\n"
        + "{template .foo visibility=\"private\"}\n"
        + "oops!\n"
        + "{/template}",

        "{namespace ns2 autoescape=\"strict\"}\n"
        + "/** Public template. */\n"
        + "{template .bar}\n"
        + "{call ns.foo /}\n"
        + "{/template}");
  }

  private void assertVisibilityError(String... sources) {
    SoySyntaxException sse = null;
    try {
      SharedTestUtils.parseSoyFiles(
        true /* doRunInitialParsingPasses */, true /* doRunCheckingPasses */, sources);
    } catch (SoySyntaxException e) {
      sse = e;
    }
    assertNotNull("expect a SoySyntaxException", sse);
  }

  private void assertNoVisibilityError(String... sources) {
    try {
      SharedTestUtils.parseSoyFiles(
        true /* doRunInitialParsingPasses */, true /* doRunCheckingPasses */, sources);
    } catch (SoySyntaxException e) {
      fail();
    }
  }
}
