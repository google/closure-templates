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

package com.google.template.soy.jssrc.internal;

import static com.google.template.soy.exprtree.Operator.TIMES;
import static com.google.template.soy.jssrc.internal.JsSrcSubject.assertThatSoyExpr;
import static com.google.template.soy.jssrc.internal.JsSrcSubject.expr;

import com.google.protobuf.Descriptors.GenericDescriptor;
import com.google.template.soy.testing.Example;
import com.google.template.soy.testing.ExampleExtendable;
import com.google.template.soy.testing.Foo;
import com.google.template.soy.testing.KvPair;
import com.google.template.soy.testing.SomeEmbeddedMessage;
import com.google.template.soy.testing.SomeEnum;
import com.google.template.soy.testing3.Proto3Message;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for protobuf-related code generation in JS.
 *
 * <p>Quarantined here until the Soy open source project can compile protos.
 *
 * <p>The test cases are mostly ported from {@link com.google.template.soy.jbcsrc.ProtoSupportTest},
 * but unlike that test, the assertions are about the generated code, not the final rendered output.
 */
@RunWith(JUnit4.class)
public final class JspbTest {

  private static final GenericDescriptor[] DESCRIPTORS =
      new GenericDescriptor[] {
        Example.getDescriptor(),
        ExampleExtendable.getDescriptor(),
        Proto3Message.getDescriptor(),
        Foo.getDescriptor(),
        KvPair.getDescriptor(),
        SomeEmbeddedMessage.getDescriptor(),
        SomeEnum.getDescriptor(),
        Example.listExtension.getDescriptor(),
        Example.someIntExtension.getDescriptor()
      };

  // Proto field access tests

  @Test
  public void testProtoMessage() {
    assertThatSoyExpr(expr("$protoMessage").withParam("{@param protoMessage: Foo}"))
        .withProtoImports(DESCRIPTORS)
        .generatesCode("opt_data.protoMessage;");
  }

  @Test
  public void testProtoEnum() {
    assertThatSoyExpr(expr("$protoEnum").withParam("{@param protoEnum: Foo.InnerEnum}"))
        .withProtoImports(DESCRIPTORS)
        .generatesCode("opt_data.protoEnum;");
  }

  @Test
  public void testSimpleProto() {
    assertThatSoyExpr(expr("$proto.key").withParam("{@param proto: KvPair}"))
        .withProtoImports(DESCRIPTORS)
        .generatesCode("opt_data.proto.getKey();");
  }

  @Test
  public void testInnerMessage() {
    assertThatSoyExpr(
            expr("$proto.field").withParam("{@param proto : ExampleExtendable.InnerMessage}"))
        .withProtoImports(DESCRIPTORS)
        .generatesCode("opt_data.proto.getField();");
  }

  @Test
  public void testMath() {
    assertThatSoyExpr(expr("$pair.anotherValue * 5").withParam("{@param pair : KvPair}"))
        .withProtoImports(DESCRIPTORS)
        .generatesCode("opt_data.pair.getAnotherValue() * 5;")
        .withPrecedence(TIMES);
  }

  @Test
  public void testProto3Fields_int() {
    assertThatSoyExpr(expr("$msg.intField * 5").withParam("{@param msg : Proto3Message}"))
        .withProtoImports(DESCRIPTORS)
        .generatesCode("opt_data.msg.getIntField() * 5;")
        .withPrecedence(TIMES);
  }

  @Test
  public void testProto3Fields_oneof() {
    assertThatSoyExpr(
            expr("$msg.anotherMessageField.field * 5").withParam("{@param msg: Proto3Message}"))
        .withProtoImports(DESCRIPTORS)
        .generatesCode("opt_data.msg.getAnotherMessageField().getField() * 5;")
        .withPrecedence(TIMES);
  }

  @Test
  public void testGetExtension() {
    assertThatSoyExpr(
            expr("$proto.getExtension(someIntExtension)")
                .withParam("{@param proto: ExampleExtendable}"))
        .withProtoImports(DESCRIPTORS)
        .generatesCode("opt_data.proto.getExtension(proto.example.someIntExtension);");
    assertThatSoyExpr(
            expr("$proto.getExtension(listExtensionList)")
                .withParam("{@param proto: ExampleExtendable}"))
        .withProtoImports(DESCRIPTORS)
        .generatesCode("opt_data.proto.getExtension(proto.example.listExtensionList);");
  }

  // Proto initialization tests

  @Test
  public void testProtoInit_empty() {
    assertThatSoyExpr("ExampleExtendable()")
        .withProtoImports(DESCRIPTORS)
        .generatesCode("new proto.example.ExampleExtendable();");
  }

  @Test
  public void testProtoInit_messageField() {
    assertThatSoyExpr(
            "ExampleExtendable(",
            "  someEmbeddedMessage:",
            "    SomeEmbeddedMessage(someEmbeddedNum: 1000)",
            "  )")
        .withProtoImports(DESCRIPTORS)
        .generatesCode(
            "new proto.example.ExampleExtendable().setSomeEmbeddedMessage(new"
                + " proto.example.SomeEmbeddedMessage().setSomeEmbeddedNum(1000));");

    assertThatSoyExpr(
            expr("ExampleExtendable(someEmbeddedMessage: $e)")
                .withParam("{@param e: SomeEmbeddedMessage}"))
        .withProtoImports(DESCRIPTORS)
        .generatesCode("new proto.example.ExampleExtendable().setSomeEmbeddedMessage(opt_data.e);");
  }

  @Test
  public void testProtoInit_enumField() {
    assertThatSoyExpr("ExampleExtendable(someEnum: SomeEnum.SECOND)")
        .withProtoImports(DESCRIPTORS)
        .generatesCode(
            "new proto.example.ExampleExtendable().setSomeEnum(/** @type {?proto.example.SomeEnum}"
                + " */ (2));");

    assertThatSoyExpr(expr("ExampleExtendable(someEnum: $e)").withParam("{@param e: SomeEnum}"))
        .withProtoImports(DESCRIPTORS)
        .generatesCode(
            "new proto.example.ExampleExtendable().setSomeEnum(/** @type"
                + " {?proto.example.SomeEnum} */ (opt_data.e));");
  }

  @Test
  public void testProtoInit_repeatedField() {
    assertThatSoyExpr("ExampleExtendable(repeatedLongWithInt52JsTypeList: [1000, 2000])")
        .withProtoImports(DESCRIPTORS)
        .generatesCode(
            "new proto.example.ExampleExtendable().setRepeatedLongWithInt52JsTypeList(soy.$$makeArray(1000,"
                + " 2000));");

    assertThatSoyExpr(
            expr("ExampleExtendable(repeatedLongWithInt52JsTypeList: $l)")
                .withParam("{@param l: list<int>}"))
        .withProtoImports(DESCRIPTORS)
        .generatesCode(
            "new proto.example.ExampleExtendable()"
                + ".setRepeatedLongWithInt52JsTypeList(opt_data.l);");
  }

  @Test
  public void testProtoInit_extensionField() {
    assertThatSoyExpr("ExampleExtendable(someIntExtension: 1000)")
        .withProtoImports(DESCRIPTORS)
        .generatesCode(
            "new proto.example.ExampleExtendable().setExtension(proto.example.someIntExtension,"
                + " 1000);");

    assertThatSoyExpr(expr("ExampleExtendable(someIntExtension: $i)").withParam("{@param i: int}"))
        .withProtoImports(DESCRIPTORS)
        .generatesCode(
            "new proto.example.ExampleExtendable().setExtension(proto.example.someIntExtension,"
                + " opt_data.i);");
  }

  @Test
  public void testProtoInit_extensionRepeatedField() {
    assertThatSoyExpr("ExampleExtendable(listExtensionList: [1000, 2000, 3000])")
        .withProtoImports(DESCRIPTORS)
        .generatesCode(
            "new proto.example.ExampleExtendable().setExtension(proto.example.listExtensionList,"
                + " soy.$$makeArray(1000, 2000, 3000));");

    assertThatSoyExpr(
            expr("ExampleExtendable(listExtensionList: $l)").withParam("{@param l: list<int>}"))
        .withProtoImports(DESCRIPTORS)
        .generatesCode(
            "new proto.example.ExampleExtendable().setExtension(proto.example.listExtensionList,"
                + " opt_data.l);");
  }
}
