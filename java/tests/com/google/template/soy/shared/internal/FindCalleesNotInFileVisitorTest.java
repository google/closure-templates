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

package com.google.template.soy.shared.internal;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link FindCalleesNotInFileVisitor}.
 *
 */
@RunWith(JUnit4.class)
public final class FindCalleesNotInFileVisitorTest {

  @Test
  public void testFindCalleesNotInFile() {
    String testFileContent =
        ""
            + "{namespace boo.foo autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/** Test template 1. */\n"
            + "{template .goo}\n"
            + "  {call .goo data=\"all\" /}\n"
            + "  {call .moo data=\"all\" /}\n"
            + "  {call boo.woo.hoo data=\"all\" /}\n"
            + // not defined in this file
            "{/template}\n"
            + "\n"
            + "/** Test template 2. */\n"
            + "{template .moo}\n"
            + "  {for $i in range(8)}\n"
            + "    {call boo.foo.goo data=\"all\" /}\n"
            + "    {call .too data=\"all\" /}\n"
            + // not defined in this file
            "    {call .goo}"
            + "      {param a}{call .moo /}{/param}"
            + "      {param b}{call .zoo /}{/param}"
            + // not defined in this file
            "    {/call}"
            + "  {/for}\n"
            + "{/template}\n"
            + "\n"
            + "/** Test template 3. */\n"
            + "{deltemplate booHoo}\n"
            + "  {call .goo data=\"all\" /}\n"
            + "  {call .moo data=\"all\" /}\n"
            + "  {call boo.hoo.roo data=\"all\" /}\n"
            + // not defined in this file
            "{/deltemplate}\n";

    ErrorReporter boom = ErrorReporter.exploding();
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(testFileContent)
            .errorReporter(boom)
            .parse()
            .fileSet();
    SoyFileNode soyFile = soyTree.getChild(0);

    Iterable<String> calleesNotInFile =
        Iterables.transform(
            new FindCalleesNotInFileVisitor().exec(soyFile),
            new Function<CallBasicNode, String>() {
              @Override
              public String apply(CallBasicNode node) {
                return node.getCalleeName();
              }
            });
    assertThat(calleesNotInFile)
        .containsExactly("boo.woo.hoo", "boo.foo.too", "boo.foo.zoo", "boo.hoo.roo");
  }
}
