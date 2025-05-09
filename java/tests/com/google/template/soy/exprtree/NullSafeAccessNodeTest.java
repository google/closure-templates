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
import static com.google.template.soy.testing.SharedTestUtils.buildAstStringWithPreview;

import com.google.common.base.Joiner;
import com.google.template.soy.exprtree.testing.ExpressionParser;
import com.google.template.soy.testing.Extendable;
import com.google.template.soy.testing.Foo;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class NullSafeAccessNodeTest {

  private static final Joiner NEWLINE = Joiner.on('\n');

  @Test
  public void testSimpleToSourceString() {
    assertThat(
            new ExpressionParser("$foo?.getStringField()")
                .withProto(Foo.getDescriptor())
                .withParam("foo", "Foo")
                .parse()
                .toSourceString())
        .isEqualTo("$foo?.getStringField()");

    assertThat(
            new ExpressionParser("$list?[$index]")
                .withOptionalParam("list", "list<int>|null")
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
            new ExpressionParser("$foo?.getMessageField()?.getFoo()?.getMessageField()")
                .withProto(Foo.getDescriptor())
                .withParam("foo", "Foo")
                .parse()
                .toSourceString())
        .isEqualTo("$foo?.getMessageField()?.getFoo()?.getMessageField()");

    assertThat(
            new ExpressionParser(
                    "$foo?.getReadonlyMessageField().getReadonlyFoo().getMessageField()")
                .withProto(Foo.getDescriptor())
                .withParam("foo", "Foo")
                .parse()
                .toSourceString())
        .isEqualTo("$foo?.getReadonlyMessageField().getReadonlyFoo().getMessageField()");
  }

  @Test
  public void testListAccessChain() {
    assertThat(
            new ExpressionParser("$list?[0]?[1]?[2]")
                .withOptionalParam("list", "list<list<list<bool>|null>|null>|null")
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
        new ExpressionParser("$foo?.getStringField()")
            .withProto(Foo.getDescriptor())
            .withParam("foo", "Foo")
            .parse();
    assertThat(buildAstStringWithPreview(expr))
        .isEqualTo(
            NEWLINE.join(
                "NULL_SAFE_ACCESS_NODE: string: $foo?.getStringField()",
                "  VAR_REF_NODE: *.Foo: $foo",
                "  METHOD_CALL_NODE: string: (undefined).getStringField()",
                "    GROUP_NODE: *.Foo: (undefined)",
                "      UNDEFINED_NODE: undefined: undefined",
                ""));
    assertThat(buildAstStringWithPreview(((NullSafeAccessNode) expr).asAccessChain()))
        .isEqualTo(
            NEWLINE.join(
                "METHOD_CALL_NODE: string: $foo?.getStringField()",
                "  VAR_REF_NODE: *.Foo: $foo",
                ""));
  }

  @Test
  public void testAccessChainAst() {
    ExprNode expr =
        new ExpressionParser("$foo?.getMessageField()?.getFoo()?.getMessageField()")
            .withProto(Foo.getDescriptor())
            .withParam("foo", "Foo")
            .parse();
    assertThat(buildAstStringWithPreview(expr))
        .isEqualTo(
            NEWLINE.join(
                "NULL_SAFE_ACCESS_NODE: *.MessageField|undefined:"
                    + " $foo?.getMessageField()?.getFoo()?.getMessageField()",
                "  VAR_REF_NODE: *.Foo: $foo",
                "  NULL_SAFE_ACCESS_NODE: *.MessageField|undefined:"
                    + " (undefined).getMessageField()?.getFoo()?.getMessageField()",
                "    METHOD_CALL_NODE: *.MessageField|undefined: (undefined).getMessageField()",
                "      GROUP_NODE: *.Foo: (undefined)",
                "        UNDEFINED_NODE: undefined: undefined",
                "    NULL_SAFE_ACCESS_NODE: *.MessageField|undefined:"
                    + " (undefined).getFoo()?.getMessageField()",
                "      METHOD_CALL_NODE: *.Foo|undefined: (undefined).getFoo()",
                "        GROUP_NODE: *.MessageField|undefined: (undefined)",
                "          UNDEFINED_NODE: undefined: undefined",
                "      METHOD_CALL_NODE: *.MessageField|undefined: (undefined).getMessageField()",
                "        GROUP_NODE: *.Foo|undefined: (undefined)",
                "          UNDEFINED_NODE: undefined: undefined",
                ""));
    assertThat(buildAstStringWithPreview(((NullSafeAccessNode) expr).asAccessChain()))
        .isEqualTo(
            NEWLINE.join(
                "METHOD_CALL_NODE: *.MessageField|undefined:"
                    + " $foo?.getMessageField()?.getFoo()?.getMessageField()",
                "  METHOD_CALL_NODE: *.Foo|undefined: $foo?.getMessageField()?.getFoo()",
                "    METHOD_CALL_NODE: *.MessageField|undefined: $foo?.getMessageField()",
                "      VAR_REF_NODE: *.Foo: $foo",
                ""));

    expr =
        new ExpressionParser("$foo?.getReadonlyMessageField().getReadonlyFoo().getMessageField()")
            .withProto(Foo.getDescriptor())
            .withParam("foo", "Foo")
            .parse();
    assertThat(buildAstStringWithPreview(expr))
        .isEqualTo(
            NEWLINE.join(
                "NULL_SAFE_ACCESS_NODE: *.MessageField|undefined:"
                    + " $foo?.getReadonlyMessageField().getReadonlyFoo().getMessageField()",
                "  VAR_REF_NODE: *.Foo: $foo",
                "  METHOD_CALL_NODE: *.MessageField|undefined:"
                    + " (undefined).getReadonlyMessageField().getReadonlyFoo().getMessageField()",
                "    METHOD_CALL_NODE: *.Foo:"
                    + " (undefined).getReadonlyMessageField().getReadonlyFoo()",
                "      METHOD_CALL_NODE: *.MessageField: (undefined).getReadonlyMessageField()",
                "        GROUP_NODE: *.Foo: (undefined)",
                "          UNDEFINED_NODE: undefined: undefined",
                ""));

    expr =
        new ExpressionParser("$foo.getMessageField()?.getReadonlyFoo().getMessageField()")
            .withProto(Foo.getDescriptor())
            .withParam("foo", "Foo")
            .parse();
    assertThat(buildAstStringWithPreview(expr))
        .isEqualTo(
            NEWLINE.join(
                "NULL_SAFE_ACCESS_NODE: *.MessageField|undefined"
                    + ":"
                    + " $foo.getMessageField()?.getReadonlyFoo().getMessageField()",
                "  METHOD_CALL_NODE: *.MessageField|undefined: $foo.getMessageField()",
                "    VAR_REF_NODE: *.Foo: $foo",
                "  METHOD_CALL_NODE: *.MessageField|undefined:"
                    + " (undefined).getReadonlyFoo().getMessageField()",
                "    METHOD_CALL_NODE: *.Foo: (undefined).getReadonlyFoo()",
                "      GROUP_NODE: *.MessageField|undefined: (undefined)",
                "        UNDEFINED_NODE: undefined: undefined",
                ""));
    assertThat(buildAstStringWithPreview(((NullSafeAccessNode) expr).asAccessChain()))
        .isEqualTo(
            NEWLINE.join(
                "METHOD_CALL_NODE: *.MessageField|undefined:"
                    + " $foo.getMessageField()?.getReadonlyFoo().getMessageField()",
                "  METHOD_CALL_NODE: *.Foo|undefined: $foo.getMessageField()?.getReadonlyFoo()",
                "    METHOD_CALL_NODE: *.MessageField|undefined: $foo.getMessageField()",
                "      VAR_REF_NODE: *.Foo: $foo",
                ""));
  }

  @Test
  public void testNonNullAssertionChain() {
    ExprNode expr =
        new ExpressionParser("$foo?.getMessageField()!")
            .withProto(Foo.getDescriptor())
            .withParam("foo", "Foo")
            .parse();
    assertThat(buildAstStringWithPreview(expr))
        .isEqualTo(
            NEWLINE.join(
                "NULL_SAFE_ACCESS_NODE: *.MessageField: $foo?.getMessageField()!",
                "  VAR_REF_NODE: *.Foo: $foo",
                "  ASSERT_NON_NULL_OP_NODE: *.MessageField: (undefined).getMessageField()!",
                "    METHOD_CALL_NODE: *.MessageField|undefined: (undefined).getMessageField()",
                "      GROUP_NODE: *.Foo: (undefined)",
                "        UNDEFINED_NODE: undefined: undefined",
                ""));

    expr =
        new ExpressionParser("$foo!.getMessageField()?.getFoo()")
            .withProto(Foo.getDescriptor())
            .withParam("foo", "Foo")
            .parse();
    assertThat(buildAstStringWithPreview(expr))
        .isEqualTo(
            NEWLINE.join(
                "NULL_SAFE_ACCESS_NODE: *.Foo|undefined: $foo.getMessageField()?.getFoo()",
                "  METHOD_CALL_NODE: *.MessageField|undefined: $foo.getMessageField()",
                "    VAR_REF_NODE: *.Foo: $foo",
                "  METHOD_CALL_NODE: *.Foo|undefined: (undefined).getFoo()",
                "    GROUP_NODE: *.MessageField|undefined: (undefined)",
                "      UNDEFINED_NODE: undefined: undefined",
                ""));
  }

  @Test
  public void testAsNullSafeBaseList() {
    ExprNode node =
        new ExpressionParser("$foo?.getMessageField()?.getFoo()?.getMessageField()")
            .withProto(Foo.getDescriptor())
            .withParam("foo", "Foo")
            .parse();
    NullSafeAccessNode nsan = (NullSafeAccessNode) node;
    List<String> sources =
        nsan.asNullSafeBaseList().stream()
            .map(ExprNode::toSourceString)
            .collect(Collectors.toList());
    assertThat(sources)
        .containsExactly(
            "$foo",
            "$foo.getMessageField()",
            "$foo.getMessageField().getFoo()",
            "$foo.getMessageField().getFoo().getMessageField()");
  }


}
