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

package com.google.template.soy.shared.restricted;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.types.primitive.IntType;
import com.google.template.soy.types.primitive.StringType;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class TypedSoyFunctionTest {

  private static final class BadTypedSoyFunction extends TypedSoyFunction {

    @Override
    public List<Signature> signatures() {
      return ImmutableList.of(
          Signature.create(ImmutableList.of(StringType.getInstance()), StringType.getInstance()),
          Signature.create(ImmutableList.of(IntType.getInstance()), IntType.getInstance()));
    }

    @Override
    public String getName() {
      return "badFunc";
    }
  }

  @Test
  public void testTypedSoyFunction() throws Exception {
    try {
      SoyFunction func = new BadTypedSoyFunction();
      func.getValidArgsSizes();
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e)
          .hasMessageThat()
          .contains(
              "TypedSoyFunction can only have exactly one signature for a given number of "
                  + "parameters. Found more than one signatures that specify 1 parameters");
    }
  }
}
