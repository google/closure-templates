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

package com.google.template.soy.soytree;

import com.google.template.soy.base.SourceLocation;

import junit.framework.*;


/**
 * Unit tests for RawTextNode.
 *
 */
public final class RawTextNodeTest extends TestCase {

  public void testToSourceString() {
    assertEquals("Aa`! {\\n} {\\r} {\\t} {lb} {rb}", rawTextToSourceString("Aa`! \n \r \t { }"));
    assertEquals("{literal}/**{/literal} some comment {literal}*/{/literal}",  rawTextToSourceString("/** some comment */"));
    assertEquals("{literal}/**{/literal}* some comment {literal}*/{/literal}",  rawTextToSourceString("/*** some comment */"));
  }

  private String rawTextToSourceString(final String rawText) {
    return new RawTextNode(0, rawText, SourceLocation.UNKNOWN).toSourceString();
  }
}
