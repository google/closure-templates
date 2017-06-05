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

package com.google.template.soy.pysrc.internal;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.google.template.soy.shared.SharedTestUtils.untypedTemplateBodyForExpression;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.SoyModule;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.pysrc.internal.GenPyExprsVisitor.GenPyExprsVisitorFactory;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyExprUtils;
import com.google.template.soy.shared.SharedTestUtils;
import com.google.template.soy.shared.SoyGeneralOptions;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import java.util.List;
import java.util.Map;

/**
 * Truth assertion which compiles the provided soy code and asserts that the generated PyExprs match
 * the expected expressions. This subject is only valid for soy code which can be represented as one
 * or more Python expressions.
 *
 */
public final class SoyExprForPySubject extends Subject<SoyExprForPySubject, String> {

  // disable optimizer for backwards compatibility
  private final SoyGeneralOptions opts = new SoyGeneralOptions().disableOptimizer();

  private final LocalVariableStack localVarExprs;

  private final Injector injector;

  private SoyExprForPySubject(FailureStrategy failureStrategy, String expr) {
    super(failureStrategy, expr);
    localVarExprs = new LocalVariableStack();
    injector = Guice.createInjector(new SoyModule());
  }

  /**
   * Adds a frame of local variables to the top of the {@link LocalVariableStack}.
   *
   * @param localVarFrame one frame of local variables
   * @return the current subject for chaining
   */
  public SoyExprForPySubject with(Map<String, PyExpr> localVarFrame) {
    localVarExprs.pushFrame();
    for (Map.Entry<String, PyExpr> entry : localVarFrame.entrySet()) {
      localVarExprs.addVariable(entry.getKey(), entry.getValue());
    }
    return this;
  }

  /**
   * Sets a map of key to {@link com.google.template.soy.data.restricted.PrimitiveData} values as
   * the current globally available data. Any compilation step will use these globals to replace
   * unrecognized variables.
   *
   * @param globals a map of keys to PrimitiveData values
   * @return the current subject for chaining
   */
  public SoyExprForPySubject withGlobals(ImmutableMap<String, ?> globals) {
    opts.setCompileTimeGlobals(globals);
    return this;
  }

  /**
   * Asserts the subject compiles to the correct PyExpr.
   *
   * @param expectedPyExpr the expected result of compilation
   */
  public void compilesTo(PyExpr expectedPyExpr) {
    compilesTo(ImmutableList.of(expectedPyExpr));
  }

  /**
   * Asserts the subject compiles to the correct list of PyExprs.
   *
   * <p>The given Soy expr is wrapped in a full body of a template. The actual result is replaced
   * with ids for ### so that tests don't break when ids change.
   *
   * @param expectedPyExprs the expected result of compilation
   */
  public void compilesTo(List<PyExpr> expectedPyExprs) {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forTemplateContents(getSubject()).parse().fileSet();
    SoyNode node = SharedTestUtils.getNode(soyTree, 0);

    SharedTestUtils.simulateNewApiCall(injector, null, BidiGlobalDir.LTR);
    GenPyExprsVisitor genPyExprsVisitor =
        injector
            .getInstance(GenPyExprsVisitorFactory.class)
            .create(localVarExprs, ExplodingErrorReporter.get());
    List<PyExpr> actualPyExprs = genPyExprsVisitor.exec(node);

    assertThat(actualPyExprs).hasSize(expectedPyExprs.size());
    for (int i = 0; i < expectedPyExprs.size(); i++) {
      PyExpr expectedPyExpr = expectedPyExprs.get(i);
      PyExpr actualPyExpr = actualPyExprs.get(i);
      assertThat(actualPyExpr.getText().replaceAll("\\([0-9]+", "(###"))
          .isEqualTo(expectedPyExpr.getText());
      assertThat(actualPyExpr.getPrecedence()).isEqualTo(expectedPyExpr.getPrecedence());
    }
  }

  /**
   * Asserts the subject translates to the expected PyExpr.
   *
   * @param expectedPyExpr the expected result of translation
   */
  public void translatesTo(PyExpr expectedPyExpr) {
    translatesTo(expectedPyExpr, null);
  }

  public void translatesTo(String expr, Operator precedence) {
    translatesTo(new PyExpr(expr, PyExprUtils.pyPrecedenceForOperator(precedence)));
  }

  public void translatesTo(String expr, int precedence) {
    translatesTo(new PyExpr(expr, precedence));
  }

  /**
   * Asserts the subject translates to the expected PyExpr including verification of the exact
   * PyExpr class (e.g. {@code PyStringExpr.class}).
   *
   * @param expectedPyExpr the expected result of translation
   * @param expectedClass the expected class of the resulting PyExpr
   */
  public void translatesTo(PyExpr expectedPyExpr, Class<? extends PyExpr> expectedClass) {

    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forTemplateContents(untypedTemplateBodyForExpression(getSubject()))
            .options(opts)
            .parse()
            .fileSet();
    PrintNode node = (PrintNode) SharedTestUtils.getNode(soyTree, 0);
    ExprNode exprNode = node.getExpr();

    PyExpr actualPyExpr =
        new TranslateToPyExprVisitor(localVarExprs, ExplodingErrorReporter.get()).exec(exprNode);
    assertThat(actualPyExpr.getText()).isEqualTo(expectedPyExpr.getText());
    assertThat(actualPyExpr.getPrecedence()).isEqualTo(expectedPyExpr.getPrecedence());

    if (expectedClass != null) {
      assertThat(actualPyExpr.getClass()).isEqualTo(expectedClass);
    }
  }

  //-----------------------------------------------------------------------------------------------
  // Public static functions for starting a SoyExprForPySubject test.

  private static final SubjectFactory<SoyExprForPySubject, String> SOYEXPR =
      new SubjectFactory<SoyExprForPySubject, String>() {
        @Override
        public SoyExprForPySubject getSubject(FailureStrategy failureStrategy, String expr) {
          return new SoyExprForPySubject(failureStrategy, expr);
        }
      };

  public static SoyExprForPySubject assertThatSoyExpr(String expr) {
    return assertAbout(SOYEXPR).that(expr);
  }
}
