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

package com.google.template.soy.passes;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Iterables;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.error.FormattingErrorReporter;
import com.google.template.soy.passes.CheckTemplateVisibility;

import junit.framework.TestCase;

/**
 * Unit tests for {@link CheckTemplateVisibility}.
 *
 * @author brndn@google.com (Brendan Linn)
 */
public final class CheckTemplateVisibilityTest extends TestCase {

  public void testCallPrivateTemplateFromSameFile() {
    SoyFileSetParserBuilder.forFileContents("{namespace ns autoescape=\"strict\"}\n"
      + "/** Private template. */\n"
      + "{template .foo visibility=\"private\"}\n"
      + "oops!\n"
      + "{/template}\n"
      + "/** Public template. */\n"
      + "{template .bar}\n"
      + "{call .foo /}\n"
      + "{/template}")
        .errorReporter(ExplodingErrorReporter.get())
        .parse();
  }

  public void testCallPrivateTemplateFromSameNamespaceButDifferentFile() {
    FormattingErrorReporter errorReporter = new FormattingErrorReporter();
    SoyFileSetParserBuilder.forFileContents("{namespace ns autoescape=\"strict\"}\n"
      + "/** Private template. */\n"
      + "{template .foo visibility=\"private\"}\n"
      + "oops!\n"
      + "{/template}", "{namespace ns autoescape=\"strict\"}\n"
      + "/** Public template. */\n"
      + "{template .bar}\n"
      + "{call .foo /}\n"
      + "{/template}")
        .errorReporter(errorReporter)
        .parse();
    assertThat(errorReporter.getErrorMessages()).hasSize(1);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrorMessages())).contains(
        "Template ns.foo has private visibility, not visible from here.");
  }

  public void testCallPrivateTemplateFromSameNamespaceAndDifferentFile() {
    FormattingErrorReporter errorReporter = new FormattingErrorReporter();
    SoyFileSetParserBuilder.forFileContents("{namespace ns autoescape=\"strict\"}\n"
      + "/** Private template. */\n"
      + "{template .foo visibility=\"private\"}\n"
      + "oops!\n"
      + "{/template}", "{namespace ns2 autoescape=\"strict\"}\n"
      + "/** Public template. */\n"
      + "{template .bar}\n"
      + "{call ns.foo /}\n"
      + "{/template}")
        .errorReporter(errorReporter)
        .parse();
    assertThat(errorReporter.getErrorMessages()).hasSize(1);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrorMessages())).contains(
        "Template ns.foo has private visibility, not visible from here.");
  }

}
