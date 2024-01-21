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
import com.google.template.soy.testing.Collisions;
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
        Collisions.getDescriptor(),
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
    assertThatSoyExpr(expr("$proto.getKeyOrUndefined()").withParam("{@param proto: KvPair}"))
        .withProtoImports(DESCRIPTORS)
        .generatesCode("opt_data.proto.getKeyOrUndefined();");
  }

  @Test
  public void testInnerMessage() {
    assertThatSoyExpr(
            expr("$proto.getFieldOrUndefined()")
                .withParam("{@param proto : ExampleExtendable.InnerMessage}"))
        .withProtoImports(DESCRIPTORS)
        .generatesCode("opt_data.proto.getFieldOrUndefined();");
  }

  @Test
  public void testMath() {
    assertThatSoyExpr(
            expr("$pair.getAnotherValueOrUndefined() * 5").withParam("{@param pair : KvPair}"))
        .withProtoImports(DESCRIPTORS)
        .generatesCode("opt_data.pair.getAnotherValueOrUndefined() * 5;")
        .withPrecedence(TIMES);
  }

  @Test
  public void testProto3Fields_int() {
    assertThatSoyExpr(expr("$msg.getIntField() * 5").withParam("{@param msg : Proto3Message}"))
        .withProtoImports(DESCRIPTORS)
        .generatesCode("opt_data.msg.getIntField() * 5;")
        .withPrecedence(TIMES);
  }

  @Test
  public void testProto3Fields_oneof() {
    assertThatSoyExpr(
            expr("$msg.getAnotherMessageField()!.getField() * 5")
                .withParam("{@param msg: Proto3Message}"))
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
        .generatesCode("opt_data.proto.getExtension($proto$example$someIntExtension);");
    assertThatSoyExpr(
            expr("$proto.getExtension(listExtensionList)")
                .withParam("{@param proto: ExampleExtendable}"))
        .withProtoImports(DESCRIPTORS)
        .generatesCode("opt_data.proto.getExtension($proto$example$listExtensionList);");
  }

  // Proto initialization tests
}
