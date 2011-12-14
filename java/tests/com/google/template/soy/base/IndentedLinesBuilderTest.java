/*
 * Copyright 2009 Google Inc.
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

package com.google.template.soy.base;

import junit.framework.TestCase;


/**
 * Unit tests for IndentedLinesBuilder.
 *
 */
public class IndentedLinesBuilderTest extends TestCase {


  public void testIndentedLinesBuilder() {

    IndentedLinesBuilder ilb = new IndentedLinesBuilder(2);
    ilb.appendLine("Line 1");
    ilb.increaseIndent();
    ilb.appendLine("Line", ' ', 2);
    ilb.setIndentLen(6);
    ilb.appendIndent().append("Line ").append('3').appendLineEnd();
    ilb.decreaseIndent();
    ilb.sb().append("Line 4 not indented\n");
    ilb.appendLine("Line 5");

    assertEquals(2, ilb.getIndentIncrementLen());
    assertEquals(4, ilb.getCurrIndentLen());
    assertEquals(
        "Line 1\n" +
        "  Line 2\n" +
        "      Line 3\n" +
        "Line 4 not indented\n" +
        "    Line 5\n",
        ilb.toString());
  }

}
