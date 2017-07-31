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
package com.google.template.soy.soytree;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.logging.LoggableElement;
import com.google.template.soy.logging.LoggingConfig;
import com.google.template.soy.logging.ValidatedLoggingConfig;
import com.google.template.soy.shared.SoyGeneralOptions;
import com.google.template.soy.types.SoyTypeProvider;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.proto.SoyProtoTypeProvider;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class FooLogNodeTest {

  @Test
  public void testParsing_justName() {
    FooLogNode openTag = parseFooLog("{foolog Bar}{/foolog}");

    assertThat(openTag.toSourceString()).isEqualTo("{foolog Bar}{/foolog}");
    assertThat(openTag.getName().identifier()).isEqualTo("Bar");
    assertThat(openTag.getConfigExpression()).isNull();
    assertThat(openTag.getLogonlyExpression()).isNull();
  }

  @Test
  public void testParsing_configExpression() {
    FooLogNode openTag = parseFooLog("{foolog Bar data=\"soy.test.Foo()\"}{/foolog}");

    assertThat(openTag.toSourceString()).isEqualTo("{foolog Bar data=\"soy.test.Foo()\"}{/foolog}");
    assertThat(openTag.getName().identifier()).isEqualTo("Bar");
    assertThat(openTag.getConfigExpression().toSourceString()).isEqualTo("soy.test.Foo()");
    assertThat(openTag.getLogonlyExpression()).isNull();
  }

  @Test
  public void testParsing_logonly() {
    FooLogNode openTag = parseFooLog("{foolog Bar logonly=\"false\"}{/foolog}");

    assertThat(openTag.toSourceString()).isEqualTo("{foolog Bar logonly=\"false\"}{/foolog}");
    assertThat(openTag.getName().identifier()).isEqualTo("Bar");
    assertThat(openTag.getConfigExpression()).isNull();
    assertThat(openTag.getLogonlyExpression().toSourceString()).isEqualTo("false");
  }

  @Test
  public void testParsing_configAndLogonly() {
    FooLogNode openTag =
        parseFooLog("{foolog Bar data=\"soy.test.Foo()\" logonly=\"false\"}{/foolog}");

    assertThat(openTag.toSourceString())
        .isEqualTo("{foolog Bar data=\"soy.test.Foo()\" logonly=\"false\"}{/foolog}");
    assertThat(openTag.getName().identifier()).isEqualTo("Bar");
    assertThat(openTag.getConfigExpression().toSourceString()).isEqualTo("soy.test.Foo()");
    assertThat(openTag.getLogonlyExpression().toSourceString()).isEqualTo("false");
  }

  @Test
  public void testExperimentEnforced() {
    ErrorReporter reporter = ErrorReporter.createForTest();
    parseFooLog("{foolog Bar}{/foolog}", false, reporter);
    assertThat(reporter.getErrors().get(0).message())
        .isEqualTo("The {foolog ...} command is disabled in this configuration.");
  }

  private FooLogNode parseFooLog(String fooLog) {
    return parseFooLog(fooLog, true, ErrorReporter.exploding());
  }

  private FooLogNode parseFooLog(String fooLog, boolean enabled, ErrorReporter reporter) {
    return Iterables.getOnlyElement(
        SoyTreeUtils.getAllNodesOfType(
            SoyFileSetParserBuilder.forTemplateContents(fooLog)
                .typeRegistry(
                    new SoyTypeRegistry(
                        ImmutableSet.<SoyTypeProvider>of(
                            new SoyProtoTypeProvider.Builder()
                                .addDescriptors(com.google.template.soy.testing.Foo.getDescriptor())
                                .buildNoFiles())))
                .setLoggingConfig(
                    ValidatedLoggingConfig.create(
                        LoggingConfig.newBuilder()
                            .addElement(
                                LoggableElement.newBuilder()
                                    .setName("Bar")
                                    .setId(1L)
                                    .setProtoType("soy.test.Foo")
                                    .build())
                            .build()))
                .options(
                    new SoyGeneralOptions()
                        .setExperimentalFeatures(
                            enabled ? Arrays.asList("logging_support") : ImmutableSet.<String>of()))
                .errorReporter(reporter)
                .parse()
                .fileSet(),
            FooLogNode.class));
  }
}
