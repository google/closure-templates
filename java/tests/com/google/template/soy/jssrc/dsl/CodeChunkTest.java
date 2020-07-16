/*
 * Copyright 2016 Google Inc.
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

package com.google.template.soy.jssrc.dsl;

import static com.google.common.truth.Truth.assertThat;
import static com.google.template.soy.exprtree.Operator.PLUS;
import static com.google.template.soy.jssrc.dsl.Expression.LITERAL_TRUE;
import static com.google.template.soy.jssrc.dsl.Expression.arrayLiteral;
import static com.google.template.soy.jssrc.dsl.Expression.construct;
import static com.google.template.soy.jssrc.dsl.Expression.dontTrustPrecedenceOf;
import static com.google.template.soy.jssrc.dsl.Expression.fromExpr;
import static com.google.template.soy.jssrc.dsl.Expression.id;
import static com.google.template.soy.jssrc.dsl.Expression.ifExpression;
import static com.google.template.soy.jssrc.dsl.Expression.not;
import static com.google.template.soy.jssrc.dsl.Expression.number;
import static com.google.template.soy.jssrc.dsl.Expression.stringLiteral;
import static com.google.template.soy.jssrc.dsl.Statement.forLoop;
import static com.google.template.soy.jssrc.dsl.Statement.ifStatement;
import static com.google.template.soy.jssrc.dsl.Statement.returnValue;
import static com.google.template.soy.jssrc.dsl.Statement.throwValue;
import static com.google.template.soy.jssrc.dsl.Statement.treatRawStringAsStatementLegacyOnly;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.jssrc.dsl.ClassExpression.MethodDeclaration;
import com.google.template.soy.jssrc.internal.JsSrcNameGenerators;
import com.google.template.soy.jssrc.restricted.JsExpr;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link CodeChunk}. */
@RunWith(JUnit4.class)
public final class CodeChunkTest {

  private static final Joiner JOINER = Joiner.on('\n');
  private CodeChunk.Generator cg;

  @Before
  public void setUp() {
    cg = CodeChunk.Generator.create(JsSrcNameGenerators.forLocalVariables());
  }

  @Test
  public void testSingleExprIsPreserved() {
    JsExpr expr = new JsExpr("1 + 2", PLUS.getPrecedence());
    Expression chunk = fromExpr(expr, ImmutableList.of());
    assertThat(chunk.getCode()).isEqualTo("1 + 2;");
    assertThat(chunk.isRepresentableAsSingleExpression()).isTrue();
    assertThat(chunk.singleExprOrName()).isSameInstanceAs(expr);
  }

  @Test
  public void testDependentChunks1() {
    Expression var = cg.declarationBuilder().setRhs(number(3).divideBy(number(4))).build().ref();
    Statement statement =
        ifStatement(var.doubleEqualsNull(), id("expensiveFunction").call().asStatement()).build();
    Expression tmp =
        cg.declarationBuilder()
            .setRhs(var.times(number(5)))
            .build()
            .ref()
            .withInitialStatement(statement);
    assertThat(tmp.getCode())
        .isEqualTo(
            JOINER.join(
                "const $tmp = 3 / 4;",
                "if ($tmp == null) {",
                "  expensiveFunction();",
                "}",
                "const $tmp$$1 = $tmp * 5;"));
  }

  @Test
  public void testDependentChunks2() {
    Expression var = cg.declarationBuilder().setRhs(id("foo").dotAccess("bar")).build().ref();
    Statement statement =
        ifStatement(var.doubleEqualsNull(), id("expensiveFunction").call().asStatement()).build();
    assertThat(statement.getCode())
        .isEqualTo(
            JOINER.join(
                "const $tmp = foo.bar;", "if ($tmp == null) {", "  expensiveFunction();", "}"));
  }

  @Test
  public void testDependentChunks3() {
    Expression var = cg.declarationBuilder().setRhs(number(1).times(number(2))).build().ref();
    Statement statement =
        ifStatement(var.doubleEqualsNull(), id("expensiveFunction").call().asStatement()).build();
    assertThat(statement.getCode())
        .isEqualTo(
            JOINER.join(
                "const $tmp = 1 * 2;", "if ($tmp == null) {", "  expensiveFunction();", "}"));
  }

  @Test
  public void testIfEmpty() {
    Expression var = cg.declarationBuilder().setRhs(number(1).plus(number(2))).build().ref();
    Statement statement =
        ifStatement(
                var.doubleEqualsNull(),
                // That's as it should be, since empty blocks are useless.
                Statement.treatRawStringAsStatementLegacyOnly("", ImmutableList.of()))
            .build();
    assertThat(statement.getCode())
        .isEqualTo(JOINER.join("const $tmp = 1 + 2;", "if ($tmp == null) {", "}"));
  }

  @Test
  public void testIf1() {
    Expression var =
        cg.declarationBuilder().setMutable().setRhs(number(1).plus(number(2))).build().ref();
    Statement statement =
        ifStatement(var.doubleEqualsNull(), var.assign(number(3)).asStatement()).build();
    assertThat(statement.getCode())
        .isEqualTo(JOINER.join("let $tmp = 1 + 2;", "if ($tmp == null) {", "  $tmp = 3;", "}"));
  }

  @Test
  public void testIf2() {
    Expression var =
        cg.declarationBuilder()
            .setMutable()
            .setRhs(construct(id("VeryExpensiveCtor")))
            .build()
            .ref();
    Statement statement =
        Statement.of(
            var.dotAccess("veryExpensiveMethod").call().asStatement(),
            ifStatement(var.doubleEqualsNull(), var.assign(LITERAL_TRUE).asStatement()).build());
    assertThat(statement.getCode())
        .isEqualTo(
            JOINER.join(
                "let $tmp = new VeryExpensiveCtor();",
                "$tmp.veryExpensiveMethod();",
                "if ($tmp == null) {",
                "  $tmp = true;",
                "}"));
  }

  @Test
  public void testIf3() {
    Expression var = cg.declarationBuilder().setRhs(construct(id("Foo"))).build().ref();
    Statement statement =
        ifStatement(var.dotAccess("isFoo").call(), var.dotAccess("doFoo").call().asStatement())
            .addElseIf(var.dotAccess("isBar").call(), var.dotAccess("doBar").call().asStatement())
            .setElse(var.dotAccess("doSomethingElse").call().asStatement())
            .build();
    assertThat(statement.getCode())
        .isEqualTo(
            JOINER.join(
                "const $tmp = new Foo();",
                "if ($tmp.isFoo()) {",
                "  $tmp.doFoo();",
                "} else if ($tmp.isBar()) {",
                "  $tmp.doBar();",
                "} else {",
                "  $tmp.doSomethingElse();",
                "}"));
  }

  @Test
  public void testFor() {
    String localVar = "myVar";
    Expression initial = number(100);
    Expression limit = number(300);
    Expression increment = number(10);
    Statement body = id("fn").call().asStatement();

    Statement forChunk = forLoop(localVar, initial, limit, increment, body);
    assertThat(forChunk.getCode())
        .isEqualTo(
            JOINER.join("for (let myVar = 100; myVar < 300; myVar += 10) {", "  fn();", "}"));

    initial = id("initialFn").call();
    limit = id("limitFn").call();
    increment = id("incrementFn").call();
    body = id("fn").call().asStatement();

    forChunk = forLoop(localVar, initial, limit, increment, body);
    assertThat(forChunk.getCode())
        .isEqualTo(
            JOINER.join(
                "for (let myVar = initialFn(); myVar < limitFn(); myVar += incrementFn()) {",
                "  fn();",
                "}"));
  }

  @Test
  public void testFor_InitialStatements() {
    String localVar = "myVar";

    Expression foo = cg.declarationBuilder().setRhs(id("foo")).build().ref();
    Expression initial = foo.withInitialStatement(foo.dotAccess("method").call().asStatement());

    Expression bar = cg.declarationBuilder().setRhs(id("bar")).build().ref();
    Expression limit = bar.withInitialStatement(bar.dotAccess("method").call().asStatement());

    Expression baz = cg.declarationBuilder().setRhs(id("baz")).build().ref();
    Expression increment = baz.withInitialStatement(baz.dotAccess("method").call().asStatement());

    Statement body = Statement.of(id("fn").call().asStatement(), id("fn2").call().asStatement());

    Statement forChunk = forLoop(localVar, initial, limit, increment, body);

    assertThat(forChunk.getCode())
        .isEqualTo(
            JOINER.join(
                "const $tmp = foo;",
                "$tmp.method();",
                "const $tmp$$1 = bar;",
                "$tmp$$1.method();",
                "const $tmp$$2 = baz;",
                "$tmp$$2.method();",
                "for (let myVar = $tmp; myVar < $tmp$$1; myVar += $tmp$$2) {",
                "  fn();",
                "  fn2();",
                "}"));
  }

  @Test
  public void testInitialStatementInElseIfIsConditionallyEvaluated() {
    Expression foo = cg.declarationBuilder().setRhs(construct(id("Foo"))).build().ref();
    Expression fooWithStatement =
        foo.withInitialStatement(foo.dotAccess("veryExpensiveMethod").call().asStatement());

    Expression bar = cg.declarationBuilder().setRhs(construct(id("Bar"))).build().ref();
    Expression barWithStatement =
        bar.withInitialStatement(bar.dotAccess("causeNPE").call().asStatement());

    Statement conditional =
        ifStatement(
                not(fooWithStatement.dotAccess("isInitialized").call()),
                id("initializeFoo").call().asStatement())
            .addElseIf(
                not(barWithStatement.dotAccess("isInitialized").call()),
                id("initializeBar").call().asStatement())
            .setElse(id("runBackupCode").call().asStatement())
            .build();
    assertThat(conditional.getCode())
        .isEqualTo(
            JOINER.join(
                "const $tmp = new Foo();",
                "$tmp.veryExpensiveMethod();",
                "if (!$tmp.isInitialized()) {",
                "  initializeFoo();",
                "} else {",
                "  const $tmp$$1 = new Bar();",
                "  $tmp$$1.causeNPE();",
                "  if (!$tmp$$1.isInitialized()) {",
                "    initializeBar();",
                "  } else {",
                "    runBackupCode();",
                "  }",
                "}"));
  }

  @Test
  public void testFieldAccessExpr1() {
    Expression expr = id("foo").bracketAccess(number(1).minus(number(2)));
    assertThat(expr.getCode()).isEqualTo("foo[1 - 2];"); // trailing semicolon
    assertThat(expr.singleExprOrName().getText()).isEqualTo("foo[1 - 2]"); // no trailing semicolon
    assertThat(expr.isRepresentableAsSingleExpression()).isTrue();
  }

  @Test
  public void testFieldAccessExpr2() {
    Expression var =
        cg.declarationBuilder().setRhs(construct(id("VeryExpensiveCtor"))).build().ref();
    Expression varWithStatement =
        var.withInitialStatement(var.dotAccess("veryExpensiveMethod").call().asStatement());

    Expression bracketAccess = id("foo").bracketAccess(varWithStatement);
    // The chunk inside the brackets cannot be represented as a single expression,
    // so it's extracted to a local variable.
    assertThat(bracketAccess.getCode())
        .isEqualTo(
            JOINER.join(
                "const $tmp = new VeryExpensiveCtor();",
                "$tmp.veryExpensiveMethod();",
                "foo[$tmp];"));
  }

  @Test
  public void testTemporary() {
    Expression first =
        cg.declarationBuilder().setRhs(construct(id("VeryExpensiveCtor"))).build().ref();
    assertThat(first.doubleEquals(first).getCode())
        .isEqualTo("const $tmp = new VeryExpensiveCtor();\n$tmp == $tmp;");
  }

  /**
   * TODO(brndn): multiple assignments could collapse to an expression using the comma operator:
   * <code>foo, bar</code>. But the benefit to the gencode is doubtful, and it certainly impairs its
   * readability.
   */
  @Test
  public void testMultipleAssignmentsDoNotCollapseToExpression() {
    Expression first = cg.declarationBuilder().setMutable().setRhs(id("foo")).build().ref();
    Expression second = first.assign(id("bar"));
    assertThat(second.isRepresentableAsSingleExpression()).isFalse();
    assertThat(second.getCode()).isEqualTo(JOINER.join("let $tmp = foo;", "$tmp = bar;"));
  }

  @Test
  public void testInitialStatementsAreExecutedOnce_1() {
    Expression foo = cg.declarationBuilder().setRhs(construct(id("Foo"))).build().ref();
    Expression fooWithStatement =
        foo.withInitialStatement(foo.dotAccess("expensiveInit").call().asStatement());
    Statement conditional =
        ifStatement(
                fooWithStatement.dotAccess("isInitialized").call(),
                fooWithStatement.dotAccess("launch").call().asStatement())
            .build();
    assertThat(conditional.getCode())
        .isEqualTo(
            JOINER.join(
                "const $tmp = new Foo();",
                "$tmp.expensiveInit();",
                "if ($tmp.isInitialized()) {",
                "  $tmp.launch();",
                "}"));
  }

  @Test
  public void testInitialStatementsAreExecutedOnce_2() {
    Expression foo = cg.declarationBuilder().setRhs(construct(id("Foo"))).build().ref();
    Expression fooWithStatement =
        foo.withInitialStatement(foo.dotAccess("expensiveInit").call().asStatement());
    // fooWithStatement is used only inside the branches...
    Expression conditional =
        ifExpression(id("shouldProceed").call(), fooWithStatement.dotAccess("proceed").call())
            .setElse(fooWithStatement.dotAccess("abort").call())
            .build(cg);
    // ...so it is initialized once in each branch:
    assertThat(conditional.getCode())
        .isEqualTo(
            JOINER.join(
                "let $tmp$$1;",
                "if (shouldProceed()) {",
                "  const $tmp = new Foo();",
                "  $tmp.expensiveInit();",
                "  $tmp$$1 = $tmp.proceed();",
                "} else {",
                "  const $tmp = new Foo();",
                "  $tmp.expensiveInit();",
                "  $tmp$$1 = $tmp.abort();",
                "}"));
  }

  @Test
  public void testInitialStatementsAreExecutedOnce_3() {
    Expression dosPrompt = cg.declarationBuilder().setRhs(construct(id("DosPrompt"))).build().ref();
    Expression dosPromptWithSyscall =
        dosPrompt.withInitialStatement(dosPrompt.dotAccess("obscureSyscall").call().asStatement());
    Statement conditional =
        ifStatement(
                id("shouldAbort").call(),
                dosPromptWithSyscall.dotAccess("abort").call().asStatement())
            .addElseIf(
                id("shouldRetry").call(),
                dosPromptWithSyscall.dotAccess("retry").call().asStatement())
            .setElse(id("fail").call().asStatement())
            .build();
    assertThat(conditional.getCode())
        .isEqualTo(
            JOINER.join(
                "if (shouldAbort()) {",
                "  const $tmp = new DosPrompt();",
                "  $tmp.obscureSyscall();",
                "  $tmp.abort();",
                "} else if (shouldRetry()) {",
                "  const $tmp = new DosPrompt();",
                "  $tmp.obscureSyscall();",
                "  $tmp.retry();",
                "} else {",
                "  fail();",
                "}"));
  }

  @Test
  public void testSimpleConditionalUsesTernaryExpression() {
    Expression ternary = ifExpression(id("foo"), id("bar")).setElse(id("baz")).build(cg);
    assertThat(ternary.getCode()).isEqualTo("foo ? bar : baz;");
  }

  @Test
  public void testConditionalWithNonExpressionPredicateUsesTernaryExpression() {
    Expression foo = cg.declarationBuilder().setRhs(construct(id("Foo"))).build().ref();
    Expression fooWithStatement =
        foo.withInitialStatement(foo.dotAccess("expensiveMethod").call().asStatement());
    Expression ternary =
        ifExpression(fooWithStatement.doubleEqualsNull(), id("bar")).setElse(id("baz")).build(cg);
    assertThat(ternary.getCode())
        .isEqualTo(
            JOINER.join(
                "const $tmp = new Foo();", "$tmp.expensiveMethod();", "$tmp == null ? bar : baz;"));
  }

  @Test
  public void testConditionalWithNonExpressionConsequentDoesntUseTernaryExpression() {
    VariableDeclaration foo = cg.declarationBuilder().setRhs(construct(id("Foo"))).build();
    Statement fooWithStatement =
        Statement.of(foo, foo.ref().dotAccess("expensiveMethod").call().asStatement());
    Statement ternary =
        ifStatement(id("shouldProceed").call(), fooWithStatement)
            .setElse(id("baz").asStatement())
            .build();
    assertThat(ternary.getCode())
        .isEqualTo(
            JOINER.join(
                "if (shouldProceed()) {",
                "  const $tmp = new Foo();",
                "  $tmp.expensiveMethod();",
                "} else {",
                "  baz;",
                "}"));
  }

  @Test
  public void testConditionalWithNonExpressionAlternateDoesntUseTernaryExpression() {
    VariableDeclaration foo = cg.declarationBuilder().setRhs(construct(id("Foo"))).build();
    Statement fooWithStatement =
        Statement.of(foo, foo.ref().dotAccess("expensiveMethod").call().asStatement());
    Statement ternary =
        ifStatement(id("shouldProceed").call(), id("bar").asStatement())
            .setElse(fooWithStatement)
            .build();
    assertThat(ternary.getCode())
        .isEqualTo(
            JOINER.join(
                "if (shouldProceed()) {",
                "  bar;",
                "} else {",
                "  const $tmp = new Foo();",
                "  $tmp.expensiveMethod();",
                "}"));
  }

  @Test
  public void testAssignmentToValueOfConditional_Simple() {
    Expression conditional = ifExpression(id("foo"), id("bar")).setElse(id("baz")).build(cg);
    Expression var = cg.declarationBuilder().setMutable().setRhs(id("blah")).build().ref();
    Expression assignment = var.assign(conditional);
    assertThat(assignment.getCode())
        .isEqualTo(JOINER.join("let $tmp = blah;", "$tmp = foo ? bar : baz;"));
  }

  @Test
  public void testAssignmentToValueOfConditional_Complex() {
    Expression foo = cg.declarationBuilder().setRhs(construct(id("Foo"))).build().ref();
    Expression fooWithStatement =
        foo.withInitialStatement(foo.dotAccess("expensiveMethod").call().asStatement());
    Expression conditional = ifExpression(id("foo"), fooWithStatement).setElse(id("baz")).build(cg);
    assertThat(conditional.getCode())
        .isEqualTo(
            JOINER.join(
                "let $tmp$$1;",
                "if (foo) {",
                "  const $tmp = new Foo();",
                "  $tmp.expensiveMethod();",
                "  $tmp$$1 = $tmp;",
                "} else {",
                "  $tmp$$1 = baz;",
                "}"));
  }

  @Test
  public void testMapLiteralLookup() {
    Expression foo = cg.declarationBuilder().setRhs(construct(id("Foo"))).build().ref();
    Expression fooWithStatement =
        foo.withInitialStatement(foo.dotAccess("expensiveMethod").call().asStatement());
    Expression map =
        cg.declarationBuilder().setRhs(Expression.objectLiteral(ImmutableMap.of())).build().ref();
    Expression mapAssignment = map.bracketAccess(stringLiteral("foo")).assign(fooWithStatement);
    assertThat(mapAssignment.getCode())
        .isEqualTo(
            JOINER.join(
                "const $tmp$$1 = {};",
                "const $tmp = new Foo();",
                "$tmp.expensiveMethod();",
                "$tmp$$1['foo'] = $tmp;"));
  }

  @Test
  public void testObjectLiteral() {
    Expression objectLiteral =
        Expression.objectLiteral(
            ImmutableMap.of(
                "plus", number(15).plus(Expression.number(4)), "string", stringLiteral("hello")));

    assertThat(objectLiteral.getCode()).isEqualTo("{plus: 15 + 4, string: 'hello'};");

    objectLiteral =
        Expression.objectLiteralWithQuotedKeys(ImmutableMap.of("quoted", stringLiteral("orange")));

    assertThat(objectLiteral.getCode()).isEqualTo("{'quoted': 'orange'};");
  }

  @Test
  public void testAppropriateParensInTernary() {
    Expression conditional =
        ifExpression(id("a").assign(id("b")).doubleEquals(id("c")), id("d"))
            .setElse(id("e"))
            .build(cg);
    assertThat(conditional.getCode())
        .isEqualTo("(a = b) == c ? d : e;");
  }

  @Test
  public void testExprsThatAreNotRepresentableAsSingleExpressions() {
    Expression foo = cg.declarationBuilder().setRhs(construct(id("Foo"))).build().ref();
    Expression fooWithStatement =
        foo.withInitialStatement(foo.dotAccess("expensiveMethod").call().asStatement());

    assertThat(fooWithStatement.getCode())
        .isEqualTo(JOINER.join("const $tmp = new Foo();", "$tmp.expensiveMethod();"));
    assertThat(fooWithStatement.singleExprOrName().getText()).isEqualTo("$tmp");

    Expression val2 = fooWithStatement.doubleEqualsNull();
    assertThat(val2.getCode())
        .isEqualTo(
            JOINER.join("const $tmp = new Foo();", "$tmp.expensiveMethod();", "$tmp == null;"));
    assertThat(val2.singleExprOrName().getText()).isEqualTo("$tmp == null");

    Statement val3 = cg.declarationBuilder().setRhs(val2.plus(number(1))).build();
    assertThat(val3.getCode())
        .isEqualTo(
            JOINER.join(
                "const $tmp = new Foo();",
                "$tmp.expensiveMethod();",
                "const $tmp$$1 = ($tmp == null) + 1;")); // + binds tighter than ==, so protect ==
  }

  @Test
  public void testPrecedence() {
    Expression call = id("a").plus(id("b")).call(id("c"), id("d"));
    assertThat(call.getCode()).isEqualTo("(a + b)(c, d);");

    Expression ternary = ifExpression(id("a").assign(id("b")), id("c")).setElse(id("d")).build(cg);
    assertThat(ternary.getCode()).isEqualTo("(a = b) ? c : d;");
  }

  @Test
  public void testPrecedenceForLeafs() {
    Expression negate = not(id("a"));
    assertThat(negate.getCode()).isEqualTo("!a;");
    negate = not(id("a").plus(id("b")));
    // The argument to not() is a structured code chunk with lower precedence.
    // It should be parenthesized.
    assertThat(negate.getCode()).isEqualTo("!(a + b);");
    negate = not(fromExpr(new JsExpr("a + b", PLUS.getPrecedence()), ImmutableList.of()));
    // Even though the argument to not() is a flat JsExpr, its precedence is preserved,
    // so it should be parenthesized.
    assertThat(negate.getCode()).isEqualTo("!(a + b);");
  }

  @Test
  public void testAssociativity_assignment() {
    Expression leaf = id("a").assign(id("b"));
    Expression branch = leaf.assign(id("c"));
    // assignment is right-associative, so requires parens when appearing as the left operand...
    assertThat(branch.getCode()).isEqualTo("(a = b) = c;");

    leaf = id("b").assign(id("c"));
    branch = id("a").assign(leaf);
    // ...but not when appearing as the right operand.
    assertThat(branch.getCode()).isEqualTo("a = b = c;");
  }

  @Test
  public void testAssociativity_ternary() {
    Expression leaf = ifExpression(id("a"), id("b")).setElse(id("c")).build(cg);
    Expression branch = ifExpression(leaf, id("d")).setElse(id("e")).build(cg);
    // ?: is right-associative, so requires parens when appearing as the left operand...
    assertThat(branch.getCode()).isEqualTo("(a ? b : c) ? d : e;");

    leaf = ifExpression(id("c"), id("d")).setElse(id("e")).build(cg);
    branch = ifExpression(id("a"), id("b")).setElse(leaf).build(cg);
    // ...but not when appearing as the right operand.
    assertThat(branch.getCode()).isEqualTo("a ? b : c ? d : e;");
  }

  @Test
  public void testBadPrecedenceOfJsExpr() {
    JsExpr wrongPrecedence = new JsExpr(
        "a / b",
        Integer.MAX_VALUE); // should be Operator.DIVIDE_BY.getPrecedence()
    Expression bad = not(fromExpr(wrongPrecedence, ImmutableList.of()));
    assertThat(bad.getCode()).isEqualTo("!a / b;");
    Expression good = not(dontTrustPrecedenceOf(wrongPrecedence, ImmutableList.of()));
    assertThat(good.getCode()).isEqualTo("!(a / b);");
  }

  @Test
  public void testAnd_BothOperandsRepresentableAsSingleExprs() {
    Expression and = id("a").and(id("b"), cg);
    assertThat(and.getCode()).isEqualTo("a && b;");
  }

  @Test
  public void testAnd_FirstOperandNotRepresentableAsSingleExpr() {
    Expression foo = cg.declarationBuilder().setRhs(construct(id("Foo"))).build().ref();
    Expression fooWithStatement =
        foo.withInitialStatement(foo.dotAccess("expensiveMethod").call().asStatement());

    // The first operand is always evaluated, so no explicit short-circuiting logic is needed.
    Expression and = fooWithStatement.and(id("a"), cg);
    assertThat(and.getCode())
        .isEqualTo(JOINER.join("const $tmp = new Foo();", "$tmp.expensiveMethod();", "$tmp && a;"));
  }

  @Test
  public void testAnd_SecondOperandNotRepresentableAsSingleExpr() {
    Expression foo = cg.declarationBuilder().setRhs(construct(id("Foo"))).build().ref();
    Expression fooWithStatement =
        foo.withInitialStatement(foo.dotAccess("expensiveMethod").call().asStatement());

    // The second operand is evaluated only if the first operand evals as true.
    Expression and = id("a").and(fooWithStatement, cg);
    assertThat(and.getCode())
        .isEqualTo(
            JOINER.join(
                "let $tmp$$1 = a;",
                "if ($tmp$$1) {",
                "  const $tmp = new Foo();",
                "  $tmp.expensiveMethod();",
                "  $tmp$$1 = $tmp;",
                "}"));
  }

  @Test
  public void testAnd_NeitherOperandRepresentableAsSingleExpr() {
    Expression foo = cg.declarationBuilder().setRhs(construct(id("Foo"))).build().ref();
    Expression fooWithStatement =
        foo.withInitialStatement(foo.dotAccess("expensiveMethod").call().asStatement());

    Expression bar = cg.declarationBuilder().setRhs(construct(id("Bar"))).build().ref();
    Expression barWithStatement =
        bar.withInitialStatement(bar.dotAccess("anotherExpensiveMethod").call().asStatement());

    // The second operand is evaluated only if the first operand evals as true.
    Expression and = fooWithStatement.and(barWithStatement, cg);
    assertThat(and.getCode())
        .isEqualTo(
            JOINER.join(
                "const $tmp = new Foo();",
                "$tmp.expensiveMethod();",
                "let $tmp$$2 = $tmp;",
                "if ($tmp$$2) {",
                "  const $tmp$$1 = new Bar();",
                "  $tmp$$1.anotherExpensiveMethod();",
                "  $tmp$$2 = $tmp$$1;",
                "}"));
  }

  @Test
  public void testOr_BothOperandsRepresentableAsSingleExprs() {
    Expression or = id("a").or(id("b"), cg);
    assertThat(or.getCode()).isEqualTo("a || b;");
  }

  @Test
  public void testOr_FirstOperandNotRepresentableAsSingleExpr() {
    Expression foo = cg.declarationBuilder().setRhs(construct(id("Foo"))).build().ref();
    Expression fooWithStatement =
        foo.withInitialStatement(foo.dotAccess("expensiveMethod").call().asStatement());

    // The first operand is always evaluated, so no explicit short-circuiting logic is needed.
    Expression or = fooWithStatement.or(id("a"), cg);
    assertThat(or.getCode())
        .isEqualTo(JOINER.join("const $tmp = new Foo();", "$tmp.expensiveMethod();", "$tmp || a;"));
  }

  @Test
  public void testOr_SecondOperandNotRepresentableAsSingleExpr() {
    Expression foo = cg.declarationBuilder().setRhs(construct(id("Foo"))).build().ref();
    Expression fooWithStatement =
        foo.withInitialStatement(foo.dotAccess("expensiveMethod").call().asStatement());

    // The second operand is evaluated only if the first operand is false.
    Expression or = id("a").or(fooWithStatement, cg);
    assertThat(or.getCode())
        .isEqualTo(
            JOINER.join(
                "let $tmp$$1 = a;",
                "if (!$tmp$$1) {",
                "  const $tmp = new Foo();",
                "  $tmp.expensiveMethod();",
                "  $tmp$$1 = $tmp;",
                "}"));
  }

  @Test
  public void testOr_NeitherOperandRepresentableAsSingleExpr() {
    Expression foo = cg.declarationBuilder().setRhs(construct(id("Foo"))).build().ref();
    Expression fooWithStatement =
        foo.withInitialStatement(foo.dotAccess("expensiveMethod").call().asStatement());

    Expression bar = cg.declarationBuilder().setRhs(construct(id("Bar"))).build().ref();
    Expression barWithStatement =
        bar.withInitialStatement(bar.dotAccess("anotherExpensiveMethod").call().asStatement());

    // The second operand is evaluated only if the first operand is false.
    Expression or = fooWithStatement.or(barWithStatement, cg);
    assertThat(or.getCode())
        .isEqualTo(
            JOINER.join(
                "const $tmp = new Foo();",
                "$tmp.expensiveMethod();",
                "let $tmp$$2 = $tmp;",
                "if (!$tmp$$2) {",
                "  const $tmp$$1 = new Bar();",
                "  $tmp$$1.anotherExpensiveMethod();",
                "  $tmp$$2 = $tmp$$1;",
                "}"));
  }

  @Test
  public void testExpressionAsStatement() {
    Expression expression = Expression.THIS.dotAccess("myVar");
    Statement statement = expression.asStatement();

    assertThat(statement.getCode()).isEqualTo("this.myVar;");

    statement =
        expression.asStatement(
            JsDoc.builder().addParameterizedAnnotation("private", "string").build());

    assertThat(statement.getCode())
        .isEqualTo(JOINER.join("/** @private {string} */", "this.myVar;"));
  }

  @Test
  public void testDeclarations() {
    // First use an automatic var name for the declaration.
    VariableDeclaration decl = cg.declarationBuilder().setRhs(id("bar")).build();
    // Even though this is the whole program, the var is declared.
    assertThat(decl.getCode()).isEqualTo("const $tmp = bar;");
    // If this is being used in another chunk, the var must be declared to prevent re-evaluation
    Expression use = decl.ref().call(decl.ref());
    assertThat(use.getCode()).isEqualTo(JOINER.join("const $tmp = bar;", "$tmp($tmp);"));
    // If the program is partially being built elsewhere,
    // something could reference the variable, so we need to keep it.
    assertThat(decl.getStatementsForInsertingIntoForeignCodeAtIndent(0))
        .isEqualTo("const $tmp = bar;\n");

    // Now use a custom var name for the declaration.
    decl = VariableDeclaration.builder("foo").setRhs(id("bar")).build();
    // Even though this is the whole program, the var is declared.
    assertThat(decl.getCode()).isEqualTo("const foo = bar;");
    // If this is being used in another chunk, foo must be declared to prevent re-evaluation
    use = decl.ref().call(decl.ref());
    assertThat(use.getCode()).isEqualTo(JOINER.join("const foo = bar;", "foo(foo);"));
    // If the program is partially being built elsewhere, something could reference foo
    // so we need to keep it.
    assertThat(decl.getStatementsForInsertingIntoForeignCodeAtIndent(0))
        .isEqualTo("const foo = bar;\n");
    assertThat(use.getStatementsForInsertingIntoForeignCodeAtIndent(0))
        .isEqualTo(JOINER.join("const foo = bar;", "foo(foo);\n"));
  }

  @Test
  public void testCustomIndentation() {
    // need to use code chunks that aren't CodeChunk.WithValues to avoid being turned into a ternary
    Statement statement =
        ifStatement(id("foo"), Statement.returnValue(id("bar").call()))
            .setElse(Statement.returnValue(id("baz").dotAccess("method").call()))
            .build();
    assertThat(statement.getStatementsForInsertingIntoForeignCodeAtIndent(2))
        .isEqualTo(
            JOINER.join(
                "  if (foo) {",
                "    return bar();",
                "  } else {",
                "    return baz.method();",
                "  }\n"));

    Statement decl = VariableDeclaration.builder("blah").setRhs(number(2)).build();
    assertThat(decl.getStatementsForInsertingIntoForeignCodeAtIndent(4))
        .isEqualTo("    const blah = 2;\n");
  }

  @Test
  public void testReturn() {
    Statement returnStatement = returnValue(id("blah"));
    assertThat(returnStatement.getCode()).isEqualTo("return blah;");

    Expression foo = cg.declarationBuilder().setRhs(construct(id("Foo"))).build().ref();
    Expression fooWithStatement =
        foo.withInitialStatement(foo.dotAccess("expensiveMethod").call().asStatement());
    returnStatement = returnValue(fooWithStatement);
    assertThat(returnStatement.getCode())
        .isEqualTo(
            JOINER.join("const $tmp = new Foo();", "$tmp.expensiveMethod();", "return $tmp;"));
  }

  @Test
  public void testReturnNothing() {
    Statement returnNothingStatement = Statement.returnNothing();
    assertThat(returnNothingStatement.getCode()).isEqualTo("return;");
  }

  @Test
  public void testThrow() {
    Statement throwStatement = throwValue(construct(id("Error"), stringLiteral("blah")));
    assertThat(throwStatement.getCode()).isEqualTo("throw new Error('blah');");

    Expression foo =
        cg.declarationBuilder().setRhs(construct(id("Error"), stringLiteral("blah"))).build().ref();
    throwStatement = throwValue(foo);
    assertThat(throwStatement.getCode())
        .isEqualTo(JOINER.join("const $tmp = new Error('blah');", "throw $tmp;"));
  }

  @Test
  public void testEquality() {
    assertThat(arrayLiteral(ImmutableList.of(construct(id("Foo")))))
        .isEqualTo(arrayLiteral(ImmutableList.of(construct(id("Foo")))));
  }

  @Test
  public void testCollectRequires() {
    Statement chunk =
        ifStatement(
                id("a"),
                treatRawStringAsStatementLegacyOnly(
                    "foo",
                    ImmutableList.of(GoogRequire.create("foo.bar"), GoogRequire.create("foo.baz"))))
            .setElse(
                treatRawStringAsStatementLegacyOnly(
                    "foo", ImmutableList.of(GoogRequire.create("foo.quux"))))
            .build();
    final List<String> namespaces = new ArrayList<>();
    chunk.collectRequires(r -> namespaces.add(r.symbol()));
    assertThat(namespaces).containsExactly("foo.bar", "foo.baz", "foo.quux");
  }

  @Test
  public void testSwitch_simple() {
    Expression foo = id("foo");
    Statement switchStatement =
        Statement.switchValue(foo.dotAccess("getStuff").call())
            .addCase(
                ImmutableList.of(stringLiteral("bar")), foo.dotAccess("bar").call().asStatement())
            .addCase(
                ImmutableList.of(stringLiteral("baz"), stringLiteral("quux")),
                foo.dotAccess("bazOrQuux").call().asStatement())
            .setDefault(foo.dotAccess("somethingElse").call().asStatement())
            .build();
    assertThat(switchStatement.getCode())
        .isEqualTo(
            JOINER.join(
                "switch (foo.getStuff()) {",
                "  case 'bar':",
                "    foo.bar();",
                "    break;",
                "  case 'baz':",
                "  case 'quux':",
                "    foo.bazOrQuux();",
                "    break;",
                "  default:",
                "    foo.somethingElse();",
                "}"));
  }

  @Test
  public void testSwitch_withInitialStatements() {
    Expression foo = cg.declarationBuilder().setRhs(construct(id("Foo"))).build().ref();
    Expression fooWithStatement =
        foo.withInitialStatement(foo.dotAccess("expensiveMethod").call().asStatement());

    Expression foo2 = cg.declarationBuilder().setRhs(construct(id("Foo"))).build().ref();
    Expression foo2WithStatement =
        foo2.withInitialStatement(foo2.dotAccess("anotherExpensiveMethod").call().asStatement());

    Statement switchStatement =
        Statement.switchValue(fooWithStatement.dotAccess("getStuff").call())
            .addCase(
                ImmutableList.of(stringLiteral("bar")), id("someFunction").call().asStatement())
            .addCase(
                ImmutableList.of(stringLiteral("baz"), foo2WithStatement),
                fooWithStatement.dotAccess("bazOrFoo2").call().asStatement())
            .setDefault(fooWithStatement.dotAccess("somethingElse").call().asStatement())
            .build();
    assertThat(switchStatement.getCode())
        .isEqualTo(
            JOINER.join(
                // The initial statements of the switch expression are hoisted to the top
                "const $tmp = new Foo();",
                "$tmp.expensiveMethod();",
                // The initial statements of all the case labels are hoisted to the top
                "const $tmp$$1 = new Foo();",
                "$tmp$$1.anotherExpensiveMethod();",
                "switch ($tmp.getStuff()) {",
                "  case 'bar':",
                // The initial statements of the case bodies are not hoisted
                "    someFunction();",
                "    break;",
                "  case 'baz':",
                "  case $tmp$$1:",
                "    $tmp.bazOrFoo2();",
                "    break;",
                "  default:",
                "    $tmp.somethingElse();",
                "}"));
  }

  @Test
  public void testJsDocSingleLine() {
    JsDoc.Builder jsDocBuilder = JsDoc.builder();
    jsDocBuilder.addParameterizedAnnotation("type", "boolean");
    VariableDeclaration variable =
        VariableDeclaration.builder("foo").setJsDoc(jsDocBuilder.build()).build();
    assertThat(variable.getCode()).isEqualTo(JOINER.join("/** @type {boolean} */", "let foo;"));
  }

  @Test
  public void testClassExpression() {
    JsDoc.Builder jsDocBuilder = JsDoc.builder();
    jsDocBuilder.addParam("num", "number");
    Statement body = returnValue(id("num").plus(Expression.number(1)));
    MethodDeclaration method = MethodDeclaration.create("addOne", jsDocBuilder.build(), body);
    ClassExpression fooClass = ClassExpression.create(ImmutableList.of(method));
    VariableDeclaration fooClassExpression =
        VariableDeclaration.builder("FooClassTemplate").setRhs(fooClass).build();
    assertThat(fooClassExpression.getCode())
        .isEqualTo(
            JOINER.join(
                "const FooClassTemplate = class {",
                "  /** @param {number} num */",
                "  addOne(num) {",
                "    return num + 1;",
                "  }",
                "};"));

    ClassExpression fooSubclass =
        ClassExpression.create(id("BaseClassTemplate"), ImmutableList.of(method));
    VariableDeclaration fooSubclassExpression =
        VariableDeclaration.builder("FooClassTemplate").setRhs(fooSubclass).build();
    assertThat(fooSubclassExpression.getCode())
        .isEqualTo(
            JOINER.join(
                "const FooClassTemplate = class extends BaseClassTemplate {",
                "  /** @param {number} num */",
                "  addOne(num) {",
                "    return num + 1;",
                "  }",
                "};"));
  }

  // Regression test for b/150716365 where we would exibit exponential behavior collecting requires
  // from variables with lots of transitive references.
  @Test
  public void testQuadraticVariableDeclaration() {
    GoogRequire theOneRequire = GoogRequire.create("foo.bar");
    Expression root =
        VariableDeclaration.builder("root")
            .setGoogRequires(ImmutableSet.of(theOneRequire))
            .setRhs(Expression.number(1))
            .build()
            .ref();

    for (int i = 0; i < 1000; i++) {
      root = VariableDeclaration.builder("tmp" + i).setRhs(root.plus(root)).build().ref();
    }
    ImmutableSet.Builder<GoogRequire> requiresBuilder = ImmutableSet.builder();
    root.collectRequires(requiresBuilder::add);
    assertThat(requiresBuilder.build()).containsExactly(theOneRequire);
  }
}
