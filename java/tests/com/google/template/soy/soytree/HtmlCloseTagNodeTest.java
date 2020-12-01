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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.soytree.HtmlTagNode.TagExistence;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class HtmlCloseTagNodeTest {

  @Test
  public void testToSourceString() {
    RawTextNode node = new RawTextNode(0, "div", SourceLocation.UNKNOWN);
    HtmlCloseTagNode closeTag =
        new HtmlCloseTagNode(1, node, SourceLocation.UNKNOWN, TagExistence.IN_TEMPLATE);
    closeTag.addChild(node);
    assertThat(closeTag.toSourceString()).isEqualTo("</div>");
    PrintNode dynamicTagName =
        new PrintNode(
            2,
            SourceLocation.UNKNOWN,
            true,
            new VarRefNode("$tag", SourceLocation.UNKNOWN, null),
            ImmutableList.of(),
            ErrorReporter.exploding());
    closeTag =
        new HtmlCloseTagNode(1, dynamicTagName, SourceLocation.UNKNOWN, TagExistence.IN_TEMPLATE);
    closeTag.addChild(dynamicTagName);
    assertThat(closeTag.toSourceString()).isEqualTo("</{$tag}>");
  }
}
