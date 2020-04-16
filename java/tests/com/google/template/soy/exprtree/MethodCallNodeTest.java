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

package com.google.template.soy.exprtree;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.base.internal.QuoteStyle;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MethodCallNodeTest {

  private static final SourceLocation LOCATION = SourceLocation.UNKNOWN;

  @Test
  public void testGetMethodName() {
    VarRefNode baseExpr = new VarRefNode("myVar", LOCATION, null);
    MethodCallNode method =
        new MethodCallNode(
            baseExpr,
            ImmutableList.of(),
            Identifier.create("myMethod", LOCATION),
            LOCATION,
            /* isNullSafe= */ false);

    assertThat(method.getMethodName().identifier()).isEqualTo("myMethod");
  }

  @Test
  public void testToSourceString() {
    VarRefNode baseExpr = new VarRefNode("myVar", LOCATION, null);
    MethodCallNode method =
        new MethodCallNode(
            baseExpr,
            ImmutableList.of(
                new IntegerNode(2, LOCATION), new StringNode("str", QuoteStyle.SINGLE, LOCATION)),
            Identifier.create("myMethod", LOCATION),
            LOCATION,
            /* isNullSafe= */ true);

    assertThat(method.toSourceString()).isEqualTo("$myVar?.myMethod(2, 'str')");
  }
}
