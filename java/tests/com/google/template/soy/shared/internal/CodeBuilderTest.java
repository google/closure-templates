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

import static com.google.common.truth.Truth.assertThat;

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
    assertThat(cb.getCode()).isEqualTo("boo");
    cb.appendLineEnd("foo", "goo");
    assertThat(cb.getCode()).isEqualTo("boofoogoo\n");
    cb.appendLine("moo", "too");
    assertThat(cb.getCode()).isEqualTo("boofoogoo\nmootoo\n");
  }

  public void testIndent() {
    SimpleCodeBuilder cb = new SimpleCodeBuilder();
    cb.increaseIndent();
    cb.appendLine("boo");
    assertThat(cb.getCode()).isEqualTo("  boo\n");
    cb.increaseIndentTwice();
    cb.appendLine("foo");
    assertThat(cb.getCode()).isEqualTo("  boo\n      foo\n");
    cb.decreaseIndent();
    cb.appendLineEnd("goo");  // not affected by indent
    cb.appendLine("moo");
    assertThat(cb.getCode()).isEqualTo("  boo\n      foo\ngoo\n    moo\n");
    cb.decreaseIndentTwice();
    cb.appendLine("too");
    assertThat(cb.getCode()).isEqualTo("  boo\n      foo\ngoo\n    moo\ntoo\n");
  }

  public void testNegativeIndent() {
    SimpleCodeBuilder cb = new SimpleCodeBuilder();

    try {
      cb.decreaseIndent();
      fail();
    } catch (IllegalStateException e) {
      // Test passes.
    }
  }
}
