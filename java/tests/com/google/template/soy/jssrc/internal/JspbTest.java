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

import static com.google.common.truth.Truth.assertThat;
import static com.google.template.soy.exprtree.Operator.TIMES;
import static com.google.template.soy.jssrc.internal.JsSrcSubject.assertThatSoyExpr;
import static com.google.template.soy.jssrc.internal.JsSrcSubject.expr;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.testing.Example;
import com.google.template.soy.testing.ExampleExtendable;
import com.google.template.soy.testing.Foo;
import com.google.template.soy.testing.KvPair;
import com.google.template.soy.testing.Proto3Message;
import com.google.template.soy.testing.SomeExtension;
import com.google.template.soy.types.SoyTypeRegistry;
import java.util.List;
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

  private static final SoyTypeRegistry REGISTRY =
      new SoyTypeRegistry.Builder()
          .addDescriptors(
              ImmutableList.of(
                  Example.getDescriptor(),
                  ExampleExtendable.getDescriptor(),
                  ExampleExtendable.InnerMessage.getDescriptor(),
                  KvPair.getDescriptor(),
                  Proto3Message.getDescriptor(),
                  SomeExtension.getDescriptor(),
                  Foo.getDescriptor()))
          .build();

  // Proto field access tests

  @Test
  public void testProtoMessage() {
    assertThatSoyExpr(expr("$protoMessage").withParam("{@param protoMessage: soy.test.Foo}"))
        .withTypeRegistry(REGISTRY)
        .generatesCode("opt_data.protoMessage;");
  }

  @Test
  public void testProtoEnum() {
    assertThatSoyExpr(expr("$protoEnum").withParam("{@param protoEnum: soy.test.Foo.InnerEnum}"))
        .withTypeRegistry(REGISTRY)
        .generatesCode("opt_data.protoEnum;");
  }

  @Test
  public void testSimpleProto() {
    assertThatSoyExpr(expr("$proto.key").withParam("{@param proto: example.KvPair}"))
        .withTypeRegistry(REGISTRY)
        .generatesCode("opt_data.proto.getKey();");
  }

  @Test
  public void testInnerMessage() {
    assertThatSoyExpr(
            expr("$proto.field")
                .withParam("{@param proto : example.ExampleExtendable.InnerMessage}"))
        .withTypeRegistry(REGISTRY)
        .generatesCode("opt_data.proto.getField();");
  }

  @Test
  public void testMath() {
    assertThatSoyExpr(expr("$pair.anotherValue * 5").withParam("{@param pair : example.KvPair}"))
        .withTypeRegistry(REGISTRY)
        .generatesCode("opt_data.pair.getAnotherValue() * 5;")
        .withPrecedence(TIMES);
  }

  @Test
  public void testProto3Fields_int() {
    assertThatSoyExpr(expr("$msg.intField * 5").withParam("{@param msg : soy.test.Proto3Message}"))
        .withTypeRegistry(REGISTRY)
        .generatesCode("opt_data.msg.getIntField() * 5;")
        .withPrecedence(TIMES);
  }

  @Test
  public void testProto3Fields_oneof() {
    assertThatSoyExpr(
            expr("$msg.anotherMessageField.field * 5")
                .withParam("{@param msg: soy.test.Proto3Message}"))
        .withTypeRegistry(REGISTRY)
        .generatesCode("opt_data.msg.getAnotherMessageField().getField() * 5;")
        .withPrecedence(TIMES);
  }

  // Proto initialization tests

  @Test
  public void testProtoInit_empty() {
    assertThatSoyExpr("example.ExampleExtendable()")
        .withTypeRegistry(REGISTRY)
        .generatesCode("new proto.example.ExampleExtendable();");
  }

  @Test
  public void testProtoInit_messageField() {
    assertThatSoyExpr(
            "example.ExampleExtendable(",
            "  someEmbeddedMessage:",
            "    example.SomeEmbeddedMessage(someEmbeddedNum: 1000)",
            "  )")
        .withTypeRegistry(REGISTRY)
        .generatesCode(
            "new proto.example.ExampleExtendable().setSomeEmbeddedMessage(new"
                + " proto.example.SomeEmbeddedMessage().setSomeEmbeddedNum(1000));");

    assertThatSoyExpr(
            expr("example.ExampleExtendable(someEmbeddedMessage: $e)")
                .withParam("{@param e: example.SomeEmbeddedMessage}"))
        .withTypeRegistry(REGISTRY)
        .generatesCode("new proto.example.ExampleExtendable().setSomeEmbeddedMessage(opt_data.e);");
  }

  @Test
  public void testProtoInit_enumField() {
    assertThatSoyExpr("example.ExampleExtendable(someEnum: example.SomeEnum.SECOND)")
        .withTypeRegistry(REGISTRY)
        .generatesCode(
            "new proto.example.ExampleExtendable().setSomeEnum(/** @type {?proto.example.SomeEnum}"
                + " */ (2));");

    assertThatSoyExpr(
            expr("example.ExampleExtendable(someEnum: $e)")
                .withParam("{@param e: example.SomeEnum}"))
        .withTypeRegistry(REGISTRY)
        .generatesCode(
            "new proto.example.ExampleExtendable().setSomeEnum(/** @type"
                + " {?proto.example.SomeEnum} */ (opt_data.e));");
  }

  @Test
  public void testProtoInit_repeatedField() {
    assertThatSoyExpr("example.ExampleExtendable(repeatedLongWithInt52JsTypeList: [1000, 2000])")
        .withTypeRegistry(REGISTRY)
        .generatesCode(
            "new proto.example.ExampleExtendable().setRepeatedLongWithInt52JsTypeList([1000,"
                + " 2000]);");

    assertThatSoyExpr(
            expr("example.ExampleExtendable(repeatedLongWithInt52JsTypeList: $l)")
                .withParam("{@param l: list<int>}"))
        .withTypeRegistry(REGISTRY)
        .generatesCode(
            "new proto.example.ExampleExtendable()"
                + ".setRepeatedLongWithInt52JsTypeList(opt_data.l);");
  }

  @Test
  public void testProtoInit_extensionField() {
    assertThatSoyExpr("example.ExampleExtendable(someIntExtension: 1000)")
        .withTypeRegistry(REGISTRY)
        .generatesCode(
            "new proto.example.ExampleExtendable().setExtension(proto.example.someIntExtension,"
                + " 1000);");

    assertThatSoyExpr(
            expr("example.ExampleExtendable(someIntExtension: $i)").withParam("{@param i: int}"))
        .withTypeRegistry(REGISTRY)
        .generatesCode(
            "new proto.example.ExampleExtendable().setExtension(proto.example.someIntExtension,"
                + " opt_data.i);");
  }

  @Test
  public void testProtoInit_extensionRepeatedField() {
    assertThatSoyExpr("example.ExampleExtendable(listExtensionList: [1000, 2000, 3000])")
        .withTypeRegistry(REGISTRY)
        .generatesCode(
            "new proto.example.ExampleExtendable().setExtension(proto.example.listExtensionList,"
                + " [1000, 2000, 3000]);");

    assertThatSoyExpr(
            expr("example.ExampleExtendable(listExtensionList: $l)")
                .withParam("{@param l: list<int>}"))
        .withTypeRegistry(REGISTRY)
        .generatesCode(
            "new proto.example.ExampleExtendable().setExtension(proto.example.listExtensionList,"
                + " opt_data.l);");
  }

  // Proto import tests

  /**
   * Test to check that soy code that accesses proto fields, correctly generate JS that includes
   * imports for the field types.
   */
  @Test
  public void testHeaderParamFieldImport() {
    SoyJsSrcOptions jsSrcOptions = new SoyJsSrcOptions();
    jsSrcOptions.setShouldProvideRequireSoyNamespaces(true);

    GenJsCodeVisitor genJsCodeVisitor =
        JsSrcMain.createVisitor(
            jsSrcOptions, new SoyTypeRegistry(), BidiGlobalDir.LTR, ErrorReporter.exploding());
    genJsCodeVisitor.jsCodeBuilder = new JsCodeBuilder();

    String testFileContent =
        "{namespace boo.foo}\n"
            + "\n"
            + "{template .goo}\n"
            + "  {@param moo : example.ExampleExtendable}\n"
            + "  {$moo.someExtensionField}\n"
            + "{/template}\n";

    ParseResult parseResult =
        SoyFileSetParserBuilder.forFileContents(testFileContent)
            .typeRegistry(REGISTRY)
            .parse();

    // Verify that the import symbol got required.
    String expectedJsFileContentStart =
        "// This file was automatically generated by the Soy compiler.\n"
            + "// Please don't edit this file by hand.\n"
            + "// source: no-path\n"
            + "\n"
            + "/**\n"
            + " * @fileoverview Templates in namespace boo.foo.\n"
            + " * @public\n"
            + " */\n"
            + "\n"
            + "goog.provide('boo.foo');\n"
            + "\n"
            + "goog.requireType('goog.soy');\n"
            + "goog.require('proto.example.ExampleExtendable');\n"
            + "goog.require('proto.example.SomeExtension');\n"
            + "goog.require('soy.asserts');\n"
            + "goog.require('soydata.VERY_UNSAFE');\n"
            + "\n"
            + "\n"
            + "/**\n"
            + " * @param {boo.foo.goo.Params} opt_data\n"
            + " * @param {(?goog.soy.IjData|?Object<string, *>)=} opt_ijData\n"
            + " * @param {(?goog.soy.IjData|?Object<string, *>)=} opt_ijData_deprecated\n"
            + " * @return {!goog.soy.data.SanitizedHtml}\n"
            + " * @suppress {checkTypes}\n"
            + " */\n"
            + "boo.foo.goo = function(opt_data, opt_ijData, opt_ijData_deprecated) {\n"
            + "  opt_ijData = /** @type {!goog.soy.IjData} */ (opt_ijData_deprecated ||"
            + " opt_ijData);\n"
            + "  var $tmp = opt_data.moo && (/** @type {?} */ (opt_data.moo).$jspbMessageInstance"
            + " || opt_data.moo);\n"
            + "  /** @type {proto.example.ExampleExtendable} */\n"
            + "  var moo = soy.asserts.assertType($tmp instanceof proto.example.ExampleExtendable,"
            + " 'moo', $tmp, 'proto.example.ExampleExtendable');\n"
            + "  return"
            + " soydata.VERY_UNSAFE.ordainSanitizedHtml("
            + "moo.getExtension(proto.example.SomeExtension.someExtensionField));\n"
            + "};\n"
            + "/**\n"
            + " * @typedef {{\n"
            + " *  moo: proto.example.ExampleExtendable,\n"
            + " * }}\n"
            + " */\n"
            + "boo.foo.goo.Params;\n"
            + "if (goog.DEBUG) {\n"
            + "  boo.foo.goo.soyTemplateName = 'boo.foo.goo';\n"
            + "}\n";

    List<String> jsFilesContents =
        genJsCodeVisitor.gen(
            parseResult.fileSet(), parseResult.registry(), ErrorReporter.exploding());
    assertThat(jsFilesContents.get(0)).isEqualTo(expectedJsFileContentStart);
  }
}
