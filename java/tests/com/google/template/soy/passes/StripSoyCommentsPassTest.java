/*
 * Copyright 2019 Google Inc.
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
import static com.google.template.soy.soytree.SoyTreeUtils.getAllNodesOfType;

import com.google.common.io.CharSource;
import com.google.template.soy.base.internal.IncrementingIdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.soyparse.SoyFileParser;
import com.google.template.soy.soytree.LineCommentNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class StripSoyCommentsPassTest {

  @Test
  public void testCommentsStripped() throws Exception {
    String testFileContent =
        "{namespace boo}\n"
            + "{template .foo}\n"
            + "{let $base: 'http://www.google.com' /}\n"
            + " // line comments\n"
            + "{let $query: '?foo=bar&baz=boo' /}\n"
            + " // should handle maybeNewline()\n"
            + "{let $url kind='uri'}\n"
            + "  // Appending a query string including the question mark is valid, though\n"
            + "  // frowned upon, and may later become unsupported.\n"
            + "  {$base}/{$query}\n"
            + "{/let}\n"
            + "{/template}";

    SoyFileNode soyFile =
        new SoyFileParser(
                new IncrementingIdGenerator(),
                CharSource.wrap(testFileContent).openStream(),
                "testCommentsStripped.soy",
                ErrorReporter.createForTest())
            .parseSoyFile();

    assertThat(getAllNodesOfType(soyFile, LineCommentNode.class)).hasSize(4);

    new StripSoyCommentsPass().run(soyFile, new IncrementingIdGenerator());

    assertThat(getAllNodesOfType(soyFile, LineCommentNode.class)).isEmpty();
  }

  @Test
  public void testNil() throws Exception {
    String testFileContent =
        "{namespace boo}\n"
            + "{template .foo}\n"
            + "any{nil}\n"
            + " // line comments\n"
            + "how\n"
            + "{/template}";

    SoyFileNode soyFile =
        new SoyFileParser(
                new IncrementingIdGenerator(),
                CharSource.wrap(testFileContent).openStream(),
                "testNil.soy",
                ErrorReporter.createForTest())
            .parseSoyFile();

    assertThat(getAllNodesOfType(soyFile, LineCommentNode.class)).hasSize(1);
    assertThat(getAllNodesOfType(soyFile, RawTextNode.class)).hasSize(2);

    new StripSoyCommentsPass().run(soyFile, new IncrementingIdGenerator());

    assertThat(getAllNodesOfType(soyFile, LineCommentNode.class)).isEmpty();
    assertThat(getAllNodesOfType(soyFile, RawTextNode.class)).hasSize(2);
  }
}
