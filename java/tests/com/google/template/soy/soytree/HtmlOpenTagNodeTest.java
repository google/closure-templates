/*
 * Copyright 2016 Google Inc.
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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.IncrementingIdGenerator;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.passes.CombineConsecutiveRawTextNodesVisitor;
import com.google.template.soy.passes.HtmlRewritePass;
import com.google.template.soy.soyparse.SoyFileParser;
import com.google.template.soy.types.SoyTypeRegistry;
import java.io.StringReader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class HtmlOpenTagNodeTest {

  @Test
  public void testTag() {
    HtmlOpenTagNode openTag = parseTag("<div>");
    assertThat(openTag.toSourceString()).isEqualTo("<div>");
    assertThat(openTag.isSelfClosing()).isFalse();

    openTag = parseTag("<div class=\"foo\">");
    assertThat(openTag.toSourceString()).isEqualTo("<div class=\"foo\">");
    assertThat(openTag.isSelfClosing()).isFalse();
    assertThat(openTag.getTagName().getNode()).isInstanceOf(RawTextNode.class);
    assertThat(openTag.numChildren()).isEqualTo(1);
  }

  @Test
  public void testToSourceString_preserveCasing() {
    HtmlOpenTagNode openTag = parseTag("<DiV>");
    assertThat(openTag.toSourceString()).isEqualTo("<DiV>");
    assertThat(openTag.isSelfClosing()).isFalse();
    assertThat(openTag.getTagName().getStaticTagNameAsLowerCase()).hasValue("div");
  }

  @Test
  public void testToSourceString_selfClosing() {
    HtmlOpenTagNode openTag = parseTag("<input />");
    assertThat(openTag.toSourceString()).isEqualTo("<input/>");
    assertThat(openTag.isSelfClosing()).isTrue();
  }

  private static HtmlOpenTagNode parseTag(String input) {
    String soyFile = Joiner.on('\n').join("{namespace ns}", "{template .t}", input, "{/template}");
    IncrementingIdGenerator nodeIdGen = new IncrementingIdGenerator();
    SoyFileNode node =
        new SoyFileParser(
                new SoyTypeRegistry(),
                nodeIdGen,
                new StringReader(soyFile),
                SoyFileKind.SRC,
                "test.soy",
                ExplodingErrorReporter.get())
            .parseSoyFile();
    new HtmlRewritePass(ImmutableList.of("stricthtml"), ExplodingErrorReporter.get())
        .run(node, nodeIdGen);
    new CombineConsecutiveRawTextNodesVisitor(nodeIdGen).exec(node);
    return (HtmlOpenTagNode) node.getChild(0).getChild(0);
  }
}
