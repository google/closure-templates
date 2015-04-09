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

package com.google.template.soy.jbcsrc;

import static com.google.template.soy.data.SoyValueHelper.EMPTY_DICT;
import static com.google.template.soy.jbcsrc.TemplateTester.EMPTY_CONTEXT;

import com.google.template.soy.jbcsrc.api.AdvisingAppendable;
import com.google.template.soy.jbcsrc.api.CompiledTemplate;
import com.google.template.soy.jbcsrc.api.RenderResult;

import junit.framework.TestCase;

import java.io.IOException;

/**
 * Tests for {@link DetachState}.
 */
public final class DetachStateTest extends TestCase {
  static final class TestAppendable implements AdvisingAppendable {
    private final StringBuilder delegate = new StringBuilder();
    boolean softLimitReached;

    @Override public TestAppendable append(CharSequence s) {
      delegate.append(s);
      return this;
    }

    @Override public TestAppendable append(CharSequence s, int start, int end) {
      delegate.append(s, start, end);
      return this;
    }

    @Override public TestAppendable append(char c) {
      delegate.append(c);
      return this;
    }

    @Override public boolean softLimitReached() {
      return softLimitReached;
    }

    @Override public String toString() {
      return delegate.toString();
    }
  }

  public void testDetach_singleRawTextNode() throws IOException {
    CompiledTemplate.Factory factory = TemplateTester.compileTemplateBody("hello world");
    CompiledTemplate template = factory.create(EMPTY_DICT);
    // Basic stuff works
    TestAppendable output = new TestAppendable();
    assertEquals(RenderResult.done(), template.render(output, EMPTY_CONTEXT));
    assertEquals("hello world", output.toString());

    output = new TestAppendable();
    output.softLimitReached = true;
    // detached!!!
    assertEquals(RenderResult.limited(), template.render(output, EMPTY_CONTEXT));
    assertEquals("hello world", output.toString());
    assertEquals(RenderResult.done(), template.render(output, EMPTY_CONTEXT));
    assertEquals("hello world", output.toString());  // nothing was added
  }

  public void testDetach_multipleNodes() throws IOException {
    CompiledTemplate.Factory factory = TemplateTester.compileTemplateBody(
        "hello",
        // this print node inserts a space character and ensures that our raw text nodes don't get
        // merged
        "{' '}",
        "world");
    CompiledTemplate template = factory.create(EMPTY_DICT);
    // Basic stuff works
    TestAppendable output = new TestAppendable();
    assertEquals(RenderResult.done(), template.render(output, EMPTY_CONTEXT));
    assertEquals("hello world", output.toString());

    output = new TestAppendable();
    output.softLimitReached = true;
    // detached!!!
    assertEquals(RenderResult.limited(), template.render(output, EMPTY_CONTEXT));
    assertEquals("hello", output.toString());
    assertEquals(RenderResult.limited(), template.render(output, EMPTY_CONTEXT));
    assertEquals("hello ", output.toString());
    assertEquals(RenderResult.limited(), template.render(output, EMPTY_CONTEXT));
    assertEquals("hello world", output.toString());
    assertEquals(RenderResult.done(), template.render(output, EMPTY_CONTEXT));
    assertEquals("hello world", output.toString());  // nothing was added
  }

  // ensure that when we call back in, locals are restored
  public void testDetach_saveRestore() throws IOException {
    CompiledTemplate.Factory factory = TemplateTester.compileTemplateBody(
        "{for $i in range(10)}",
        "  {$i}",
        "{/for}");
    CompiledTemplate template = factory.create(EMPTY_DICT);
    // Basic stuff works
    TestAppendable output = new TestAppendable();
    assertEquals(RenderResult.done(), template.render(output, EMPTY_CONTEXT));
    assertEquals("0123456789", output.toString());

    output = new TestAppendable();
    output.softLimitReached = true;
    for (int i = 0; i < 10; i++) {
      assertEquals(RenderResult.limited(), template.render(output, EMPTY_CONTEXT));
      assertEquals(String.valueOf(i), output.toString());
      output.delegate.setLength(0);
    }
    assertEquals(RenderResult.done(), template.render(output, EMPTY_CONTEXT));
    assertEquals("", output.toString());  // last render was empty
  }
}
