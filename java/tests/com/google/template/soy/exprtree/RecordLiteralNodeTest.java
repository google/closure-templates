/*
 * Copyright 2011 Google Inc.
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

/** Unit tests for RecordLiteralNode. */
@RunWith(JUnit4.class)
public final class RecordLiteralNodeTest {

  private static final SourceLocation X = SourceLocation.UNKNOWN;

  @Test
  public void testToSourceString() {
    VarRefNode fooDataRef = new VarRefNode("foo", X, null);

    RecordLiteralNode recordLit =
        new RecordLiteralNode(
            Identifier.create("record", X),
            ImmutableList.of(
                Identifier.create("aaa", X),
                Identifier.create("bbb", X),
                Identifier.create("boo", X)),
            X);
    recordLit.addChildren(
        ImmutableList.of(
            new StringNode("blah", QuoteStyle.SINGLE, X), new IntegerNode(123, X), fooDataRef));
    assertThat(recordLit.toSourceString()).isEqualTo("record(aaa: 'blah', bbb: 123, boo: $foo)");
  }
}
