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

package com.google.template.soy.shared.internal;

import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.internal.targetexpr.TargetExpr;

import junit.framework.TestCase;

import java.util.List;


/**
 * Unit tests for CodeBuilder.
 *
 */
public final class CodeBuilderTest extends TestCase {

  private class SimpleCodeBuilder extends CodeBuilder<TargetExpr> {

    @Override
    public void initOutputVarIfNecessary() { /* NOOP */ }
    @Override
    public void addToOutputVar(List<? extends TargetExpr> targetExprs) { /* NOOP */ }
  }

  public void testAppend() {
    SimpleCodeBuilder cb = new SimpleCodeBuilder();
    cb.append("boo");
    assertEquals("boo", cb.getCode());
    cb.appendLineEnd("foo", "goo");
    assertEquals("boofoogoo\n", cb.getCode());
    cb.appendLine("moo", "too");
    assertEquals("boofoogoo\nmootoo\n", cb.getCode());
  }

  public void testIndent() {
    SimpleCodeBuilder cb = new SimpleCodeBuilder();
    cb.increaseIndent();
    cb.appendLine("boo");
    assertEquals("  boo\n", cb.getCode());
    cb.increaseIndentTwice();
    cb.appendLine("foo");
    assertEquals("  boo\n      foo\n", cb.getCode());
    cb.decreaseIndent();
    cb.appendLineEnd("goo");  // not affected by indent
    cb.appendLine("moo");
    assertEquals("  boo\n      foo\ngoo\n    moo\n", cb.getCode());
    cb.decreaseIndentTwice();
    cb.appendLine("too");
    assertEquals("  boo\n      foo\ngoo\n    moo\ntoo\n", cb.getCode());

    try {
      cb.decreaseIndent();
      fail();
    } catch (SoySyntaxException sse) {
      // Test passes.
    }

    try {
      for (int i = 0; i < 12; ++i) {
        cb.increaseIndent();
      }
      fail();
    } catch (SoySyntaxException sse) {
      // Test passes.
    }
  }
}
