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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.protobuf.Descriptors.GenericDescriptor;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.LoggingAdvisingAppendable.BufferingAppendable;
import com.google.template.soy.data.internal.ParamStore;
import com.google.template.soy.jbcsrc.TemplateTester.CompiledTemplateSubject;
import com.google.template.soy.jbcsrc.shared.CompiledTemplate;
import com.google.template.soy.jbcsrc.shared.CompiledTemplates;
import com.google.template.soy.testing.Example;
import com.google.template.soy.testing.ExampleExtendable;
import com.google.template.soy.testing.KvPair;
import com.google.template.soy.testing.ProtoMap;
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
            "{$proto.getKeyOrUndefined()}{\\n}",
            "{$proto.getValueOrUndefined()}{\\n}",
            "{$proto.getAnotherValueOrUndefined()}")
        .rendersAs(
            "key\nvalue\n3",
            ImmutableMap.of(
                "proto",
                KvPair.newBuilder().setKey("key").setValue("value").setAnotherValue(3).build()));
  }

  private CompiledTemplateSubject assertThatTemplateBody(String... body) {
    try {
      SoyFileSetParserBuilder builder =
          SoyFileSetParserBuilder.forTemplateAndImports(
              "{template foo}\n" + Joiner.on("\n").join(body) + "\n{/template}\n", descriptors);
      return TemplateTester.assertThatFile(
              Iterables.getOnlyElement(builder.build().soyFileSuppliers().values())
                  .asCharSource()
                  .read())
          .withTypeRegistry(builder.getTypeRegistry());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private String render(CompiledTemplates templates, String name, ParamStore params) {
    CompiledTemplate caller = templates.getTemplate(name);
    BufferingAppendable sb = LoggingAdvisingAppendable.buffering();
    try {
      assertThat(caller.render(null, params, sb, getDefaultContext(templates))).isNull();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return sb.toString();
  }
}
