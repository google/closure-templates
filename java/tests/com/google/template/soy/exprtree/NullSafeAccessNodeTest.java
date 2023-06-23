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
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
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
            new ExpressionParser("$foo?.stringField")
                .withProto(Foo.getDescriptor())
                .withParam("foo", "Foo")
                .parse()
                .toSourceString())
        .isEqualTo("$foo?.stringField");

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
        .isEqualTo("$extendable?.getExtension(boolField)");
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
            "$extendable?.getExtension(fooRepeatedList)"
                + "?[$extendable?.getExtension(intRepeatedList)?[0]]");
  }

  @Test
  public void testSimpleAst() {
    ExprNode expr =
        new ExpressionParser("$foo?.stringField")
            .withProto(Foo.getDescriptor())
            .withParam("foo", "Foo")
            .parse();
    assertThat(buildAstStringWithPreview(expr))
        .isEqualTo(
            NEWLINE.join(
                "NULL_SAFE_ACCESS_NODE: string: $foo?.stringField",
                "  VAR_REF_NODE: *.Foo: $foo",
                "  FIELD_ACCESS_NODE: string: (null).stringField",
                "    GROUP_NODE: *.Foo: (null)",
                "      NULL_NODE: null: null",
                ""));
    assertThat(buildAstStringWithPreview(((NullSafeAccessNode) expr).asAccessChain()))
        .isEqualTo(
            NEWLINE.join(
                "FIELD_ACCESS_NODE: string: $foo?.stringField", "  VAR_REF_NODE: *.Foo: $foo", ""));
  }

  @Test
  public void testAccessChainAst() {
    ExprNode expr =
        new ExpressionParser("$foo?.messageField?.foo?.messageField")
            .withProto(Foo.getDescriptor())
            .withParam("foo", "Foo")
            .parse();
    assertThat(buildAstStringWithPreview(expr))
        .isEqualTo(
            NEWLINE.join(
                "NULL_SAFE_ACCESS_NODE: *.MessageField: $foo?.messageField?.foo?.messageField",
                "  VAR_REF_NODE: *.Foo: $foo",
                "  NULL_SAFE_ACCESS_NODE: *.MessageField: (null).messageField?.foo?.messageField",
                "    FIELD_ACCESS_NODE: *.MessageField: (null).messageField",
                "      GROUP_NODE: *.Foo: (null)",
                "        NULL_NODE: null: null",
                "    NULL_SAFE_ACCESS_NODE: *.MessageField: (null).foo?.messageField",
                "      FIELD_ACCESS_NODE: *.Foo: (null).foo",
                "        GROUP_NODE: *.MessageField: (null)",
                "          NULL_NODE: null: null",
                "      FIELD_ACCESS_NODE: *.MessageField: (null).messageField",
                "        GROUP_NODE: *.Foo: (null)",
                "          NULL_NODE: null: null",
                ""));
    assertThat(buildAstStringWithPreview(((NullSafeAccessNode) expr).asAccessChain()))
        .isEqualTo(
            NEWLINE.join(
                "FIELD_ACCESS_NODE: *.MessageField: $foo?.messageField?.foo?.messageField",
                "  FIELD_ACCESS_NODE: *.Foo: $foo?.messageField?.foo",
                "    FIELD_ACCESS_NODE: *.MessageField: $foo?.messageField",
                "      VAR_REF_NODE: *.Foo: $foo",
                ""));

    expr =
        new ExpressionParser("$foo?.messageField.foo.messageField")
            .withProto(Foo.getDescriptor())
            .withParam("foo", "Foo")
            .parse();
    assertThat(buildAstStringWithPreview(expr))
        .isEqualTo(
            NEWLINE.join(
                "NULL_SAFE_ACCESS_NODE: *.MessageField: $foo?.messageField.foo.messageField",
                "  VAR_REF_NODE: *.Foo: $foo",
                "  FIELD_ACCESS_NODE: *.MessageField: (null).messageField.foo.messageField",
                "    FIELD_ACCESS_NODE: *.Foo: (null).messageField.foo",
                "      FIELD_ACCESS_NODE: *.MessageField: (null).messageField",
                "        GROUP_NODE: *.Foo: (null)",
                "          NULL_NODE: null: null",
                ""));

    expr =
        new ExpressionParser("$foo.messageField?.foo.messageField")
            .withProto(Foo.getDescriptor())
            .withParam("foo", "Foo")
            .parse();
    assertThat(buildAstStringWithPreview(expr))
        .isEqualTo(
            NEWLINE.join(
                "NULL_SAFE_ACCESS_NODE: *.MessageField: $foo.messageField?.foo.messageField",
                "  FIELD_ACCESS_NODE: *.MessageField: $foo.messageField",
                "    VAR_REF_NODE: *.Foo: $foo",
                "  FIELD_ACCESS_NODE: *.MessageField: (null).foo.messageField",
                "    FIELD_ACCESS_NODE: *.Foo: (null).foo",
                "      GROUP_NODE: *.MessageField: (null)",
                "        NULL_NODE: null: null",
                ""));
    assertThat(buildAstStringWithPreview(((NullSafeAccessNode) expr).asAccessChain()))
        .isEqualTo(
            NEWLINE.join(
                "FIELD_ACCESS_NODE: *.MessageField: $foo.messageField?.foo.messageField",
                "  FIELD_ACCESS_NODE: *.Foo: $foo.messageField?.foo",
                "    FIELD_ACCESS_NODE: *.MessageField: $foo.messageField",
                "      VAR_REF_NODE: *.Foo: $foo",
                ""));

    expr =
        new ExpressionParser("$foo.messageField.foo?.messageField")
            .withProto(Foo.getDescriptor())
            .withParam("foo", "Foo")
            .parse();
    assertThat(buildAstStringWithPreview(expr))
        .isEqualTo(
            NEWLINE.join(
                "NULL_SAFE_ACCESS_NODE: *.MessageField: $foo.messageField.foo?.messageField",
                "  FIELD_ACCESS_NODE: *.Foo: $foo.messageField.foo",
                "    FIELD_ACCESS_NODE: *.MessageField: $foo.messageField",
                "      VAR_REF_NODE: *.Foo: $foo",
                "  FIELD_ACCESS_NODE: *.MessageField: (null).messageField",
                "    GROUP_NODE: *.Foo: (null)",
                "      NULL_NODE: null: null",
                ""));

    expr =
        new ExpressionParser("$foo?.messageField?.foo.messageField")
            .withProto(Foo.getDescriptor())
            .withParam("foo", "Foo")
            .parse();
    assertThat(buildAstStringWithPreview(expr))
        .isEqualTo(
            NEWLINE.join(
                "NULL_SAFE_ACCESS_NODE: *.MessageField: $foo?.messageField?.foo.messageField",
                "  VAR_REF_NODE: *.Foo: $foo",
                "  NULL_SAFE_ACCESS_NODE: *.MessageField: (null).messageField?.foo.messageField",
                "    FIELD_ACCESS_NODE: *.MessageField: (null).messageField",
                "      GROUP_NODE: *.Foo: (null)",
                "        NULL_NODE: null: null",
                "    FIELD_ACCESS_NODE: *.MessageField: (null).foo.messageField",
                "      FIELD_ACCESS_NODE: *.Foo: (null).foo",
                "        GROUP_NODE: *.MessageField: (null)",
                "          NULL_NODE: null: null",
                ""));

    expr =
        new ExpressionParser("$foo?.messageField.foo?.stringField")
            .withProto(Foo.getDescriptor())
            .withParam("foo", "Foo")
            .parse();
    assertThat(buildAstStringWithPreview(expr))
        .isEqualTo(
            NEWLINE.join(
                "NULL_SAFE_ACCESS_NODE: string: $foo?.messageField.foo?.stringField",
                "  VAR_REF_NODE: *.Foo: $foo",
                "  NULL_SAFE_ACCESS_NODE: string: (null).messageField.foo?.stringField",
                "    FIELD_ACCESS_NODE: *.Foo: (null).messageField.foo",
                "      FIELD_ACCESS_NODE: *.MessageField: (null).messageField",
                "        GROUP_NODE: *.Foo: (null)",
                "          NULL_NODE: null: null",
                "    FIELD_ACCESS_NODE: string: (null).stringField",
                "      GROUP_NODE: *.Foo: (null)",
                "        NULL_NODE: null: null",
                ""));
    assertThat(buildAstStringWithPreview(((NullSafeAccessNode) expr).asAccessChain()))
        .isEqualTo(
            NEWLINE.join(
                "FIELD_ACCESS_NODE: string: $foo?.messageField.foo?.stringField",
                "  FIELD_ACCESS_NODE: *.Foo: $foo?.messageField.foo",
                "    FIELD_ACCESS_NODE: *.MessageField: $foo?.messageField",
                "      VAR_REF_NODE: *.Foo: $foo",
                ""));

    expr =
        new ExpressionParser("$foo.messageField?.foo?.messageField")
            .withProto(Foo.getDescriptor())
            .withParam("foo", "Foo")
            .parse();
    assertThat(buildAstStringWithPreview(expr))
        .isEqualTo(
            NEWLINE.join(
                "NULL_SAFE_ACCESS_NODE: *.MessageField: $foo.messageField?.foo?.messageField",
                "  FIELD_ACCESS_NODE: *.MessageField: $foo.messageField",
                "    VAR_REF_NODE: *.Foo: $foo",
                "  NULL_SAFE_ACCESS_NODE: *.MessageField: (null).foo?.messageField",
                "    FIELD_ACCESS_NODE: *.Foo: (null).foo",
                "      GROUP_NODE: *.MessageField: (null)",
                "        NULL_NODE: null: null",
                "    FIELD_ACCESS_NODE: *.MessageField: (null).messageField",
                "      GROUP_NODE: *.Foo: (null)",
                "        NULL_NODE: null: null",
                ""));
  }

  @Test
  public void testNonNullAssertionChain() {
    ExprNode expr =
        new ExpressionParser("$foo?.messageField!")
            .withProto(Foo.getDescriptor())
            .withParam("foo", "Foo")
            .parse();
    assertThat(buildAstStringWithPreview(expr))
        .isEqualTo(
            NEWLINE.join(
                "NULL_SAFE_ACCESS_NODE: *.MessageField: $foo?.messageField!",
                "  VAR_REF_NODE: *.Foo: $foo",
                "  ASSERT_NON_NULL_OP_NODE: *.MessageField: (null).messageField!",
                "    FIELD_ACCESS_NODE: *.MessageField: (null).messageField",
                "      GROUP_NODE: *.Foo: (null)",
                "        NULL_NODE: null: null",
                ""));

    expr =
        new ExpressionParser("$foo!.messageField?.foo")
            .withProto(Foo.getDescriptor())
            .withParam("foo", "Foo")
            .parse();
    assertThat(buildAstStringWithPreview(expr))
        .isEqualTo(
            NEWLINE.join(
                "NULL_SAFE_ACCESS_NODE: *.Foo: $foo.messageField?.foo",
                "  FIELD_ACCESS_NODE: *.MessageField: $foo.messageField",
                "    VAR_REF_NODE: *.Foo: $foo",
                "  FIELD_ACCESS_NODE: *.Foo: (null).foo",
                "    GROUP_NODE: *.MessageField: (null)",
                "      NULL_NODE: null: null",
                ""));
  }

  private static String buildAstStringWithPreview(ExprNode node) {
    return buildAstStringWithPreview(ImmutableList.of(node), 0, new StringBuilder()).toString();
  }

  /**
   * Similar to {@link SoyTreeUtils#buildAstString}, but for ExprNodes and also prints the source
   * string for debug usages.
   */
  @CanIgnoreReturnValue
  private static StringBuilder buildAstStringWithPreview(
      Iterable<ExprNode> nodes, int indent, StringBuilder sb) {
    for (ExprNode child : nodes) {
      sb.append(Strings.repeat("  ", indent))
          .append(child.getKind())
          .append(": ")
          .append(child.getType().toString().replaceAll("soy\\.test\\.", "*."))
          .append(": ")
          .append(child.toSourceString())
          .append('\n');
      if (child instanceof ParentExprNode) {
        buildAstStringWithPreview(((ParentExprNode) child).getChildren(), indent + 1, sb);
      }
    }
    return sb;
  }
}
