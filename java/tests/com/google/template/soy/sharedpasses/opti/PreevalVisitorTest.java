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
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueConverterUtility;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.sharedpasses.render.Environment;
import com.google.template.soy.sharedpasses.render.RenderException;
import com.google.template.soy.sharedpasses.render.TestingEnvironment;
import com.google.template.soy.soytree.PrintNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for PreevalVisitor.
 *
 */
@RunWith(JUnit4.class)
public final class PreevalVisitorTest {

  @Test
  public void testPreevalNoData() {

    assertThat(preeval("-99+-111").integerValue()).isEqualTo(-210);
    assertThat(preeval("-99 + '-111'").stringValue()).isEqualTo("-99-111");
    assertThat(preeval("false ?: 0 ?: 0.0 ?: ''").booleanValue()).isFalse();
    assertThat(preeval("0 <= 0").booleanValue()).isTrue();
    assertThat(preeval("'22' == 22").booleanValue()).isTrue();
    assertThat(preeval("'22' == '' + 22").booleanValue()).isTrue();

    // With functions.
    // TODO SOON: Uncomment these tests when basic functions have been changed to SoyJavaFunction.
    //assertEquals(8, preeval("max(4, 8)").integerValue());
    //assertEquals(3, preeval("floor(7/2)").integerValue());

    // With impure function.
    try {
      preeval("randomInt(1000)");
      fail();
    } catch (RenderException re) {
      assertThat(re).hasMessageThat().isEqualTo("Cannot preevaluate impure function.");
    }
  }

  @Test
  public void testPreevalWithData() {

    assertThat(preeval("$boo", "boo").integerValue()).isEqualTo(8);
    assertThat(preeval("$boo % 3", "boo").integerValue()).isEqualTo(2);
    assertThat(preeval("not $boo ? 1 : 2", "boo").integerValue()).isEqualTo(2);
    assertThat(preeval("$boo + ''", "boo").stringValue()).isEqualTo("8");

    // With functions.
    // TODO SOON: Uncomment these tests when basic functions have been changed to SoyJavaFunction.
    //assertEquals(8, preeval("max(4, $boo)").integerValue());
    //assertEquals(2, preeval("floor($boo / 3)").integerValue());
    //assertEquals(3, preeval("round($boo / 3)").integerValue());

    // With undefined data.
    try {
      preeval("4 + $foo", "foo");
      fail();
    } catch (RenderException re) {
      // Test passes.
    }
  }

  @Test
  public void testPreevalWithIjData() {

    try {
      preeval("6 + $ij.foo");
      fail();
    } catch (RenderException re) {
      // Test passes.
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Helpers.

  /**
   * Evaluates the given expression and returns the result.
   *
   * @param expression The expression to preevaluate.
   * @return The expression result.
   */
  private static SoyValue preeval(String expression, String... params) {
    String header = "";
    for (String param : params) {
      header += "{@param " + param + " : ?}\n";
    }
    PrintNode code =
        (PrintNode)
            SoyFileSetParserBuilder.forTemplateContents(header + "{" + expression + "}")
                .parse()
                .fileSet()
                .getChild(0)
                .getChild(0)
                .getChild(0);
    ExprRootNode expr = code.getExpr();
    Environment env =
        TestingEnvironment.createForTest(
            SoyValueConverterUtility.newDict("boo", 8),
            ImmutableMap.<String, SoyValueProvider>of());

    return new PreevalVisitor(env).exec(expr);
  }
}
