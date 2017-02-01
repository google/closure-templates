/*
 * Copyright 2008 Google Inc.
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

import com.google.template.soy.base.SourceLocation;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for StringNode.
 *
 */
@RunWith(JUnit4.class)
public final class StringNodeTest {

  @Test
  public void testToSourceString() {
    StringNode sn = new StringNode("Aa`! \n \r \t \\ \' \"", SourceLocation.UNKNOWN);
    assertEquals("'Aa`! \\n \\r \\t \\\\ \\\' \"'", sn.toSourceString());

    sn = new StringNode("\u2222 \uEEEE \u9EC4 \u607A", SourceLocation.UNKNOWN);
    assertEquals("'\u2222 \uEEEE \u9EC4 \u607A'", sn.toSourceString());
    assertEquals("'\\u2222 \\uEEEE \\u9EC4 \\u607A'", sn.toSourceString(true));
  }
}
