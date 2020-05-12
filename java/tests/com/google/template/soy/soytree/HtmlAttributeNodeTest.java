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

package com.google.template.soy.soytree;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class HtmlAttributeNodeTest {

  @Test
  public void tetGetStaticValue() {
    HtmlOpenTagNode tag = parseTag("<link rel='foo'>");
    assertThat(tag.getDirectAttributeNamed("rel").getStaticContent()).isEqualTo("foo");
  }

  @Test
  public void tetGetStaticValue_blank1() {
    HtmlOpenTagNode tag = parseTag("<link rel>");
    assertThat(tag.getDirectAttributeNamed("rel").getStaticContent()).isEqualTo(null);
  }

  @Test
  public void tetGetStaticValue_blank2() {
    HtmlOpenTagNode tag = parseTag("<link rel=''>");
    assertThat(tag.getDirectAttributeNamed("rel").getStaticContent()).isEqualTo("");
  }

  @Test
  public void tetGetStaticValue_multipleChildren() {
    HtmlOpenTagNode tag = parseTag("<link rel='foo{1}'>");
    assertThat(tag.getDirectAttributeNamed("rel").getStaticContent()).isEqualTo(null);
  }

  @Test
  public void tetGetStaticValue_ifChild() {
    HtmlOpenTagNode tag = parseTag("<link rel={if 1}'foo'{else}'bar'{/if}>");
    assertThat(tag.getDirectAttributeNamed("rel").getStaticContent()).isEqualTo(null);
  }

  private HtmlOpenTagNode parseTag(String input) {
    String soyFile =
        Joiner.on('\n')
            .join("{namespace ns}", "{template .t stricthtml=\"false\"}", input, "{/template}");
    SoyFileNode node =
        SoyFileSetParserBuilder.forFileContents(soyFile)
            .desugarHtmlAndStateNodes(false)
            .parse()
            .fileSet()
            .getChild(0);
    return (HtmlOpenTagNode) ((TemplateNode) node.getChild(0)).getChild(0);
  }
}
