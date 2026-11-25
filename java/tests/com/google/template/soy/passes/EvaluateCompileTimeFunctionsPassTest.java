/*
 * Copyright 2026 Google Inc.
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

import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.shared.internal.SoyCompileTimeFunction;
import com.google.template.soy.shared.internal.SoyCompilerContext;
import com.google.template.soy.shared.internal.SoyCompilerContext.OutputTarget;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link EvaluateCompileTimeFunctionsPass}. */
@RunWith(JUnit4.class)
public final class EvaluateCompileTimeFunctionsPassTest {

  @Test
  public void testDoesNotEvaluateOrPruneWhenOutputTargetIsNone() {
    SoyFileSetNode fileSet =
        SoyFileSetParserBuilder.forTemplateContents("{if soyDefineIsSsr()}server{else}client{/if}")
            .runOptimizer(true)
            .outputTarget(OutputTarget.NONE)
            .parse()
            .fileSet();
    assertThat(hasCompileTimeFunctionCall(fileSet)).isTrue();
    String source = fileSet.getChild(0).getTemplates().get(0).toSourceString();
    assertThat(source).contains("server");
    assertThat(source).contains("client");
  }

  @Test
  public void testAllowedInParamDefaultWithoutSoyPureFunction() {
    SoyFileSetNode ssrFileSet =
        SoyFileSetParserBuilder.forTemplateContents("{@param p := soyDefineIsSsr()}\n{$p}")
            .runOptimizer(true)
            .outputTarget(OutputTarget.JBCSRC)
            .parse()
            .fileSet();
    assertThat(hasCompileTimeFunctionCall(ssrFileSet)).isFalse();
    assertThat(
            ssrFileSet
                .getChild(0)
                .getTemplates()
                .get(0)
                .getParams()
                .get(0)
                .defaultValue()
                .toSourceString())
        .isEqualTo("true");
  }

  @Test
  public void testEvaluatesUnannotatedCompileTimeFunctionWithConstantArg() {
    SoyFileSetNode fileSet =
        SoyFileSetParserBuilder.forTemplateContents("{testCompileTime('hello')}")
            .addSoySourceFunction(new TestCompileTimeFunction())
            .runOptimizer(false)
            .outputTarget(OutputTarget.JBCSRC)
            .parse()
            .fileSet();
    assertThat(hasCompileTimeFunctionCall(fileSet)).isFalse();
    assertThat(fileSet.getChild(0).getTemplates().get(0).toSourceString())
        .contains("'JBCSRC:hello'");
  }

  private static boolean hasCompileTimeFunctionCall(SoyFileSetNode fileSet) {
    return SoyTreeUtils.allNodesOfType(fileSet, FunctionNode.class)
        .anyMatch(fn -> fn.isResolved() && fn.getSoyFunction() instanceof SoyCompileTimeFunction);
  }

  @SoyFunctionSignature(
      name = "testCompileTime",
      value =
          @Signature(
              parameterTypes = {"string"},
              returnType = "string"))
  private static final class TestCompileTimeFunction implements SoyCompileTimeFunction {

    @Override
    public SoyValue eval(List<SoyValue> args, SoyCompilerContext context) {
      return StringData.forValue(context.outputTarget() + ":" + args.get(0).stringValue());
    }
  }
}
