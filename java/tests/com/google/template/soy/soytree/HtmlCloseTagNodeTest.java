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

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.exprparse.SoyParsingContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class HtmlCloseTagNodeTest {

  @Test
  public void testToSourceString() {
    HtmlCloseTagNode closeTag =
        new HtmlCloseTagNode(
            1,
            new TagName(new RawTextNode(0, "div", SourceLocation.UNKNOWN)),
            SourceLocation.UNKNOWN);
    assertThat(closeTag.toSourceString()).isEqualTo("</div>");
    PrintNode dynamicTagName =
        new PrintNode.Builder(2, true, SourceLocation.UNKNOWN)
            .exprText("$tag")
            .build(SoyParsingContext.exploding());
    closeTag = new HtmlCloseTagNode(1, new TagName(dynamicTagName), SourceLocation.UNKNOWN);
    assertThat(closeTag.toSourceString()).isEqualTo("</{$tag}>");
  }
}
