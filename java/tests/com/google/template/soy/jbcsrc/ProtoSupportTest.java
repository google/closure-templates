/*
 * Copyright 2017 Google Inc.
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

package com.google.template.soy.jbcsrc;

import static com.google.common.truth.Truth.assertThat;
import static com.google.template.soy.jbcsrc.TemplateTester.getDefaultContext;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.GenericDescriptor;
import com.google.template.soy.SoyFileSetParser;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.LoggingAdvisingAppendable.BufferingAppendable;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.internal.ParamStore;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.jbcsrc.TemplateTester.CompiledTemplateSubject;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.jbcsrc.shared.CompiledTemplate;
import com.google.template.soy.jbcsrc.shared.CompiledTemplates;
import com.google.template.soy.testing.Example;
import com.google.template.soy.testing.ExampleExtendable;
import com.google.template.soy.testing.KvPair;
import com.google.template.soy.testing.ProtoMap;
import com.google.template.soy.testing.ProtoMap.InnerMessage;
import com.google.template.soy.testing.SomeEmbeddedMessage;
import com.google.template.soy.testing.SomeEnum;
import com.google.template.soy.testing.SomeExtension;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import com.google.template.soy.testing3.Proto3Message;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@code jbcsrc's} support for protocol buffers. Extracted into a separate test case to
 * make it easier to exclude from the open source build.
 */
@RunWith(JUnit4.class)
public final class ProtoSupportTest {

  private static final Joiner JOINER = Joiner.on('\n');

  private static final GenericDescriptor[] descriptors = {
    Example.getDescriptor(),
    ExampleExtendable.getDescriptor(),
    KvPair.getDescriptor(),
    ProtoMap.getDescriptor(),
    SomeEmbeddedMessage.getDescriptor(),
    SomeEnum.getDescriptor(),
    SomeExtension.getDescriptor(),
    Proto3Message.getDescriptor(),
    Example.someBoolExtension.getDescriptor(),
    Example.someIntExtension.getDescriptor(),
    Example.listExtension.getDescriptor()
  };

  @Test
  public void testSimpleProto() {
    assertThatTemplateBody(
            "{@param proto : KvPair}",
            "{$proto.key}{\\n}",
            "{$proto.value}{\\n}",
            "{$proto.anotherValue}")
        .rendersAs(
            "key\nvalue\n3",
            ImmutableMap.of(
                "proto",
                KvPair.newBuilder().setKey("key").setValue("value").setAnotherValue(3).build()));
  }

  @Test
  public void testSimpleProto_nullCoalescing() {
    assertThatTemplateBody("{@param? proto : KvPair}", "{$proto?.value ?: 'bar'}").rendersAs("bar");

    CompiledTemplateSubject tester =
        assertThatTemplateBody(
            "{@param proto : ProtoMap}", "{$proto.mapMessageFieldMap?[2390]?.field ?: 'bar'}");
    tester.rendersAs(
        "4837",
        ImmutableMap.of(
            "proto",
            ProtoMap.newBuilder()
                .putMapMessageField(2390, InnerMessage.newBuilder().setField(4837).build())));
    tester.rendersAs(
        "0",
        ImmutableMap.of(
            "proto",
            ProtoMap.newBuilder().putMapMessageField(2390, InnerMessage.getDefaultInstance())));
    tester.rendersAs("bar", ImmutableMap.of("proto", ProtoMap.getDefaultInstance()));

    assertThatTemplateBody(
            "{@param? proto : KvPair}",
            "{@param? proto2 : KvPair}",
            "{$proto?.value ?: $proto2?.value}")
        .rendersAs("null");
  }

  // The null safe accessor syntax introduces some complication for primitive proto accessors since
  // the types imply that the result should be a reference type which means we need to box return
  // values
  @Test
  public void testSimpleProto_nullSafePrimitive() {
    assertThatTemplateBody("{@param? proto : KvPair}", "{$proto?.anotherValue}").rendersAs("null");
    assertThatTemplateBody(
            "{@param? proto : ExampleExtendable}", "{if not $proto?.boolField}", "  foo", "{/if}")
        .rendersAs("foo");
  }

  @Test
  public void testSimpleProto_nullSafeReference() {
    CompiledTemplateSubject tester =
        assertThatTemplateBody(
            "{@param? proto : ExampleExtendable}",
            "{if $proto?.someEmbeddedMessage}",
            "  foo",
            "{/if}");
    tester.rendersAs("");
    tester.rendersAs("", ImmutableMap.of("proto", ExampleExtendable.getDefaultInstance()));
    tester.rendersAs(
        "foo",
        ImmutableMap.of(
            "proto",
            ExampleExtendable.newBuilder()
                .setSomeEmbeddedMessage(SomeEmbeddedMessage.getDefaultInstance())));
  }

  @Test
  public void testSimpleProto_nullSafeProtoLetVar() {
    CompiledTemplateSubject tester =
        assertThatTemplateBody(
            "{@param? proto : ExampleExtendable}",
            "{let $foo : $proto?.someEmbeddedMessage /}",
            "{$foo ? 'true' : 'false'}");
    tester.rendersAs("false", ImmutableMap.of());
    tester.rendersAs("false", ImmutableMap.of("proto", ExampleExtendable.getDefaultInstance()));
    tester.rendersAs(
        "true",
        ImmutableMap.of(
            "proto",
            ExampleExtendable.newBuilder()
                .setSomeEmbeddedMessage(SomeEmbeddedMessage.getDefaultInstance())));
  }

  @Test
  public void testSimpleProto_nullSafeStringLetVar() {
    CompiledTemplateSubject tester =
        assertThatTemplateBody(
            "{@param? proto : ExampleExtendable}",
            "{let $foo : $proto?.someEmbeddedMessage?.someEmbeddedString /}",
            "{$foo}");
    tester.rendersAs("null", ImmutableMap.of());
    tester.rendersAs("null", ImmutableMap.of("proto", ExampleExtendable.getDefaultInstance()));
    tester.rendersAs(
        "foo",
        ImmutableMap.of(
            "proto",
            ExampleExtendable.newBuilder()
                .setSomeEmbeddedMessage(
                    SomeEmbeddedMessage.newBuilder().setSomeEmbeddedString("foo").build())));
  }

  @Test
  public void testMathOnNullableValues() {
    CompiledTemplateSubject tester =
        assertThatTemplateBody(
            "{@param? proto : ExampleExtendable}",
            "{let $foo : $proto?.someEmbeddedMessage?.someEmbeddedString /}",
            "{$foo}");
    tester.rendersAs("null", ImmutableMap.of());
    tester.rendersAs("null", ImmutableMap.of("proto", ExampleExtendable.getDefaultInstance()));
    tester.rendersAs(
        "foo",
        ImmutableMap.of(
            "proto",
            ExampleExtendable.newBuilder()
                .setSomeEmbeddedMessage(
                    SomeEmbeddedMessage.newBuilder().setSomeEmbeddedString("foo").build())));
  }

  @Test
  public void testInnerMessageProto() {
    assertThatTemplateBody("{@param proto : ExampleExtendable.InnerMessage}", "{$proto.field}")
        .rendersAs(
            "12",
            ImmutableMap.of(
                "proto", ExampleExtendable.InnerMessage.newBuilder().setField(12).build()));
  }

  @Test
  public void testRepeatedFields() {
    assertThatTemplateBody(
            "{@param e : ExampleExtendable}",
            "{for $m in $e.repeatedEmbeddedMessageList}",
            "  {$m.someEmbeddedString}",
            "{/for}")
        .rendersAs(
            "k1k2",
            ImmutableMap.of(
                "e",
                ExampleExtendable.newBuilder()
                    .addRepeatedEmbeddedMessage(
                        SomeEmbeddedMessage.newBuilder().setSomeEmbeddedString("k1"))
                    .addRepeatedEmbeddedMessage(
                        SomeEmbeddedMessage.newBuilder().setSomeEmbeddedString("k2"))));
  }

  @Test
  public void testRepeatedFields_ofNullable() {
    assertThatTemplateBody(
            "{@param e : ExampleExtendable}",
            "{for $str in $e.someEmbeddedMessage.someEmbeddedRepeatedStringList}",
            "  {$str}",
            "{/for}")
        .rendersAs(
            "abc",
            ImmutableMap.of(
                "e",
                ExampleExtendable.newBuilder()
                    .setSomeEmbeddedMessage(
                        SomeEmbeddedMessage.newBuilder()
                            .addSomeEmbeddedRepeatedString("a")
                            .addSomeEmbeddedRepeatedString("b")
                            .addSomeEmbeddedRepeatedString("c"))))
        .failsToRenderWith(
            NullPointerException.class,
            ImmutableMap.of("e", ExampleExtendable.getDefaultInstance()));
  }

  @Test
  public void testMathOnProtoFields() {
    assertThatTemplateBody("{@param pair : KvPair}", "{$pair.anotherValue * 5}")
        .rendersAs("10", ImmutableMap.of("pair", KvPair.newBuilder().setAnotherValue(2)));
  }

  // tests for a bug in handling of ternaries
  @Test
  public void testPassingManipulatedFields() {
    String file =
        JOINER.join(
            "{template .caller}",
            "  {@param pair : KvPair}",
            "  {let $closeUrl : $pair.value /}",
            "  {call .callee}{param str : $closeUrl ? $closeUrl : '' /}{/call}",
            "{/template}",
            "",
            "{template .callee}",
            "  {@param? str : string}",
            "  {if $str}",
            "    {$str}",
            "  {/if}",
            "{/template}");
    SoyFileSetParserBuilder builder =
        SoyFileSetParserBuilder.forTemplateAndImports(file, descriptors);
    SoyFileSetParser parser = builder.build();
    ParseResult parseResult = parser.parse();
    CompiledTemplates templates =
        BytecodeCompiler.compile(
                parseResult.registry(),
                parseResult.fileSet(),
                ErrorReporter.exploding(),
                parser.soyFileSuppliers(),
                builder.getTypeRegistry())
            .get();
    render(
        templates,
        "ns.caller",
        (SoyRecord)
            SoyValueConverter.INSTANCE.convert(
                ImmutableMap.of("pair", KvPair.newBuilder().setAnotherValue(2))));
  }

  @Test
  public void testProto3Fields_int() {
    CompiledTemplateSubject tester =
        assertThatTemplateBody("{@param msg : Proto3Message}", "{$msg.intField * 5}");
    tester.rendersAs("10", ImmutableMap.of("msg", Proto3Message.newBuilder().setIntField(2)));
    tester.rendersAs("0", ImmutableMap.of("msg", Proto3Message.getDefaultInstance()));
  }

  @Test
  public void testProto3Fields_message() {
    assertThatTemplateBody("{@param msg : Proto3Message}", "{$msg.intField * 5}")
        .rendersAs("10", ImmutableMap.of("msg", Proto3Message.newBuilder().setIntField(2)));
  }

  @Test
  public void testProto3Fields_oneof() {
    assertThatTemplateBody("{@param msg: Proto3Message}", "{$msg.anotherMessageField.field * 5}")
        .rendersAs(
            "10",
            ImmutableMap.of(
                "msg",
                Proto3Message.newBuilder()
                    .setAnotherMessageField(Proto3Message.InnerMessage.newBuilder().setField(2))));
    assertThatTemplateBody("{@param msg: Proto3Message}", "{$msg.anotherIntField * 5}")
        .rendersAs("10", ImmutableMap.of("msg", Proto3Message.newBuilder().setAnotherIntField(2)))
        // missing int from a oneof returns 0
        .rendersAs("0", ImmutableMap.of("msg", Proto3Message.getDefaultInstance()));
    assertThatTemplateBody("{@param msg: Proto3Message}", "{$msg.anotherMessageField.field}")
        .failsToRenderWith(
            NullPointerException.class, ImmutableMap.of("msg", Proto3Message.getDefaultInstance()));
  }

  @Test
  public void testProto3Fields_enum() {
    // Test that unspecified enums don't cause crashes.
    // in proto2 this is a non-issue since unknown enum values automatically get mapped to 0 when
    // being parsed.
    assertThatTemplateBody(
            "{@param msg: Proto3Message}",
            "{$msg.anEnum} {$msg.anEnumsList}"
            )
        .rendersAs(
            "11 [12, 13]"
                + "",
            ImmutableMap.of(
                "msg",
                Proto3Message.newBuilder()
                    .setAnEnumValue(11) // N.B. none of these are valid enum values
                    .addAnEnumsValue(12)
                    .addAnEnumsValue(13)
                    .build()));
  }

  @Test
  public void testProtoInitByteString() {
    ExampleExtendable proto =
        ExampleExtendable.newBuilder()
            .setByteField(ByteString.copyFrom(new byte[] {0, 127, -128}))
            .build();

    assertThatTemplateBody(
            "{@param bs: ExampleExtendable}",
            "{let $p: ExampleExtendable(",
            "  byteField: $bs.byteField) /}",
            "{$p.byteField}")
        .rendersAs("AH+A", ImmutableMap.of("bs", proto));
  }

  @Test
  public void testProtoInitMessageConstants() {
    assertThatTemplateBody(
            "{let $p: ExampleExtendable(",
            "  someEmbeddedMessage:",
            "    SomeEmbeddedMessage(someEmbeddedNum: 1)",
            "  ) /}",
            "{$p}")
        .rendersAs(JOINER.join("some_embedded_message {", "  some_embedded_num: 1", "}", ""));
  }

  @Test
  public void testProtoInitMessageVars() {
    assertThatTemplateBody(
            "{@param e: SomeEmbeddedMessage}",
            "{let $p: ExampleExtendable(",
            "  someEmbeddedMessage: $e) /}",
            "{$p}")
        .rendersAs(
            JOINER.join("some_embedded_message {", "  some_embedded_num: 1", "}", ""),
            ImmutableMap.of("e", SomeEmbeddedMessage.newBuilder().setSomeEmbeddedNum(1).build()));
  }

  @Test
  public void testProtoInitEnumConstants() {
    assertThatTemplateBody("{let $p: ExampleExtendable(", "  someEnum: SomeEnum.SECOND) /}", "{$p}")
        .rendersAs(JOINER.join("some_enum: SECOND", ""));
  }

  @Test
  public void testProtoInitEnumVars() {
    assertThatTemplateBody(
            "{@param e: SomeEnum}", "{let $p: ExampleExtendable(", "  someEnum: $e) /}", "{$p}")
        .rendersAs(JOINER.join("some_enum: LAST", ""), ImmutableMap.of("e", SomeEnum.LAST));
  }

  @Test
  public void testProtoInitEnumBadEnum() {
    assertThatTemplateBody(
            "{@param e: ?}", "{let $p: ExampleExtendable(", "  someEnum: $e) /}", "{$p}")
        .failsToRenderWith(NullPointerException.class, ImmutableMap.of("e", 99999));
  }

  @Test
  public void testProtoInitRepeatedFieldConstants() {
    assertThatTemplateBody(
            "{let $p: ExampleExtendable(",
            "  repeatedLongWithInt52JsTypeList: [1000, 2000]) /}",
            "{$p.repeatedLongWithInt52JsTypeList}{\\n}",
            "{$p.repeatedLongWithInt52JsTypeList[0]}{\\n}",
            "{$p.repeatedLongWithInt52JsTypeList[1]}")
        .rendersAs(JOINER.join("[1000, 2000]", "1000", "2000"));
  }

  @Test
  public void testProtoInitRepeatedFieldVars() {
    assertThatTemplateBody(
            "{@param l: list<int>}",
            "{let $p: ExampleExtendable(",
            "  repeatedLongWithInt52JsTypeList: $l) /}",
            "{$p.repeatedLongWithInt52JsTypeList}{\\n}",
            "{$p.repeatedLongWithInt52JsTypeList[0]}{\\n}",
            "{$p.repeatedLongWithInt52JsTypeList[1]}")
        .rendersAs(
            JOINER.join("[1000, 2000]", "1000", "2000"),
            ImmutableMap.of("l", ImmutableList.of(1000, 2000)));
  }

  @Test
  public void testProtoInitRepeatedFieldNullList() {
    assertThatTemplateBody(
            "{@param? l: list<int>|null}",
            "{let $p: ExampleExtendable(",
            "  repeatedLongWithInt52JsTypeList: $l) /}",
            "{$p.repeatedLongWithInt52JsTypeList}")
        .rendersAs("[]");
  }

  @Test
  public void testProtoInitRepeatedFieldListWithNullElement() {
    assertThatTemplateBody(
            "{@param l: ?}",
            "{let $p: ExampleExtendable(repeatedLongWithInt52JsTypeList: $l) /}",
            "{$p.repeatedLongWithInt52JsTypeList}")
        .rendersAs("[]");
  }

  @FunctionalInterface
  interface TemplateRenderer {
    RenderResult render() throws IOException;
  }

  private CompiledTemplateSubject assertThatTemplateBody(String... body) {
    try {
      SoyFileSetParserBuilder builder =
          SoyFileSetParserBuilder.forTemplateAndImports(
              "{template .foo}\n" + Joiner.on("\n").join(body) + "\n{/template}\n", descriptors);
      return TemplateTester.assertThatFile(
              Iterables.getOnlyElement(builder.build().soyFileSuppliers().values())
                  .asCharSource()
                  .read())
          .withTypeRegistry(builder.getTypeRegistry());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private String render(CompiledTemplates templates, String name, SoyRecord params) {
    CompiledTemplate caller = templates.getTemplate(name);
    BufferingAppendable sb = LoggingAdvisingAppendable.buffering();
    try {
      assertThat(caller.render(params, ParamStore.EMPTY_INSTANCE, sb, getDefaultContext(templates)))
          .isEqualTo(RenderResult.done());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return sb.toString();
  }
}
