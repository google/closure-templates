/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.sharedpasses.opti;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.plugin.restricted.SoySourceFunction;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.SoyFileSetNode;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PrintDirectiveRewritingTest {

  @Test
  public void testRewrite() {
    testRewritten("{hola(1)}", "{hola(1)}");
    testRewritten("{@param a : ?}{$a |hola}", "{hola($a)}");
    testRewritten("{@param a : ?}{$a |hola: 1}", "{hola($a, 1)}");
    testRewritten("{@param a : ?}{$a |hola: 1, 'x'}", "{hola($a, 1, 'x')}");
    testRewritten("{@param a : ?}{@param y : ?}{$a |hola: 1, 'x', $y}", "{hola($a, 1, 'x', $y)}");
    testRewritten(
        "{@param a : ?}{@param y : ?}{$a |hola: 1, 'x', $y, isNonnull($y)}",
        "{hola($a, 1, 'x', $y, isNonnull($y))}");
    testRewritten("{@param a : ?}{$a |hola: 1 |hola: 2}", "{hola(hola($a, 1), 2)}");
  }

  @Test
  public void testWhitelist() {
    testRewritten("{adios(1)}", "{adios(1)}");
    assertThrows(AssertionError.class, () -> simplifySoyCode("{@param a : ?}{$a |adios}"));
  }

  @Test
  public void testPrintDirectiveOrder() {
    testRewritten("{@param a : ?}{$a |hola |asi}", "{hola($a) |asi}");
    assertThrows(AssertionError.class, () -> simplifySoyCode("{@param a : ?}{$a |asi |hola}"));
  }

  private static void testRewritten(String from, String to) {
    assertThat(simplifySoyCode(from)).isEqualTo(to);
  }

  private static String simplifySoyCode(String soyCode) {
    SoyFileSetNode fileSet =
        SoyFileSetParserBuilder.forTemplateContents(soyCode)
            .addSoySourceFunction(new CallableFunction())
            .addSoySourceFunction(new NotCallableFunction())
            .addPrintDirective(new LegacyPrintDirective())
            .parse()
            .fileSet();
    SimplifyVisitor simplifyVisitor =
        SimplifyVisitor.create(
            fileSet.getNodeIdGenerator(), ImmutableList.copyOf(fileSet.getChildren()));
    simplifyVisitor.simplify(fileSet.getChild(0));
    return fileSet.getChild(0).getChild(0).getChild(0).toSourceString();
  }

  @SoyFunctionSignature(
      name = "hola",
      value = {
        @Signature(
            parameterTypes = {"?"},
            returnType = "string"),
        @Signature(
            parameterTypes = {"?", "?"},
            returnType = "string"),
        @Signature(
            parameterTypes = {"?", "?", "?"},
            returnType = "string"),
        @Signature(
            parameterTypes = {"?", "?", "?", "?"},
            returnType = "string"),
        @Signature(
            parameterTypes = {"?", "?", "?", "?", "?"},
            returnType = "string"),
      },
      callableAsDeprecatedPrintDirective = true)
  static class CallableFunction implements SoySourceFunction {}

  @SoyFunctionSignature(
      name = "adios",
      value = {
        @Signature(
            parameterTypes = {"?"},
            returnType = "string")
      },
      callableAsDeprecatedPrintDirective = false)
  static class NotCallableFunction implements SoySourceFunction {}

  static class LegacyPrintDirective implements SoyPrintDirective {

    @Override
    public String getName() {
      return "|asi";
    }

    @Override
    public Set<Integer> getValidArgsSizes() {
      return ImmutableSet.of(0);
    }
  }
}
