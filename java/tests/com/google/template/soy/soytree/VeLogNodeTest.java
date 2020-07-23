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

import com.google.common.collect.Iterables;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.logging.LoggableElement;
import com.google.template.soy.logging.testing.LoggingConfigs;
import com.google.template.soy.testing.Foo;
import com.google.template.soy.testing.SharedTestUtils;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class VeLogNodeTest {

  @Test
  public void testParsing_justName() {
    VeLogNode logNode = parseVeLog("{velog Bar}<div></div>{/velog}");

    assertThat(logNode.toSourceString())
        .isEqualTo("{velog ve_data(ve(Bar), null)}<div></div>{/velog}");
    assertThat(logNode.getLogonlyExpression()).isNull();
  }

  @Test
  public void testClone() {
    VeLogNode logNode = parseVeLog("{velog Bar}<div></div>{/velog}");

    assertThat(logNode.toSourceString())
        .isEqualTo("{velog ve_data(ve(Bar), null)}<div></div>{/velog}");

    VeLogNode copy = logNode.copy(new CopyState());
    assertThat(copy.toSourceString())
        .isEqualTo("{velog ve_data(ve(Bar), null)}<div></div>{/velog}");
  }

  @Test
  public void testParsing_configExpression() {
    VeLogNode logNode = parseVeLog("{velog ve_data(Bar, Foo())}<div></div>{/velog}");

    assertThat(logNode.toSourceString())
        .isEqualTo("{velog ve_data(ve(Bar), Foo())}<div></div>{/velog}");
    assertThat(logNode.getLogonlyExpression()).isNull();
  }

  @Test
  public void testParsing_logonly() {
    VeLogNode logNode = parseVeLog("{velog Bar logonly=\"false\"}<div></div>{/velog}");

    assertThat(logNode.toSourceString())
        .isEqualTo("{velog ve_data(ve(Bar), null) logonly=\"false\"}<div></div>{/velog}");
    assertThat(logNode.getLogonlyExpression().toSourceString()).isEqualTo("false");
  }

  @Test
  public void testParsing_configAndLogonly() {
    VeLogNode logNode =
        parseVeLog("{velog ve_data(Bar, Foo()) logonly=\"false\"}<div></div>{/velog}");

    assertThat(logNode.toSourceString())
        .isEqualTo("{velog ve_data(ve(Bar), Foo()) logonly=\"false\"}<div></div>{/velog}");
    assertThat(logNode.getLogonlyExpression().toSourceString()).isEqualTo("false");
  }

  private VeLogNode parseVeLog(String veLog) {
    return parseVeLog(veLog, ErrorReporter.exploding());
  }

  private VeLogNode parseVeLog(String veLog, ErrorReporter reporter) {
    return Iterables.getOnlyElement(
        SoyTreeUtils.getAllNodesOfType(
            SoyFileSetParserBuilder.forTemplateContents(true, veLog)
                .typeRegistry(SharedTestUtils.importing(Foo.getDescriptor()))
                .setLoggingConfig(
                    LoggingConfigs.createLoggingConfig(
                        LoggableElement.newBuilder()
                            .setName("Bar")
                            .setId(1L)
                            .setProtoType("soy.test.Foo")
                            .build()))
                .errorReporter(reporter)
                .parse()
                .fileSet(),
            VeLogNode.class));
  }
}
