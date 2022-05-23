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

package com.google.template.soy.logging;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.template.soy.logging.ValidatedLoggingConfig.ValidatedLoggableElement;
import com.google.template.soy.logging.testing.LoggingConfigs;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ValidatedLoggingConfigTest {
  @Test
  public void testLoggingValidation_badName() {
    IllegalArgumentException expected =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                LoggingConfigs.createLoggingConfig(
                    LoggableElement.newBuilder().setName("%%%").build()));
    assertThat(expected).hasMessageThat().isEqualTo("'%%%' is not a valid identifier");
  }

  @Test
  public void testLoggingValidation_duplicateNames() {
    IllegalArgumentException expected =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                LoggingConfigs.createLoggingConfig(
                    LoggableElement.newBuilder().setName("Foo").setId(287545).build(),
                    LoggableElement.newBuilder().setName("Foo").setId(923456).build()));
    assertThat(expected)
        .hasMessageThat()
        .startsWith("Found 2 LoggableElements with the same name Foo:");
    assertThat(expected).hasMessageThat().contains("287545");
    assertThat(expected).hasMessageThat().contains("923456");
  }

  @Test
  public void testLoggingValidation_duplicateIds() {
    IllegalArgumentException expected =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                LoggingConfigs.createLoggingConfig(
                    LoggableElement.newBuilder().setName("Foo").setId(1).build(),
                    LoggableElement.newBuilder().setName("Bar").setId(1).build()));
    assertThat(expected)
        .hasMessageThat()
        .startsWith("Found 2 LoggableElements with the same id 1:");
    assertThat(expected).hasMessageThat().contains("Foo");
    assertThat(expected).hasMessageThat().contains("Bar");
  }

  @Test
  public void testLoggingValidation_perfectDuplicates() {
    LoggingConfigs.createLoggingConfig(
        LoggableElement.newBuilder().setName("Foo").setId(1).build(),
        LoggableElement.newBuilder().setName("Foo").setId(1).build());
  }

  @Test
  public void testLoggingValidation_idValueEdgeCases() {
    LoggingConfigs.createLoggingConfig(
        LoggableElement.newBuilder().setName("Foo").setId(-9007199254740991L).build(),
        LoggableElement.newBuilder().setName("Bar").setId(9007199254740991L).build());
  }

  @Test
  public void testLoggingValidation_idTooLarge() {
    IllegalArgumentException expected =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                LoggingConfigs.createLoggingConfig(
                    LoggableElement.newBuilder().setName("Foo").setId(9007199254740992L).build()));
    assertThat(expected)
        .hasMessageThat()
        .isEqualTo(
            "ID 9007199254740992 for 'Foo' must be between -9007199254740991 and 9007199254740991 "
                + "(inclusive).");
  }

  @Test
  public void testLoggingValidation_idTooSmall() {
    IllegalArgumentException expected =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                LoggingConfigs.createLoggingConfig(
                    LoggableElement.newBuilder().setName("Foo").setId(-9007199254740992L).build()));
    assertThat(expected)
        .hasMessageThat()
        .isEqualTo(
            "ID -9007199254740992 for 'Foo' must be between -9007199254740991 and 9007199254740991 "
                + "(inclusive).");
  }

  @Test
  public void testLoggingValidation_duplicateUndefinedVe() {
    IllegalArgumentException expected =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                LoggingConfigs.createLoggingConfig(
                    LoggableElement.newBuilder()
                        .setId(-1)
                        .setName("BadVe")
                        .setProtoType("a.bad.Data")
                        .build()));
    assertThat(expected)
        .hasMessageThat()
        .startsWith("Found 2 LoggableElements with the same id -1:");
    assertThat(expected).hasMessageThat().contains("UndefinedVe");
    assertThat(expected).hasMessageThat().contains("BadVe");
  }

  @Test
  public void testLoggingValidation_undefinedVeWithMetadata() {
    IllegalStateException expected =
        assertThrows(
            IllegalStateException.class,
            () ->
                ValidatedLoggingConfig.create(
                    AnnotatedLoggingConfig.newBuilder()
                        .addElement(
                            AnnotatedLoggableElement.newBuilder()
                                .setElement(
                                    LoggableElement.newBuilder().setId(-1).setName("UndefinedVe"))
                                .setHasMetadata(true))
                        .build()));
    assertThat(expected).hasMessageThat().isEqualTo("UndefinedVe cannot have metadata.");
  }

  @Test
  public void testLoggingValidation_allowsDuplicatesWithDifferentMetadataDetails() {
    LoggingConfigs.createLoggingConfig(
        AnnotatedLoggableElement.newBuilder()
            .setElement(LoggableElement.newBuilder().setId(23786).setName("AnElement"))
            .setHasMetadata(true)
            .setJavaPackage("some.java.package")
            .setJsPackage("root.some.js.package")
            .setClassName("SomeClass")
            .build(),
        AnnotatedLoggableElement.newBuilder()
            .setElement(LoggableElement.newBuilder().setId(23786).setName("AnElement"))
            .setHasMetadata(true)
            .setJavaPackage("different.java.package")
            .setJsPackage("root.another.js.package")
            .setClassName("DifferentClass")
            .build());
  }

  @Test
  public void testEmptyHasUndefinedVe() {
    assertThat(ValidatedLoggingConfig.EMPTY.allKnownIdentifiers()).containsExactly("UndefinedVe");
    assertThat(ValidatedLoggingConfig.EMPTY.getElement("UndefinedVe").getId()).isEqualTo(-1);
  }

  @Test
  public void testAnnotations() {
    ValidatedLoggingConfig config =
        LoggingConfigs.createLoggingConfig(
            AnnotatedLoggableElement.newBuilder()
                .setElement(LoggableElement.newBuilder().setName("First").setId(1).build())
                .setJavaPackage("test.java.package.first")
                .setJsPackage("root.test.js.packge.first.logging_config")
                .setClassName("JavaClassFirst")
                .build(),
            AnnotatedLoggableElement.newBuilder()
                .setElement(LoggableElement.newBuilder().setName("Second").setId(2).build())
                .setJavaPackage("test.java.package.second")
                .setJsPackage("root.test.js.packge.second.logging_config")
                .setClassName("JavaClassSecond")
                .build());

    ValidatedLoggableElement first = config.getElement("First");
    assertThat(first.getName()).isEqualTo("First");
    assertThat(first.getId()).isEqualTo(1);
    assertThat(first.getJavaPackage()).isEqualTo("test.java.package.first");
    assertThat(first.getJsPackage()).isEqualTo("root.test.js.packge.first.logging_config");
    assertThat(first.getClassName()).isEqualTo("JavaClassFirst");

    ValidatedLoggableElement second = config.getElement("Second");
    assertThat(second.getName()).isEqualTo("Second");
    assertThat(second.getId()).isEqualTo(2);
    assertThat(second.getJavaPackage()).isEqualTo("test.java.package.second");
    assertThat(second.getJsPackage()).isEqualTo("root.test.js.packge.second.logging_config");
    assertThat(second.getClassName()).isEqualTo("JavaClassSecond");
  }
}
