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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.common.truth.Truth;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.FormattingErrorReporter;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.VarRefNode;
import java.util.List;

/**
 * Custom Truth subject for testing {@link ExpressionParser}.
 *
 * @author brndn@google.com (Brendan Linn)
 */
final class ExpressionSubject extends Subject<ExpressionSubject, String> {

  private static final SubjectFactory<ExpressionSubject, String> FACTORY =
      new SubjectFactory<ExpressionSubject, String>() {
        @Override
        public ExpressionSubject getSubject(FailureStrategy failureStrategy, String s) {
          return new ExpressionSubject(failureStrategy, s);
        }
      };

  private final ImmutableMap.Builder<String, String> aliasesBuilder = ImmutableMap.builder();

  public ExpressionSubject(FailureStrategy failureStrategy, String s) {
    super(failureStrategy, s);
  }

  static ExpressionSubject assertThatExpression(String input) {
    return Truth.assertAbout(FACTORY).that(input);
  }

  void generatesASTWithRootOfType(Class<? extends ExprNode> clazz) {
    ExprNode root = isValidExpression();
    if (!clazz.isInstance(root)) {
      failWithBadResults("generates an ast with root of type", clazz, "has type", root.getClass());
    }
    Truth.assertThat(root).isInstanceOf(clazz);
  }

  void isNotValidExpression() {
    FormattingErrorReporter errorReporter = new FormattingErrorReporter();
    expressionParser(errorReporter).parseExpression();
    if (errorReporter.getErrorMessages().isEmpty()) {
      fail("is an invalid expression");
    }
  }

  void isNotValidExpressionList() {
    FormattingErrorReporter errorReporter = new FormattingErrorReporter();
    expressionParser(errorReporter).parseExpressionList();
    if (errorReporter.getErrorMessages().isEmpty()) {
      fail("is an invalid expression list");
    }
  }

  void isNotValidGlobal() {
    FormattingErrorReporter errorReporter = new FormattingErrorReporter();
    ExprNode expr = expressionParser(errorReporter).parseExpression();
    ImmutableList<String> errorMessages = errorReporter.getErrorMessages();
    if (expr instanceof GlobalNode && errorMessages.isEmpty()) {
      fail("is an invalid global");
    }
  }

  void isNotValidVar() {
    FormattingErrorReporter errorReporter = new FormattingErrorReporter();
    expressionParser(errorReporter).parseVariable();
    if (errorReporter.getErrorMessages().isEmpty()) {
      fail("is an invalid var");
    }
  }

  ExprNode isValidExpression() {
    FormattingErrorReporter errorReporter = new FormattingErrorReporter();
    ExprNode expr = expressionParser(errorReporter).parseExpression();
    if (!errorReporter.getErrorMessages().isEmpty()) {
      fail("is a valid expression", errorReporter.getErrorMessages());
    }
    return expr;
  }

  List<ExprNode> isValidExpressionList() {
    FormattingErrorReporter errorReporter = new FormattingErrorReporter();
    List<ExprNode> exprList = expressionParser(errorReporter).parseExpressionList();
    if (!errorReporter.getErrorMessages().isEmpty()) {
      fail("is a valid expression list", errorReporter.getErrorMessages());
    }
    return exprList;
  }

  void isValidGlobal() {
    FormattingErrorReporter errorReporter = new FormattingErrorReporter();
    ExprNode expr = expressionParser(errorReporter).parseExpression();
    if (!errorReporter.getErrorMessages().isEmpty()) {
      fail("is a valid global", errorReporter.getErrorMessages());
    }
    Truth.assertThat(expr).named(actualAsString()).isInstanceOf(GlobalNode.class);
  }

  void isValidGlobalNamed(String name) {
    FormattingErrorReporter errorReporter = new FormattingErrorReporter();
    GlobalNode globalNode = (GlobalNode) expressionParser(errorReporter).parseExpression();
    if (!errorReporter.getErrorMessages().isEmpty()) {
      fail("is valid global", errorReporter.getErrorMessages());
    }
    String actualName = globalNode.getName();
    if (!actualName.equals(name)) {
      failWithBadResults("is global named", name, "has name", actualName);
    }
    String actualSourceString = globalNode.toSourceString();
    if (!actualSourceString.equals(name)) {
      failWithBadResults("is global named", name, "has source string", actualSourceString);
    }
  }

  void isValidVar() {
    FormattingErrorReporter errorReporter = new FormattingErrorReporter();
    expressionParser(errorReporter).parseVariable();
    if (!errorReporter.getErrorMessages().isEmpty()) {
      fail("is a valid var", errorReporter.getErrorMessages());
    }
  }

  void isValidVarNamed(String name) {
    FormattingErrorReporter errorReporter = new FormattingErrorReporter();
    VarRefNode varNode = expressionParser(errorReporter).parseVariable();

    assertThat(errorReporter.getErrorMessages()).isEmpty();

    String actualName = varNode.getName();
    if (!actualName.equals(name)) {
      failWithBadResults("is var named", name, "is named", actualName);
    }
    if (!varNode.toSourceString().equals("$" + name)) {
      failWithBadResults("has sourceString", "$" + name, "is named", varNode.toSourceString());
    }
  }

  ExpressionSubject withAlias(String alias, String namespace) {
    aliasesBuilder.put(alias, namespace);
    return this;
  }

  private ExpressionParser expressionParser(ErrorReporter reporter) {
    return new ExpressionParser(
        actual(),
        SourceLocation.UNKNOWN,
        SoyParsingContext.create(reporter, "fake.namespace", aliasesBuilder.build()));
  }
}
