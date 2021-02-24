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
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link CheckTemplateVisibilityPass}.
 *
 * @author brndn@google.com (Brendan Linn)
 */
@RunWith(JUnit4.class)
public final class CheckTemplateVisibilityPassTest {

  @Test
  public void testCallPrivateTemplateFromSameFile() {
    SoyFileSetParserBuilder.forFileContents(
            "{namespace ns}\n"
                + "/** Private template. */\n"
                + "{template .foo visibility=\"private\"}\n"
                + "oops!\n"
                + "{/template}\n"
                + "/** Public template. */\n"
                + "{template .bar}\n"
                + "{call .foo /}\n"
                + "{/template}")
        .errorReporter(ErrorReporter.exploding())
        .parse();
  }

  @Test
  public void testCallPrivateTemplateFromSameNamespaceButDifferentFile() {
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    SoyFileSetParserBuilder.forFileContents(
            "{namespace ns}\n"
                + "/** Private template. */\n"
                + "{template .foo visibility=\"private\"}\n"
                + "oops!\n"
                + "{/template}",
            "{namespace ns}\n"
                + "import {foo} from 'no-path';"
                + "/** Public template. */\n"
                + "{template .bar}\n"
                + "{call foo /}\n"
                + "{/template}")
        .errorReporter(errorReporter)
        .parse();
    assertThat(errorReporter.getErrors()).hasSize(1);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo("ns.foo has private access in no-path.");
  }

  @Test
  public void testImportPrivateTemplateFromSameNamespaceButDifferentFile() {
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    SoyFileSetParserBuilder.forFileContents(
            "{namespace ns}\n"
                + "/** Private template. */\n"
                + "{template .foo visibility=\"private\"}\n"
                + "oops!\n"
                + "{/template}",
            "{namespace ns}\n"
                + "import {foo} from 'no-path';"
                + "/** Public template. */\n"
                + "{template .bar}\n"
                + "{call foo /}\n"
                + "{/template}")
        .errorReporter(errorReporter)
        .parse();
    assertThat(errorReporter.getErrors()).hasSize(1);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo("ns.foo has private access in no-path.");
  }

  @Test
  public void testCallPrivateTemplateDifferentFile() {
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    SoyFileSetParserBuilder.forFileContents(
            "{namespace ns}\n"
                + "/** Private template. */\n"
                + "{template .foo visibility=\"private\"}\n"
                + "oops!\n"
                + "{/template}",
            "{namespace ns2}\n"
                + "import {foo} from 'no-path';"
                + "/** Public template. */\n"
                + "{template .bar}\n"
                + "{call foo /}\n"
                + "{/template}")
        .errorReporter(errorReporter)
        .parse();
    assertThat(errorReporter.getErrors()).hasSize(1);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo("ns.foo has private access in no-path.");
  }

  @Test
  public void testBindPrivateTemplateDifferentFile() {
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    SoyFileSetParserBuilder.forFileContents(
            "{namespace ns}\n"
                + "/** Private template. */\n"
                + "{template .foo visibility=\"private\"}\n"
                + "oops!\n"
                + "{/template}",
            "{namespace ns2}\n"
                + "import {foo} from 'no-path';"
                + "/** Public template. */\n"
                + "{template .bar}\n"
                + "{let $foo: foo /}\n"
                + "{call $foo /}\n"
                + "{/template}")
        .errorReporter(errorReporter)
        .parse();
    assertThat(errorReporter.getErrors()).hasSize(1);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo("ns.foo has private access in no-path.");
  }

  // There was a bug in the visibility pass where you could call private templates if the caller was
  // defined in a file with the same name irrespective of directory
  @Test
  public void testCallPrivateTemplateSameFileNameDifferentDirectory() {
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    SoyFileSetParserBuilder.forSuppliers(
            SoyFileSupplier.Factory.create(
                "{namespace ns}\n"
                    + "/** Private template. */\n"
                    + "{template .foo visibility=\"private\"}\n"
                    + "oops!\n"
                    + "{/template}",
                SourceFilePath.create("foo/bar.soy")),
            SoyFileSupplier.Factory.create(
                "{namespace ns2}\n"
                    + "import {foo} from 'foo/bar.soy';"
                    + "/** Public template. */\n"
                    + "{template .bar}\n"
                    + "{call foo /}\n"
                    + "{/template}",
                SourceFilePath.create("baz/bar.soy")))
        .errorReporter(errorReporter)
        .parse();
    assertThat(errorReporter.getErrors()).hasSize(1);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo("ns.foo has private access in foo/bar.soy.");
  }

  @Test
  public void testBindPrivateTemplateSameFileNameDifferentDirectory() {
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    SoyFileSetParserBuilder.forSuppliers(
            SoyFileSupplier.Factory.create(
                "{namespace ns}\n"
                    + "/** Private template. */\n"
                    + "{template .foo visibility=\"private\"}\n"
                    + "oops!\n"
                    + "{/template}",
                SourceFilePath.create("foo/bar.soy")),
            SoyFileSupplier.Factory.create(
                "{namespace ns2}\n"
                    + "import {foo} from 'foo/bar.soy';"
                    + "/** Public template. */\n"
                    + "{template .bar}\n"
                    + "{let $foo: foo /}\n"
                    + "{call $foo /}\n"
                    + "{/template}",
                SourceFilePath.create("baz/bar.soy")))
        .errorReporter(errorReporter)
        .parse();
    assertThat(errorReporter.getErrors()).hasSize(1);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo("ns.foo has private access in foo/bar.soy.");
  }
}
