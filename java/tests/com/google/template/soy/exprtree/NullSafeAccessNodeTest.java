/*
 * Copyright 2020 Google Inc.
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

package com.google.template.soy.exprtree;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.template.soy.exprtree.testing.ExpressionParser;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.testing.Extendable;
import com.google.template.soy.testing.Foo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class NullSafeAccessNodeTest {

  private static final Joiner NEWLINE = Joiner.on('\n');

  @Test
  public void testSimpleToSourceString() {
    assertThat(
            new ExpressionParser("$foo?.someString")
                .withProto(Foo.getDescriptor())
                .withParam("foo", "Foo")
                .parse()
                .toSourceString())
        .isEqualTo("$foo?.someString");

    assertThat(
            new ExpressionParser("$list?[$index]")
                .withParam("list", "list<int>|null")
                .withParam("index", "int")
                .parse()
                .toSourceString())
        .isEqualTo("$list?[$index]");

    assertThat(
            new ExpressionParser("$extendable?.getExtension(boolField)")
                .withParam("extendable", "Extendable")
                .withProto(Extendable.getDescriptor())
                .withProto(com.google.template.soy.testing.Test.boolField.getDescriptor())
                .parse()
                .toSourceString())
        .isEqualTo("$extendable?.getExtension(\"soy.test.boolField\")");
  }

  @Test
  public void testAccessChainToSourceString() {
    assertThat(
            new ExpressionParser("$foo?.messageField?.foo?.messageField")
                .withProto(Foo.getDescriptor())
                .withParam("foo", "Foo")
                .parse()
                .toSourceString())
        .isEqualTo("$foo?.messageField?.foo?.messageField");

    assertThat(
            new ExpressionParser("$foo?.messageField.foo.messageField")
                .withProto(Foo.getDescriptor())
                .withParam("foo", "Foo")
                .parse()
                .toSourceString())
        .isEqualTo("$foo?.messageField.foo.messageField");

    assertThat(
            new ExpressionParser("$foo.messageField?.foo.messageField")
                .withProto(Foo.getDescriptor())
                .withParam("foo", "Foo")
                .parse()
                .toSourceString())
        .isEqualTo("$foo.messageField?.foo.messageField");

    assertThat(
            new ExpressionParser("$foo.messageField.foo?.messageField")
                .withProto(Foo.getDescriptor())
                .withParam("foo", "Foo")
                .parse()
                .toSourceString())
        .isEqualTo("$foo.messageField.foo?.messageField");

    assertThat(
            new ExpressionParser("$foo?.messageField?.foo.messageField")
                .withProto(Foo.getDescriptor())
                .withParam("foo", "Foo")
                .parse()
                .toSourceString())
        .isEqualTo("$foo?.messageField?.foo.messageField");

    assertThat(
            new ExpressionParser("$foo?.messageField.foo?.messageField")
                .withProto(Foo.getDescriptor())
                .withParam("foo", "Foo")
                .parse()
                .toSourceString())
        .isEqualTo("$foo?.messageField.foo?.messageField");

    assertThat(
            new ExpressionParser("$foo.messageField?.foo?.messageField")
                .withProto(Foo.getDescriptor())
                .withParam("foo", "Foo")
                .parse()
                .toSourceString())
        .isEqualTo("$foo.messageField?.foo?.messageField");
  }

  @Test
  public void testListAccessChain() {
    assertThat(
            new ExpressionParser("$list?[0]?[1]?[2]")
                .withParam("list", "list<list<list<bool>|null>|null>|null")
                .parse()
                .toSourceString())
        .isEqualTo("$list?[0]?[1]?[2]");
  }

  @Test
  public void testNestedNullSafe() {
    assertThat(
            new ExpressionParser(
                    "$extendable?.getExtension(fooRepeatedList)"
                        + "?[$extendable?.getExtension(intRepeatedList)?[0]]")
                .withProto(Extendable.getDescriptor())
                .withProto(com.google.template.soy.testing.Test.intRepeated.getDescriptor())
                .withProto(com.google.template.soy.testing.Test.fooRepeated.getDescriptor())
                .withParam("extendable", "Extendable")
                .parse()
                .toSourceString())
        .isEqualTo(
            "$extendable?.getExtension(\"soy.test.fooRepeatedList\")"
                + "?[$extendable?.getExtension(\"soy.test.intRepeatedList\")?[0]]");
  }

  @Test
  public void testSimpleAst() {
    ExprNode expr =
        new ExpressionParser("$foo?.someString")
            .withProto(Foo.getDescriptor())
            .withParam("foo", "Foo")
            .parse();
    String exprString =
        SoyTreeUtils.buildAstStringWithPreview(expr.getParent(), 0, new StringBuilder()).toString();
    assertThat(exprString)
        .isEqualTo(
            NEWLINE.join(
                "NULL_SAFE_ACCESS_NODE: $foo?.someString",
                "  VAR_REF_NODE: $foo",
                "  FIELD_ACCESS_NODE: DO_NOT_USE__NULL_SAFE_ACCESS.someString",
                "    GLOBAL_NODE: DO_NOT_USE__NULL_SAFE_ACCESS",
                ""));
  }

  @Test
  public void testAccessChainAst() {
    ExprNode expr =
        new ExpressionParser("$foo?.messageField?.foo?.messageField")
            .withProto(Foo.getDescriptor())
            .withParam("foo", "Foo")
            .parse();
    String exprString =
        SoyTreeUtils.buildAstStringWithPreview(expr.getParent(), 0, new StringBuilder()).toString();
    assertThat(exprString)
        .isEqualTo(
            NEWLINE.join(
                "NULL_SAFE_ACCESS_NODE: $foo?.messageField?.foo?.messageField",
                "  VAR_REF_NODE: $foo",
                "  NULL_SAFE_ACCESS_NODE:"
                    + " DO_NOT_USE__NULL_SAFE_ACCESS.messageField?.foo?.messageField",
                "    FIELD_ACCESS_NODE: DO_NOT_USE__NULL_SAFE_ACCESS.messageField",
                "      GLOBAL_NODE: DO_NOT_USE__NULL_SAFE_ACCESS",
                "    NULL_SAFE_ACCESS_NODE: DO_NOT_USE__NULL_SAFE_ACCESS.foo?.messageField",
                "      FIELD_ACCESS_NODE: DO_NOT_USE__NULL_SAFE_ACCESS.foo",
                "        GLOBAL_NODE: DO_NOT_USE__NULL_SAFE_ACCESS",
                "      FIELD_ACCESS_NODE: DO_NOT_USE__NULL_SAFE_ACCESS.messageField",
                "        GLOBAL_NODE: DO_NOT_USE__NULL_SAFE_ACCESS",
                ""));

    expr =
        new ExpressionParser("$foo?.messageField.foo.messageField")
            .withProto(Foo.getDescriptor())
            .withParam("foo", "Foo")
            .parse();
    exprString =
        SoyTreeUtils.buildAstStringWithPreview(expr.getParent(), 0, new StringBuilder()).toString();
    assertThat(exprString)
        .isEqualTo(
            NEWLINE.join(
                "NULL_SAFE_ACCESS_NODE: $foo?.messageField.foo.messageField",
                "  VAR_REF_NODE: $foo",
                "  FIELD_ACCESS_NODE: DO_NOT_USE__NULL_SAFE_ACCESS.messageField.foo.messageField",
                "    FIELD_ACCESS_NODE: DO_NOT_USE__NULL_SAFE_ACCESS.messageField.foo",
                "      FIELD_ACCESS_NODE: DO_NOT_USE__NULL_SAFE_ACCESS.messageField",
                "        GLOBAL_NODE: DO_NOT_USE__NULL_SAFE_ACCESS",
                ""));

    expr =
        new ExpressionParser("$foo.messageField?.foo.messageField")
            .withProto(Foo.getDescriptor())
            .withParam("foo", "Foo")
            .parse();
    exprString =
        SoyTreeUtils.buildAstStringWithPreview(expr.getParent(), 0, new StringBuilder()).toString();
    assertThat(exprString)
        .isEqualTo(
            NEWLINE.join(
                "NULL_SAFE_ACCESS_NODE: $foo.messageField?.foo.messageField",
                "  FIELD_ACCESS_NODE: $foo.messageField",
                "    VAR_REF_NODE: $foo",
                "  FIELD_ACCESS_NODE: DO_NOT_USE__NULL_SAFE_ACCESS.foo.messageField",
                "    FIELD_ACCESS_NODE: DO_NOT_USE__NULL_SAFE_ACCESS.foo",
                "      GLOBAL_NODE: DO_NOT_USE__NULL_SAFE_ACCESS",
                ""));

    expr =
        new ExpressionParser("$foo.messageField.foo?.messageField")
            .withProto(Foo.getDescriptor())
            .withParam("foo", "Foo")
            .parse();
    exprString =
        SoyTreeUtils.buildAstStringWithPreview(expr.getParent(), 0, new StringBuilder()).toString();
    assertThat(exprString)
        .isEqualTo(
            NEWLINE.join(
                "NULL_SAFE_ACCESS_NODE: $foo.messageField.foo?.messageField",
                "  FIELD_ACCESS_NODE: $foo.messageField.foo",
                "    FIELD_ACCESS_NODE: $foo.messageField",
                "      VAR_REF_NODE: $foo",
                "  FIELD_ACCESS_NODE: DO_NOT_USE__NULL_SAFE_ACCESS.messageField",
                "    GLOBAL_NODE: DO_NOT_USE__NULL_SAFE_ACCESS",
                ""));

    expr =
        new ExpressionParser("$foo?.messageField?.foo.messageField")
            .withProto(Foo.getDescriptor())
            .withParam("foo", "Foo")
            .parse();
    exprString =
        SoyTreeUtils.buildAstStringWithPreview(expr.getParent(), 0, new StringBuilder()).toString();
    assertThat(exprString)
        .isEqualTo(
            NEWLINE.join(
                "NULL_SAFE_ACCESS_NODE: $foo?.messageField?.foo.messageField",
                "  VAR_REF_NODE: $foo",
                "  NULL_SAFE_ACCESS_NODE:"
                    + " DO_NOT_USE__NULL_SAFE_ACCESS.messageField?.foo.messageField",
                "    FIELD_ACCESS_NODE: DO_NOT_USE__NULL_SAFE_ACCESS.messageField",
                "      GLOBAL_NODE: DO_NOT_USE__NULL_SAFE_ACCESS",
                "    FIELD_ACCESS_NODE: DO_NOT_USE__NULL_SAFE_ACCESS.foo.messageField",
                "      FIELD_ACCESS_NODE: DO_NOT_USE__NULL_SAFE_ACCESS.foo",
                "        GLOBAL_NODE: DO_NOT_USE__NULL_SAFE_ACCESS",
                ""));

    expr =
        new ExpressionParser("$foo?.messageField.foo?.messageField")
            .withProto(Foo.getDescriptor())
            .withParam("foo", "Foo")
            .parse();
    exprString =
        SoyTreeUtils.buildAstStringWithPreview(expr.getParent(), 0, new StringBuilder()).toString();
    assertThat(exprString)
        .isEqualTo(
            NEWLINE.join(
                "NULL_SAFE_ACCESS_NODE: $foo?.messageField.foo?.messageField",
                "  VAR_REF_NODE: $foo",
                "  NULL_SAFE_ACCESS_NODE:"
                    + " DO_NOT_USE__NULL_SAFE_ACCESS.messageField.foo?.messageField",
                "    FIELD_ACCESS_NODE: DO_NOT_USE__NULL_SAFE_ACCESS.messageField.foo",
                "      FIELD_ACCESS_NODE: DO_NOT_USE__NULL_SAFE_ACCESS.messageField",
                "        GLOBAL_NODE: DO_NOT_USE__NULL_SAFE_ACCESS",
                "    FIELD_ACCESS_NODE: DO_NOT_USE__NULL_SAFE_ACCESS.messageField",
                "      GLOBAL_NODE: DO_NOT_USE__NULL_SAFE_ACCESS",
                ""));

    expr =
        new ExpressionParser("$foo.messageField?.foo?.messageField")
            .withProto(Foo.getDescriptor())
            .withParam("foo", "Foo")
            .parse();
    exprString =
        SoyTreeUtils.buildAstStringWithPreview(expr.getParent(), 0, new StringBuilder()).toString();
    assertThat(exprString)
        .isEqualTo(
            NEWLINE.join(
                "NULL_SAFE_ACCESS_NODE: $foo.messageField?.foo?.messageField",
                "  FIELD_ACCESS_NODE: $foo.messageField",
                "    VAR_REF_NODE: $foo",
                "  NULL_SAFE_ACCESS_NODE: DO_NOT_USE__NULL_SAFE_ACCESS.foo?.messageField",
                "    FIELD_ACCESS_NODE: DO_NOT_USE__NULL_SAFE_ACCESS.foo",
                "      GLOBAL_NODE: DO_NOT_USE__NULL_SAFE_ACCESS",
                "    FIELD_ACCESS_NODE: DO_NOT_USE__NULL_SAFE_ACCESS.messageField",
                "      GLOBAL_NODE: DO_NOT_USE__NULL_SAFE_ACCESS",
                ""));
  }

  @Test
  public void testNonNullAssertionChain() {
    ExprNode expr =
        new ExpressionParser("$foo?.messageField!")
            .withProto(Foo.getDescriptor())
            .withParam("foo", "Foo")
            .withExperimentalFeatures("enableNonNullAssertionOperator")
            .parse();
    String exprString =
        SoyTreeUtils.buildAstStringWithPreview(expr.getParent(), 0, new StringBuilder()).toString();
    assertThat(exprString)
        .isEqualTo(
            NEWLINE.join(
                "NULL_SAFE_ACCESS_NODE: $foo?.messageField!",
                "  VAR_REF_NODE: $foo",
                "  ASSERT_NON_NULL_OP_NODE: DO_NOT_USE__NULL_SAFE_ACCESS.messageField!",
                "    FIELD_ACCESS_NODE: DO_NOT_USE__NULL_SAFE_ACCESS.messageField",
                "      GLOBAL_NODE: DO_NOT_USE__NULL_SAFE_ACCESS",
                ""));

    expr =
        new ExpressionParser("$foo!.messageField?.foo")
            .withProto(Foo.getDescriptor())
            .withParam("foo", "Foo")
            .withExperimentalFeatures("enableNonNullAssertionOperator")
            .parse();
    exprString =
        SoyTreeUtils.buildAstStringWithPreview(expr.getParent(), 0, new StringBuilder()).toString();
    assertThat(exprString)
        .isEqualTo(
            NEWLINE.join(
                "NULL_SAFE_ACCESS_NODE: $foo.messageField?.foo",
                "  FIELD_ACCESS_NODE: $foo.messageField",
                "    VAR_REF_NODE: $foo",
                "  FIELD_ACCESS_NODE: DO_NOT_USE__NULL_SAFE_ACCESS.foo",
                "    GLOBAL_NODE: DO_NOT_USE__NULL_SAFE_ACCESS",
                ""));
  }
}
