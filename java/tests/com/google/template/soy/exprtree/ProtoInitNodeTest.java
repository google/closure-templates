/*
 * Copyright 2016 Google Inc.
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

import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link ProtoInitNode}. */
@RunWith(JUnit4.class)
public class ProtoInitNodeTest {

  @Test
  public void testToSourceString() {
    ProtoInitNode fn =
        new ProtoInitNode(
            "my.awesome.Proto", ImmutableList.of("f", "i", "s"), SourceLocation.UNKNOWN);
    fn.addChild(new FloatNode(3.14159, SourceLocation.UNKNOWN));
    fn.addChild(new IntegerNode(2, SourceLocation.UNKNOWN));
    fn.addChild(new StringNode("str", SourceLocation.UNKNOWN));
    assertEquals("my.awesome.Proto(f: 3.14159, i: 2, s: 'str')", fn.toSourceString());
  }
}
