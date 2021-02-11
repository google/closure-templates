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

import com.google.common.collect.Iterables;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link FindCalleesNotInFile}.
 *
 */
@RunWith(JUnit4.class)
public final class FindCalleesNotInFileTest {

  @Test
  public void testFindCalleesNotInFile() {
    String testFileContent =
        ""
            + "{namespace boo.foo}\n"
            + "import * as booHooTmp from 'no-path-2';\n"
            + "/** Test template 1. */\n"
            + "{template .goo}\n"
            + "  {call .goo data=\"all\" /}\n"
            + "  {call .moo data=\"all\" /}\n"
            + "  {call booHooTmp.hoo data=\"all\" /}\n"
            + "{/template}\n"
            + "\n"
            + "/** Test template 2. */\n"
            + "{template .moo}\n"
            + "  {for $i in range(8)}\n"
            + "    {call .goo data=\"all\" /}\n"
            + "    {call booHooTmp.too data=\"all\" /}\n"
            + "    {call .goo}"
            + "      {param a kind=\"text\"}{call .moo /}{/param}"
            + "      {param b kind=\"text\"}{call booHooTmp.zoo /}{/param}"
            + "    {/call}"
            + "  {/for}\n"
            + "{/template}\n"
            + "\n"
            + "/** Test template 3. */\n"
            + "{deltemplate booHoo}\n"
            + "  {call .goo data=\"all\" /}\n"
            + "  {call .moo data=\"all\" /}\n"
            + "  {call booHooTmp.roo data=\"all\" /}\n"
            + "{/deltemplate}\n";

    String includesContent =
        ""
            + "{namespace boo.hoo}\n"
            + "\n"
            + "{template .hoo}{@param a: ?}{@param b: ?}{/template}\n"
            + "{template .too}{/template}\n"
            + "{template .zoo}{/template}\n"
            + "{template .roo}{/template}\n";

    ErrorReporter boom = ErrorReporter.exploding();
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(testFileContent, includesContent)
            .errorReporter(boom)
            .parse()
            .fileSet();
    SoyFileNode soyFile = soyTree.getChild(0);

    Iterable<String> calleesNotInFile =
        Iterables.transform(
            FindCalleesNotInFile.findCalleesNotInFile(soyFile),
            TemplateLiteralNode::getResolvedName);
    assertThat(calleesNotInFile)
        .containsExactly("boo.hoo.hoo", "boo.hoo.too", "boo.hoo.zoo", "boo.hoo.roo");
  }
}
