/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.soytree;

import static com.google.template.soy.soytree.TemplateRegistrySubject.assertThatRegistry;

import static com.google.common.truth.Truth.assertThat;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SoySyntaxException;

import junit.framework.TestCase;

/**
 * Tests for {@link TemplateRegistry}.
 *
 * @author brndn@google.com (Brendan Linn)
 */
public final class TemplateRegistryTest extends TestCase {

  public void testSimple() {
    TemplateRegistry registry = SoyFileSet.builder()
        .add(
            "{namespace ns}\n"
                + "/** Simple template. */\n"
                + "{template .foo}\n"
                + "{/template}\n"
                + "/** Simple deltemplate. */\n"
                + "{deltemplate bar.baz}\n"
                + "{/deltemplate}",
            "example.soy")
        .build()
        .generateTemplateRegistry();
    assertThatRegistry(registry).containsBasicTemplate("ns.foo")
        .definedAt(new SourceLocation("example.soy", 3, 1, 4, 11));
    assertThatRegistry(registry).doesNotContainBasicTemplate("foo");
    assertThatRegistry(registry).containsDelTemplate("bar.baz")
        .definedAt(new SourceLocation("example.soy", 6, 1, 7, 14));
    assertThatRegistry(registry).doesNotContainDelTemplate("ns.bar.baz");
  }

  public void testGenerateTemplateRegistryRequiresSyntaxV2() {
    try {
      SoyFileSet.builder()
          .add(
              "{namespace ns}\n"
                  + "{template .foo}\n"
                  + "{/template}\n",
              "bar.soy")
          .build()
          .generateTemplateRegistry();
      fail();
    } catch (SoySyntaxException e) {
      assertThat(e.getMessage())
          .contains("Found error where declared syntax version 2.0 is not satisfied");
    }
  }

  public void testBasicTemplatesWithSameNamesInDifferentFiles() {
    TemplateRegistry registry = SoyFileSet.builder()
        .add(
            "{namespace ns}\n"
                + "/** Template. */\n"
                + "{template .foo}\n"
                + "{/template}\n",
            "bar.soy")
        .add(
            "{namespace ns2}\n"
                + "/** Template. */\n"
                + "{template .foo}\n"
                + "{/template}\n",
            "baz.soy")
        .build()
        .generateTemplateRegistry();

    assertThatRegistry(registry).containsBasicTemplate("ns.foo")
        .definedAt(new SourceLocation("bar.soy", 3, 1, 4, 11));
    assertThatRegistry(registry).containsBasicTemplate("ns2.foo")
        .definedAt(new SourceLocation("baz.soy", 3, 1, 4, 11));
  }

  public void testDelTemplates() {
    TemplateRegistry registry = SoyFileSet.builder()
        .add(
            "{namespace ns}\n"
            + "/** Deltemplate. */\n"
            + "{deltemplate foo.bar}\n"
            + "{/deltemplate}",
            "foo.soy")
        .add("{delpackage foo}\n"
            + "{namespace ns}\n"
            + "/** Deltemplate. */\n"
            + "{deltemplate foo.bar}\n"
            + "{/deltemplate}",
            "bar.soy")
        .build()
        .generateTemplateRegistry();

    assertThatRegistry(registry).containsDelTemplate("foo.bar")
        .definedAt(new SourceLocation("foo.soy", 3, 1, 4, 14));
    assertThatRegistry(registry).containsDelTemplate("foo.bar")
        .definedAt(new SourceLocation("bar.soy", 4, 1, 5, 14));
  }

  // TODO(user): This is obviously undesirable. Fix all offending templates,
  // fix Soy to report these errors, and change this test to assert on the error.
  public void testDuplicateBasicTemplatesLastOneWins() {
    // Template uniqueness is not enforced either within a file or across files.
    String file = "{namespace ns}\n"
        + "/** Foo. */\n"
        + "{template .foo}\n"
        + "{/template}\n"
        + "/** Foo. */\n"
        + "{template .foo}\n"
        + "{/template}\n";
    TemplateRegistry registry = SoyFileSet.builder()
        .add(file, "foo.soy")
        .add(file, "bar.soy")
        .build()
        .generateTemplateRegistry();
    assertThatRegistry(registry).containsBasicTemplate("ns.foo")
        .definedAt(new SourceLocation("bar.soy", 6, 1, 7, 11)); // last one wins
  }
}
