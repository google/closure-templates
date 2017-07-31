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
import static org.junit.Assert.fail;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ValidatedLoggingConfigTest {
  @Test
  public void testLoggingValidation_badName() {
    try {
      ValidatedLoggingConfig.create(
          LoggingConfig.newBuilder()
              .addElement(LoggableElement.newBuilder().setName("%%%"))
              .build());
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessageThat().isEqualTo("'%%%' is not a valid identifier");
    }
  }

  @Test
  public void testLoggingValidation_duplicateNames() {
    try {
      ValidatedLoggingConfig.create(
          LoggingConfig.newBuilder()
              .addElement(LoggableElement.newBuilder().setName("Foo").setId(1))
              .addElement(LoggableElement.newBuilder().setName("Foo").setId(2))
              .build());
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected)
          .hasMessageThat()
          .isEqualTo("Found 2 LoggableElements with the same name Foo, their ids are 1 and 2");
    }
  }

  @Test
  public void testLoggingValidation_duplicateIds() {
    try {
      ValidatedLoggingConfig.create(
          LoggingConfig.newBuilder()
              .addElement(LoggableElement.newBuilder().setName("Foo").setId(1))
              .addElement(LoggableElement.newBuilder().setName("Bar").setId(1))
              .build());
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected)
          .hasMessageThat()
          .isEqualTo("Found 2 LoggableElements with the same id 1: Foo and Bar");
    }
  }
}
