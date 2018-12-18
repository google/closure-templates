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
package com.google.template.soy.passes;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.IncrementingIdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.VeLiteralNode;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.soyparse.SoyFileParser;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import java.io.StringReader;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class EnforceExperimentalFeaturesPassTest {

  @Test
  public void testVeSyntax() {
    String fileContent =
        ""
            + "{namespace boo.foo}\n"
            + "\n"
            + "{template .main}\n"
            + "  {ve(TestVe)}\n"
            + "{/template}\n";

    AssertionError e = assertThrows(AssertionError.class, () -> parseSoyFile(fileContent));
    assertThat(e)
        .hasMessageThat()
        .contains("Dynamic VE features are not available for general use.");

    SoyFileNode soyTree = parseSoyFile(fileContent, ImmutableSet.of("dynamic_ve"));

    List<VeLiteralNode> veNodes = SoyTreeUtils.getAllNodesOfType(soyTree, VeLiteralNode.class);

    assertThat(veNodes).hasSize(1);

    VeLiteralNode veNode = veNodes.get(0);
    assertThat(veNode.getName().identifier()).isEqualTo("TestVe");

    assertThat(veNode.toSourceString()).isEqualTo("ve(TestVe)");
  }

  @Test
  public void testVeDataSyntax() {
    String fileContent =
        ""
            + "{namespace boo.foo}\n"
            + "\n"
            + "{template .main}\n"
            + "  {ve_data(ve(TestVe), soy.test.Foo())}\n"
            + "{/template}\n";

    AssertionError e = assertThrows(AssertionError.class, () -> parseSoyFile(fileContent));
    assertThat(e)
        .hasMessageThat()
        .contains("Dynamic VE features are not available for general use.");

    SoyFileNode soyTree = parseSoyFile(fileContent, ImmutableSet.of("dynamic_ve"));

    List<FunctionNode> functionNodes = SoyTreeUtils.getAllNodesOfType(soyTree, FunctionNode.class);

    assertThat(functionNodes).hasSize(2);

    FunctionNode veDataNode = functionNodes.get(0);
    assertThat(veDataNode.getFunctionName()).isEqualTo(BuiltinFunction.VE_DATA.getName());
    assertThat(veDataNode.numChildren()).isEqualTo(2);
    assertThat(((VeLiteralNode) veDataNode.getChild(0)).getName().identifier()).isEqualTo("TestVe");
    assertThat(((FunctionNode) veDataNode.getChild(1)).getFunctionName()).isEqualTo("soy.test.Foo");

    assertThat(veDataNode.toSourceString()).isEqualTo("ve_data(ve(TestVe), soy.test.Foo())");

    // nullary proto inits are parsed as function nodes until a later pass rewrites them
    FunctionNode protoInit = functionNodes.get(1);
    assertThat(protoInit.getFunctionName()).isEqualTo("soy.test.Foo");
  }

  SoyFileNode parseSoyFile(String file) {
    return parseSoyFile(file, ImmutableSet.of());
  }

  SoyFileNode parseSoyFile(String file, ImmutableSet<String> features) {
    IdGenerator idGen = new IncrementingIdGenerator();
    SoyFileNode node =
        new SoyFileParser(idGen, new StringReader(file), "test.soy", ErrorReporter.exploding())
            .parseSoyFile();
    new EnforceExperimentalFeaturesPass(features, ErrorReporter.exploding()).run(node, idGen);
    return node;
  }

  <T extends Throwable> T assertThrows(Class<T> exceptionClass, Callable<?> callable) {
    try {
      callable.call();
    } catch (Throwable t) {
      assertThat(t).isInstanceOf(exceptionClass);
      return exceptionClass.cast(t);
    }
    throw new AssertionError("expected: " + callable + " to throw a " + exceptionClass);
  }
}
