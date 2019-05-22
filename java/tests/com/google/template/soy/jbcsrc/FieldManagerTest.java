/*
 * Copyright 2019 Google Inc.
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
import static org.junit.Assert.fail;

import com.google.template.soy.jbcsrc.restricted.FieldRef;
import com.google.template.soy.jbcsrc.restricted.TypeInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.objectweb.asm.Type;

@RunWith(JUnit4.class)
public final class FieldManagerTest {
  @Test
  public void testCantAddDuplicateNames() {
    FieldManager fields = new FieldManager(TypeInfo.create(getClass()));
    fields.addField("foo", Type.INT_TYPE);

    try {
      fields.addField("foo", Type.INT_TYPE);
      fail();
    } catch (IllegalArgumentException t) {
      assertThat(t).hasMessageThat().isEqualTo("Name: foo was already claimed!");
    }
  }

  @Test
  public void testCanMangleName() {
    FieldManager fields = new FieldManager(TypeInfo.create(getClass()));
    FieldRef f1 = fields.addGeneratedFinalField("foo", Type.INT_TYPE);
    assertThat(f1.name()).isEqualTo("foo");
    FieldRef f2 = fields.addGeneratedFinalField("foo", Type.INT_TYPE);
    assertThat(f2.name()).isEqualTo("foo%1");
  }

  @Test
  public void testCantDefineFieldsMultipleTimes() {
    FieldManager fields = new FieldManager(TypeInfo.create(getClass()));
    fields.defineFields(null);
    try {
      fields.defineFields(null);
      fail();
    } catch (IllegalStateException expected) {
    }
    try {
      fields.addField("foo", Type.INT_TYPE);
      fail();
    } catch (IllegalStateException expected) {
    }
  }
}
