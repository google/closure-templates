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
                ValidatedLoggingConfig.create(
                    LoggingConfig.newBuilder()
                        .addElement(LoggableElement.newBuilder().setName("%%%"))
                        .build()));
    assertThat(expected).hasMessageThat().isEqualTo("'%%%' is not a valid identifier");
  }

  @Test
  public void testLoggingValidation_duplicateNames() {
    IllegalArgumentException expected =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ValidatedLoggingConfig.create(
                    LoggingConfig.newBuilder()
                        .addElement(LoggableElement.newBuilder().setName("Foo").setId(1))
                        .addElement(LoggableElement.newBuilder().setName("Foo").setId(2))
                        .build()));
    assertThat(expected)
        .hasMessageThat()
        .isEqualTo("Found 2 LoggableElements with the same name Foo, their ids are 1 and 2");
  }

  @Test
  public void testLoggingValidation_duplicateIds() {
    IllegalArgumentException expected =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ValidatedLoggingConfig.create(
                    LoggingConfig.newBuilder()
                        .addElement(LoggableElement.newBuilder().setName("Foo").setId(1))
                        .addElement(LoggableElement.newBuilder().setName("Bar").setId(1))
                        .build()));
    assertThat(expected)
        .hasMessageThat()
        .isEqualTo("Found 2 LoggableElements with the same id 1: Foo and Bar");
  }

  @Test
  public void testLoggingValidation_perfectDuplicates() {
    ValidatedLoggingConfig.create(
        LoggingConfig.newBuilder()
            .addElement(LoggableElement.newBuilder().setName("Foo").setId(1))
            .addElement(LoggableElement.newBuilder().setName("Foo").setId(1))
            .build());
  }

  @Test
  public void testLoggingValidation_idValueEdgeCases() {
    ValidatedLoggingConfig.create(
        LoggingConfig.newBuilder()
            .addElement(LoggableElement.newBuilder().setName("Foo").setId(-9007199254740991L))
            .addElement(LoggableElement.newBuilder().setName("Bar").setId(9007199254740991L))
            .build());
  }

  @Test
  public void testLoggingValidation_idTooLarge() {
    IllegalArgumentException expected =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ValidatedLoggingConfig.create(
                    LoggingConfig.newBuilder()
                        .addElement(
                            LoggableElement.newBuilder().setName("Foo").setId(9007199254740992L))
                        .build()));
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
                ValidatedLoggingConfig.create(
                    LoggingConfig.newBuilder()
                        .addElement(
                            LoggableElement.newBuilder().setName("Foo").setId(-9007199254740992L))
                        .build()));
    assertThat(expected)
        .hasMessageThat()
        .isEqualTo(
            "ID -9007199254740992 for 'Foo' must be between -9007199254740991 and 9007199254740991 "
                + "(inclusive).");
  }
}
