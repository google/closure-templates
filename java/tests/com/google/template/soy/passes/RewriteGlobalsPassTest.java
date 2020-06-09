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

package com.google.template.soy.passes;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RewriteGlobalsPass}. */
@RunWith(JUnit4.class)
public final class RewriteGlobalsPassTest {

  @Test
  public void testResolveAlias() {
    String template =
        ""
            + "{namespace ns}\n"
            + "\n"
            + "{alias foo.bar.baz as global}\n"
            + "{alias global.with.sugar}\n"
            + "\n"
            + "{template .t}\n"
            + "  {global}\n"
            + "  {global.with.field}\n"
            + "  {sugar}\n"
            + "  {sugar.with.field}\n"
            + "  {unregistered}\n"
            + "{/template}";
    SoyFileSetNode soytree =
        SoyFileSetParserBuilder.forFileContents(template)
            .allowUnboundGlobals(true)
            .parse()
            .fileSet();

    ImmutableList.Builder<String> actual = ImmutableList.builder();
    for (SoyNode child : ((TemplateNode) soytree.getChild(0).getChild(0)).getChildren()) {
      PrintNode printNode = (PrintNode) child;
      GlobalNode global = (GlobalNode) printNode.getExpr().getRoot();
      actual.add(global.getName());
    }

    assertThat(actual.build())
        .containsExactly(
            "foo.bar.baz",
            "foo.bar.baz.with.field",
            "foo.bar.baz.with.sugar",
            "foo.bar.baz.with.sugar.with.field",
            "unregistered")
        .inOrder();
  }
}
