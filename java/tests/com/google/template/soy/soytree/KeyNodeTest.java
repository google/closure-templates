/*
 * Copyright 2018 Google Inc.
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

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.QuoteStyle;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.VarRefNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class KeyNodeTest {

  private static final SourceLocation X = SourceLocation.UNKNOWN;

  @Test
  public void testToSourceString() {
    VarRefNode foo = new VarRefNode("$foo", X, null);
    KeyNode keyNode0 = new KeyNode(0, X, foo);
    assertThat(keyNode0.toSourceString()).isEqualTo("{key $foo}");

    StringNode bar = new StringNode("bar", QuoteStyle.SINGLE, X);
    KeyNode keyNode1 = new KeyNode(1, X, bar);
    assertThat(keyNode1.toSourceString()).isEqualTo("{key 'bar'}");
  }
}
