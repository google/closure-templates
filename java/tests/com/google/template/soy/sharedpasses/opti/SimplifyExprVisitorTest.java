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

import com.google.common.collect.ImmutableMap;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.common.truth.Truth;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.template.soy.SoyModule;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.passes.ResolveFunctionsVisitor;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.soyparse.SoyFileParser;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link SimplifyExprVisitor}.
 *
 */
@RunWith(JUnit4.class)
public final class SimplifyExprVisitorTest {

  @Test
  public void testSimplifyFullySimplifiableExpr() {
    assertThat("-99+-111").simplifiesTo("-210");
    assertThat("-99+-111").simplifiesTo("-210");
    assertThat("-99 + '-111'").simplifiesTo("'-99-111'");
    assertThat("false or 0 or 0.0 or ''").simplifiesTo("''");
    assertThat("0 <= 0").simplifiesTo("true");
    assertThat("'22' == 22").simplifiesTo("true");
    assertThat("'22' == '' + 22").simplifiesTo("true");

    // With functions.
    assertThat("max(4, 8)").simplifiesTo("8");
    assertThat("floor(7/2)").simplifiesTo("3");
  }

  @Test
  public void testSimplifyNotSimplifiableExpr() {
    assertThat("$boo").simplifiesTo("$boo");
    assertThat("$boo % 3").simplifiesTo("$boo % 3");
    assertThat("not $boo").simplifiesTo("not $boo");
    assertThat("$boo + ''").simplifiesTo("$boo + ''");

    // With functions.
    assertThat("max(4, $boo)").simplifiesTo("max(4, $boo)");
    assertThat("floor($boo / 3)").simplifiesTo("floor($boo / 3)");
  }

  @Test
  public void testSimplifyPartiallySimplifiableExpr() {
    assertThat("3 * 5 % $boo").simplifiesTo("15 % $boo");
    assertThat("not false and not $boo").simplifiesTo("not $boo");
    assertThat("'a' + 'b' + $boo").simplifiesTo("'ab' + $boo");

    // With functions.
    assertThat("max(max(4, 8), $boo)").simplifiesTo("max(8, $boo)");
    assertThat("floor($boo / (1.0 + 2))").simplifiesTo("floor($boo / 3.0)");
  }

  @Test
  public void testSimplifyListAndMapLiterals() {
    assertThat("['a' + 'b', 1 - 3]").simplifiesTo("['ab', -2]");
    assertThat("['a' + 'b': 1 - 3]").simplifiesTo("['ab': -2]");
    assertThat("[8, ['a' + 'b', 1 - 3]]").simplifiesTo("[8, ['ab', -2]]");
    assertThat("['z': ['a' + 'b': 1 - 3]]").simplifiesTo("['z': ['ab': -2]]");

    // With functions.
    // Note: Currently, ListLiteralNode and MapLiteralNode are never considered to be constant,
    // even though in reality, they can be constant. So in the current implementation, this keys()
    // call cannot be simplified away.
    assertThat("keys(['a' + 'b': 1 - 3])").simplifiesTo("keys(['ab': -2])");
  }

  @Test
  public void testSimplifyBinaryLogicalOps() {
    // 'and'
    assertThat("true and true").simplifiesTo("true");
    assertThat("true and false").simplifiesTo("false");
    assertThat("false and true").simplifiesTo("false");
    assertThat("false and false").simplifiesTo("false");
    assertThat("true and $boo").simplifiesTo("$boo");
    assertThat("$boo and true").simplifiesTo("$boo and true"); // Can't simplify
    assertThat("true and 1").simplifiesTo("1");
    assertThat("1 and true").simplifiesTo("true");
    assertThat("false and 1").simplifiesTo("false");
    assertThat("1 and false").simplifiesTo("false");
    assertThat("false and $boo").simplifiesTo("false");
    assertThat("$boo and false").simplifiesTo("$boo and false"); // Can't simplify

    // 'or'
    assertThat("true or true").simplifiesTo("true");
    assertThat("true or false").simplifiesTo("true");
    assertThat("false or true").simplifiesTo("true");
    assertThat("false or false").simplifiesTo("false");
    assertThat("true or $boo").simplifiesTo("true");
    assertThat("$boo or true").simplifiesTo("$boo or true"); // Can't simplify
    assertThat("false or $boo").simplifiesTo("$boo");
    assertThat("$boo or false").simplifiesTo("$boo or false");
    assertThat("false or 1").simplifiesTo("1");
    assertThat("1 or false").simplifiesTo("1");
    assertThat("true or 1").simplifiesTo("true");
    assertThat("1 or true").simplifiesTo("1");
  }

  @Test
  public void testSimplifyConditionalOp() {
    assertThat("true ? 111 : 222").simplifiesTo("111");
    assertThat("false ? 111 : 222").simplifiesTo("222");
    assertThat("true ? 111 : $boo").simplifiesTo("111");
    assertThat("false ? $boo : 222").simplifiesTo("222");
    assertThat("$boo or true ? $boo and false : true")
        .simplifiesTo("$boo or true ? $boo and false : true"); // Can't simplify
  }

  // -----------------------------------------------------------------------------------------------
  // Helpers.

  private static final Injector INJECTOR = Guice.createInjector(new SoyModule());

  private static final ImmutableMap<String, ? extends SoyFunction> SOY_FUNCTIONS =
      INJECTOR.getInstance(new Key<ImmutableMap<String, ? extends SoyFunction>>() {});

  private static final class SimplifySubject extends Subject<SimplifySubject, String> {
    private SimplifySubject(FailureStrategy failureStrategy, String s) {
      super(failureStrategy, s);
    }

    private void simplifiesTo(String expected) {
      ExprRootNode exprRoot = new ExprRootNode(SoyFileParser.parseExprOrDie(actual()));
      new ResolveFunctionsVisitor(SOY_FUNCTIONS).exec(exprRoot);
      new SimplifyExprVisitor(new PreevalVisitorFactory(SoyValueConverter.UNCUSTOMIZED_INSTANCE))
          .exec(exprRoot);
      Truth.assertThat(exprRoot.toSourceString()).isEqualTo(expected);
    }
  }

  private static final SubjectFactory<SimplifySubject, String> FACTORY =
      new SubjectFactory<SimplifySubject, String>() {
        @Override
        public SimplifySubject getSubject(FailureStrategy failureStrategy, String s) {
          return new SimplifySubject(failureStrategy, s);
        }
      };

  private static SimplifySubject assertThat(String input) {
    return Truth.assertAbout(FACTORY).that(input);
  }
}
