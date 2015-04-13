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
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.error.TransitionalThrowingErrorReporter;
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
    TransitionalThrowingErrorReporter errorReporter = new TransitionalThrowingErrorReporter();
    ExprNode root = new ExpressionParser(getSubject(), SourceLocation.UNKNOWN, errorReporter)
        .parseExpression();
    errorReporter.throwIfErrorsPresent();
    Truth.assertThat(root).isInstanceOf(clazz);
  }

  void isNotValidExpression() {
    TransitionalThrowingErrorReporter errorReporter = new TransitionalThrowingErrorReporter();
    new ExpressionParser(getSubject(), SourceLocation.UNKNOWN, errorReporter).parseExpression();
    try {
      errorReporter.throwIfErrorsPresent();
    } catch (SoySyntaxException e) {
      return; // passes
    }
    fail("is an invalid expression");
  }

  void isNotValidDataRef() {
    TransitionalThrowingErrorReporter errorReporter = new TransitionalThrowingErrorReporter();
    new ExpressionParser(getSubject(), SourceLocation.UNKNOWN, errorReporter).parseDataReference();
    try {
      errorReporter.throwIfErrorsPresent();
    } catch (SoySyntaxException e) {
      return; // passes
    }
    fail("is an invalid data ref");
  }

  void isNotValidExpressionList() {
    TransitionalThrowingErrorReporter errorReporter = new TransitionalThrowingErrorReporter();
    new ExpressionParser(getSubject(), SourceLocation.UNKNOWN, errorReporter).parseExpressionList();
    try {
      errorReporter.throwIfErrorsPresent();
    } catch (SoySyntaxException e) {
      return; // passes
    }
    fail("is an invalid expression list");
  }

  void isNotValidGlobal() {
    TransitionalThrowingErrorReporter errorReporter = new TransitionalThrowingErrorReporter();
    new ExpressionParser(getSubject(), SourceLocation.UNKNOWN, errorReporter).parseGlobal();
    try {
      errorReporter.throwIfErrorsPresent();
    } catch (SoySyntaxException e) {
      return; // passes
    }
    fail("is an invalid global");
  }

  void isNotValidVar() {
    TransitionalThrowingErrorReporter errorReporter = new TransitionalThrowingErrorReporter();
    new ExpressionParser(getSubject(), SourceLocation.UNKNOWN, errorReporter).parseVariable();
    try {
      errorReporter.throwIfErrorsPresent();
    } catch (SoySyntaxException e) {
      return; // passes
    }
    fail("is an invalid var");
  }

  ExprNode isValidExpression() {
    TransitionalThrowingErrorReporter errorReporter = new TransitionalThrowingErrorReporter();
    ExprNode rootNode = new ExpressionParser(getSubject(), SourceLocation.UNKNOWN, errorReporter)
        .parseExpression();
    errorReporter.throwIfErrorsPresent();
    return rootNode;
  }

  ExprNode isValidDataRef() {
    TransitionalThrowingErrorReporter errorReporter = new TransitionalThrowingErrorReporter();
    ExprNode exprNode = new ExpressionParser(getSubject(), SourceLocation.UNKNOWN, errorReporter)
        .parseDataReference();
    errorReporter.throwIfErrorsPresent();
    return exprNode;
  }

  List<ExprNode> isValidExpressionList() {
    TransitionalThrowingErrorReporter errorReporter = new TransitionalThrowingErrorReporter();
    List<ExprNode> exprList = 
        new ExpressionParser(getSubject(), SourceLocation.UNKNOWN, errorReporter)
            .parseExpressionList();
    errorReporter.throwIfErrorsPresent();
    return exprList;
  }

  void isValidGlobal() {
    TransitionalThrowingErrorReporter errorReporter = new TransitionalThrowingErrorReporter();
    new ExpressionParser(getSubject(), SourceLocation.UNKNOWN, errorReporter).parseGlobal();
    errorReporter.throwIfErrorsPresent();
  }

  void isValidGlobalNamed(String name) {
    TransitionalThrowingErrorReporter errorReporter = new TransitionalThrowingErrorReporter();
    GlobalNode globalNode
        = new ExpressionParser(getSubject(), SourceLocation.UNKNOWN, errorReporter)
        .parseGlobal();
    errorReporter.throwIfErrorsPresent();
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
    TransitionalThrowingErrorReporter errorReporter = new TransitionalThrowingErrorReporter();
    new ExpressionParser(getSubject(), SourceLocation.UNKNOWN, errorReporter).parseVariable();
    errorReporter.throwIfErrorsPresent();
  }

  void isValidVarNamed(String name) {
    TransitionalThrowingErrorReporter errorReporter = new TransitionalThrowingErrorReporter();
    VarNode varNode = new ExpressionParser(getSubject(), SourceLocation.UNKNOWN, errorReporter)
        .parseVariable();
    errorReporter.throwIfErrorsPresent();
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
}
