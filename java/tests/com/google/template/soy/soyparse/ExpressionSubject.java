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

package com.google.template.soy.soyparse;

import static com.google.common.truth.Fact.simpleFact;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.VarRefNode;

/**
 * Custom Truth subject for testing expression parsing.
 *
 * @author brndn@google.com (Brendan Linn)
 */
final class ExpressionSubject extends Subject {

  private final String actual;
  private final ErrorReporter errorReporter;

  private static final Subject.Factory<ExpressionSubject, String> FACTORY =
      new Subject.Factory<ExpressionSubject, String>() {
        @Override
        public ExpressionSubject createSubject(FailureMetadata failureMetadata, String s) {
          return new ExpressionSubject(failureMetadata, s, ErrorReporter.createForTest());
        }
      };

  private final ImmutableMap.Builder<String, String> aliasesBuilder = ImmutableMap.builder();

  public ExpressionSubject(FailureMetadata failureMetadata, String s, ErrorReporter errorReporter) {
    super(failureMetadata, s);
    this.actual = s;
    this.errorReporter = errorReporter;
  }

  static ExpressionSubject assertThatExpression(String input) {
    return Truth.assertAbout(FACTORY).that(input);
  }

  void generatesASTWithRootOfType(Class<? extends ExprNode> clazz) {
    ExprNode root = isValidExpression();
    check("parseExpression()").that(root).isInstanceOf(clazz);
  }

  void isNotValidExpression() {
    parseExpression();
    if (!errorReporter.hasErrors()) {
      failWithActual(simpleFact("expected to be an invalid expression"));
    }
  }

  void isNotValidGlobal() {
    ExprNode expr = parseExpression();
    if (expr instanceof GlobalNode && !errorReporter.hasErrors()) {
      failWithActual(simpleFact("expected to be an invalid global"));
    }
  }

  void isNotValidVar() {
    ExprNode expr = parseExpression();
    if (expr instanceof VarRefNode && !errorReporter.hasErrors()) {
      failWithActual(simpleFact("expected to be an invalid var"));
    }
  }

  ExprNode isValidExpression() {
    ExprNode expr = parseExpression();
    if (errorReporter.hasErrors()) {
      failWithActual("expected to be a valid expression", errorReporter.getErrors());
    }
    return expr;
  }

  void isValidGlobal() {
    ExprNode expr = parseExpression();
    if (errorReporter.hasErrors()) {
      failWithActual("expected to be a valid global", errorReporter.getErrors());
    }
    check("parseExpression()").that(expr).isInstanceOf(GlobalNode.class);
  }

  void isValidGlobalNamed(String name) {
    GlobalNode globalNode = (GlobalNode) parseExpression();
    if (errorReporter.hasErrors()) {
      failWithActual("expected to be valid global", errorReporter.getErrors());
    }
    check("parseExpression().getName()").that(globalNode.getName()).isEqualTo(name);
    check("parseExpression().toSourceString()").that(globalNode.toSourceString()).isEqualTo(name);
  }

  void isValidVar() {
    ExprNode expr = parseExpression();
    if (!(expr instanceof VarRefNode) || errorReporter.hasErrors()) {
      failWithActual("expected to be a valid var", errorReporter.getErrors());
    }
  }

  void isValidVarNamed(String name) {
    VarRefNode varNode = (VarRefNode) parseExpression();

    assertThat(errorReporter.hasErrors()).isFalse();

    check("parseExpression().getName()").that(varNode.getName()).isEqualTo(name);
    check("parseExpression().getName()").that(varNode.toSourceString()).isEqualTo("$" + name);
  }

  ExpressionSubject withAlias(String alias, String namespace) {
    aliasesBuilder.put(alias, namespace);
    return this;
  }

  private ExprNode parseExpression() {
    return SoyFileParser.parseExpression(actual, errorReporter);
  }
}
