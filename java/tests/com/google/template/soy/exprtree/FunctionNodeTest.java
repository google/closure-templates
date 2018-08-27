/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.exprtree;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.basicfunctions.RoundFunction;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for FunctionNode.
 *
 */
@RunWith(JUnit4.class)
public final class FunctionNodeTest {

  @Test
  public void testToSourceString() {
    FunctionNode fn =
        new FunctionNode(
            Identifier.create("round", SourceLocation.UNKNOWN),
            new RoundFunction(),
            SourceLocation.UNKNOWN);
    fn.addChild(new FloatNode(3.14159, SourceLocation.UNKNOWN));
    fn.addChild(new IntegerNode(2, SourceLocation.UNKNOWN));
    assertThat(fn.toSourceString()).isEqualTo("round(3.14159, 2)");
  }

  /**
   * Tests that {@link com.google.template.soy.passes.ResolveFunctionsVisitor} recurses into {@link
   * FunctionNode}s that are descendants of other FunctionNodes. (This omission caused cl/101255365
   * to be rolled back.)
   */
  @Test
  public void testResolveFunctionsVisitor() {
    SoyFunction foo =
        new SoyFunction() {
          @Override
          public String getName() {
            return "foo";
          }

          @Override
          public Set<Integer> getValidArgsSizes() {
            return ImmutableSet.of(1);
          }
        };

    SoyFunction bar =
        new SoyFunction() {
          @Override
          public String getName() {
            return "bar";
          }

          @Override
          public Set<Integer> getValidArgsSizes() {
            return ImmutableSet.of(1);
          }
        };

    SoyFileSetNode root =
        SoyFileSetParserBuilder.forTemplateContents("<a class=\"{foo(bar(1))}\">")
            .addSoyFunction(foo)
            .addSoyFunction(bar)
            .parse()
            .fileSet();
    List<FunctionNode> functionNodes = SoyTreeUtils.getAllNodesOfType(root, FunctionNode.class);
    assertThat(functionNodes).hasSize(2);
    assertThat(functionNodes.get(0).getSoyFunction()).isSameAs(foo);
    assertThat(functionNodes.get(1).getSoyFunction()).isSameAs(bar);
  }
}
