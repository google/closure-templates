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

package com.google.template.soy.exprparse;

import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.common.truth.Truth;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.VarNode;

import java.util.List;

/**
 * Custom Truth subject for testing {@link ExpressionParser}.
 *
 * @author brndn@google.com (Brendan Linn)
 */
final class ExpressionSubject extends Subject<ExpressionSubject, String> {

  private static final SubjectFactory<ExpressionSubject, String> FACTORY
      = new SubjectFactory<ExpressionSubject, String>() {
    @Override
    public ExpressionSubject getSubject(FailureStrategy failureStrategy, String s) {
      return new ExpressionSubject(failureStrategy, s);
    }
  };

  public ExpressionSubject(FailureStrategy failureStrategy, String s) {
    super(failureStrategy, s);
  }

  static ExpressionSubject assertThatExpression(String input) {
    return Truth.assertAbout(FACTORY).that(input);
  }

  void generatesASTWithRootOfType(Class<? extends ExprNode> clazz) {
    ExprNode root = expressionParser().parseExpression();
    Truth.assertThat(root).isInstanceOf(clazz);
  }

  void isNotValidExpression() {
    try {
      expressionParser().parseExpression();
    } catch (IllegalStateException e) {
      return; // passes
    }
    fail("is an invalid expression");
  }

  void isNotValidDataRef() {
    try {
      expressionParser().parseDataReference();
    } catch (IllegalStateException e) {
      return; // passes
    }
    fail("is an invalid data ref");
  }

  void isNotValidExpressionList() {
    try {
      expressionParser().parseExpressionList();
    } catch (IllegalStateException e) {
      return; // passes
    }
    fail("is an invalid expression list");
  }

  void isNotValidGlobal() {
    try {
      expressionParser().parseGlobal();
    } catch (IllegalStateException e) {
      return; // passes
    }
    fail("is an invalid global");
  }

  void isNotValidVar() {
    try {
      expressionParser().parseVariable();
    } catch (IllegalStateException e) {
      return; // passes
    }
    fail("is an invalid var");
  }

  ExprNode isValidExpression() {
    return expressionParser().parseExpression();
  }

  ExprNode isValidDataRef() {
    return expressionParser().parseDataReference();
  }

  List<ExprNode> isValidExpressionList() {
    return expressionParser().parseExpressionList();
  }

  void isValidGlobal() {
    expressionParser().parseGlobal();
  }

  void isValidGlobalNamed(String name) {
    GlobalNode globalNode = expressionParser().parseGlobal();
    String actualName = globalNode.getName();
    Truth.assertWithMessage(
        "expected "
            + getSubject()
            + "to be a valid global with name "
            + name
            + " but got "
            + actualName)
        .that(actualName)
        .isEqualTo(name);
    Truth.assertThat(globalNode.toSourceString()).isEqualTo(name);
  }

  void isValidVar() {
    expressionParser().parseVariable();
  }

  void isValidVarNamed(String name) {
    VarNode varNode = expressionParser().parseVariable();
    String actualName = varNode.getName();
    Truth.assertWithMessage(
        "expected "
            + getSubject()
            + "to be a valid var with name "
            + name
            + " but got "
            + actualName)
        .that(actualName)
        .isEqualTo(name);
    Truth.assertThat(varNode.toSourceString()).isEqualTo("$" + name);
  }

  private ExpressionParser expressionParser() {
    return new ExpressionParser(getSubject(), SourceLocation.UNKNOWN, ExplodingErrorReporter.get());
  }
}
