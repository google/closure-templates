/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.jbcsrc.restricted;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link com.google.template.soy.jbcsrc.restricted.FieldRef} */
@RunWith(JUnit4.class)
public class FieldRefTest {
  enum SimpleEnum {
    FOO,
    BAR;
  }

  enum ComplexEnum {
    FOO {
      @Override
      void foo() {}
    },
    BAR {
      @Override
      void foo() {}
    };

    abstract void foo();
  }

  @Test
  public void testEnumReference() {
    FieldRef ref = FieldRef.enumReference(SimpleEnum.FOO);
    assertThat(ref.owner().className()).isEqualTo(SimpleEnum.class.getName());

    ref = FieldRef.enumReference(ComplexEnum.FOO);
    assertThat(ref.owner().className()).isEqualTo(ComplexEnum.class.getName());
  }
}
