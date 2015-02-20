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

import com.google.common.collect.ImmutableList;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.template.soy.pysrc.internal.GenPyExprsVisitor.GenPyExprsVisitorFactory;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.shared.SharedTestUtils;
import com.google.template.soy.soyparse.ParseResult;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;

import java.util.List;

/**
 * Truth assertion which compiles the provided soy code and asserts that the generated PyExprs match
 * the expected expressions.
 *
 */
public final class SoyCodeForPySubject extends Subject<SoyCodeForPySubject, String> {

  private static final Injector INJECTOR = Guice.createInjector(new PySrcModule());

  private GenPyExprsVisitor genPyExprsVisitor;

  SoyCodeForPySubject(FailureStrategy failureStrategy, String code) {
    super(failureStrategy, code);

    SharedTestUtils.simulateNewApiCall(INJECTOR, null, null);
    genPyExprsVisitor = INJECTOR.getInstance(GenPyExprsVisitorFactory.class).create();
  }

  /**
   * Assert soy code compiled to the correct list of PyExprs.
   * The given piece of Soy code is wrapped in a full body of a template.
   * The actual result is replaced with ids for ### so that tests don't break when ids change.
   */
  public void compilesTo(List<PyExpr> expectedPyExprs) {
    ParseResult<SoyFileSetNode> result = SharedTestUtils.parseSoyCode(getSubject());
    SoyNode node = SharedTestUtils.getNode(result.getParseTree(), 0);

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
   * Assert soy code compiled to the correct PyExpr.
   */
  public void compilesTo(PyExpr expectedPyExpr) {
    compilesTo(ImmutableList.of(expectedPyExpr));
  }

  private static final SubjectFactory<SoyCodeForPySubject, String> SOYCODE =
      new SubjectFactory<SoyCodeForPySubject, String>() {
        @Override
        public SoyCodeForPySubject getSubject(FailureStrategy failureStrategy, String code) {
          return new SoyCodeForPySubject(failureStrategy, code);
        }
      };

  public static SoyCodeForPySubject assertThatSoyCode(String code) {
    return assertAbout(SOYCODE).that(code);
  }
}
