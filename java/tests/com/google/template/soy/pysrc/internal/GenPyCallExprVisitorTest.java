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

package com.google.template.soy.pysrc.internal;

import static com.google.template.soy.pysrc.internal.SoyCodeForPySubject.assertThatSoyFile;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for GenCallCodeVisitor.
 *
 */
@RunWith(JUnit4.class)
public final class GenPyCallExprVisitorTest {

  private static final String SOY_BASE =
      "{namespace boo.foo autoescape=\"strict\"}\n"
          + "{template .goo}\n"
          + "  Hello\n"
          + "{/template}\n"
          + "{template .moo}\n"
          + "  %s\n"
          + "{/template}\n";

  private static final String SANITIZED_APPROVAL =
      "approval=sanitize.IActuallyUnderstandSoyTypeSafetyAndHaveSecurityApproval("
          + "'Internally created Sanitization.')";

  @Test
  public void testBasicCall() {
    String soyCode = "{call .goo data=\"all\" /}";
    String expectedPyCode = "goo(data, ijData)";

    assertThatSoyFile(String.format(SOY_BASE, soyCode)).compilesToSourceContaining(expectedPyCode);

    soyCode = "{@param bar : ?}\n" + "{call .goo data=\"$bar\" /}";
    expectedPyCode = "goo(data.get('bar'), ijData)";

    assertThatSoyFile(String.format(SOY_BASE, soyCode)).compilesToSourceContaining(expectedPyCode);
  }

  @Test
  public void testBasicCall_external() {
    String soyCode = "{call external.library.boo data=\"all\" /}";
    String expectedPyCode = "library.boo(data, ijData)";

    assertThatSoyFile(String.format(SOY_BASE, soyCode)).compilesToSourceContaining(expectedPyCode);

    soyCode = "{@param bar : ?}\n" + "{call external.library.boo data=\"$bar\" /}";
    expectedPyCode = "library.boo(data.get('bar'), ijData)";

    assertThatSoyFile(String.format(SOY_BASE, soyCode)).compilesToSourceContaining(expectedPyCode);
  }

  @Test
  public void testBasicCall_params() {
    String soyCode =
        "{@param moo : ?}\n" + "{call .goo}\n" + "  {param goo: $moo /}\n" + "{/call}\n";
    String expectedPyCode = "goo({'goo': data.get('moo')}, ijData)";

    assertThatSoyFile(String.format(SOY_BASE, soyCode)).compilesToSourceContaining(expectedPyCode);

    soyCode = "{call .goo}\n" + "  {param goo kind=\"text\"}Hello{/param}\n" + "{/call}\n";
    expectedPyCode =
        "goo({'goo': sanitize.UnsanitizedText('Hello', " + SANITIZED_APPROVAL + ")}, ijData)";

    assertThatSoyFile(String.format(SOY_BASE, soyCode)).compilesToSourceContaining(expectedPyCode);

    soyCode =
        "{@param moo : ?}\n"
            + "{call .goo}\n"
            + "  {param goo: $moo /}\n"
            + "  {param moo kind=\"text\"}Hello{/param}\n"
            + "{/call}\n";
    expectedPyCode =
        "goo({'goo': data.get('moo'), 'moo': sanitize.UnsanitizedText('Hello', "
            + SANITIZED_APPROVAL
            + ")}, ijData)";

    assertThatSoyFile(String.format(SOY_BASE, soyCode)).compilesToSourceContaining(expectedPyCode);

    soyCode =
        "{@param moo : ?}\n"
            + "{@param bar : ?}\n"
            + "{call .goo data=\"$bar\"}"
            + "  {param goo: $moo /}\n"
            + "{/call}\n";
    expectedPyCode =
        "goo(runtime.merge_into_dict(dict(data.get('bar')), {'goo': data.get('moo')}), "
            + "ijData)";

    assertThatSoyFile(String.format(SOY_BASE, soyCode)).compilesToSourceContaining(expectedPyCode);
  }

  @Test
  public void testBasicCall_blockParams() {
    String soyCode =
        "{call .goo}\n"
            + "  {param moo kind=\"text\"}\n"
            + "    {for $i in range(3)}{$i}{/for}\n"
            + "  {/param}\n"
            + "{/call}\n";
    String expectedPyCode =
        "goo({'moo': sanitize.UnsanitizedText(''.join(param###), "
            + SANITIZED_APPROVAL
            + ")}, ijData)";

    assertThatSoyFile(String.format(SOY_BASE, soyCode)).compilesToSourceContaining(expectedPyCode);
  }

  @Test
  public void testDelegateCall() {
    String soyCode = "{@param bar : ?}\n" + "{delcall moo.goo data=\"$bar\" /}";
    String expectedPyCode =
        "runtime.get_delegate_fn('moo.goo', '', False)(data.get('bar'), ijData)";

    assertThatSoyFile(String.format(SOY_BASE, soyCode)).compilesToSourceContaining(expectedPyCode);

    soyCode = "{@param bar : ?}\n" + "{delcall moo.goo data=\"$bar\" variant=\"'beta'\" /}";
    expectedPyCode = "runtime.get_delegate_fn('moo.goo', 'beta', False)(data.get('bar'), ijData)";

    assertThatSoyFile(String.format(SOY_BASE, soyCode)).compilesToSourceContaining(expectedPyCode);

    soyCode =
        "{@param bar : ?}\n"
            + "{delcall moo.goo data=\"$bar\" variant=\"'beta'\" allowemptydefault=\"true\" /}";
    expectedPyCode = "runtime.get_delegate_fn('moo.goo', 'beta', True)(data.get('bar'), ijData)";

    assertThatSoyFile(String.format(SOY_BASE, soyCode)).compilesToSourceContaining(expectedPyCode);
  }
}
