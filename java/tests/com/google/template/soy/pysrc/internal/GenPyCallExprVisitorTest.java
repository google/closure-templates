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

import junit.framework.TestCase;

/**
 * Unit tests for GenCallCodeVisitor.
 *
 */
public final class GenPyCallExprVisitorTest extends TestCase {

  private static final String SOY_BASE = "{namespace boo.foo autoescape=\"strict\"}\n"
      + "{template .goo}\n"
      + "  Hello\n"
      + "{/template}\n"
      + "{template .moo}\n"
      + "  %s\n"
      + "{/template}\n";


  public void testBasicCall() {
    String soyCode = "{call .goo data=\"all\" /}";
    String expectedPyCode = "goo(opt_data, opt_ijData)";

    assertThatSoyFile(String.format(SOY_BASE, soyCode)).compilesToSourceContaining(expectedPyCode);


    soyCode = "{call .goo data=\"$bar\" /}";
    expectedPyCode = "goo(opt_data.get('bar'), opt_ijData)";

    assertThatSoyFile(String.format(SOY_BASE, soyCode)).compilesToSourceContaining(expectedPyCode);
  }

  public void testBasicCall_external() {
    String soyCode = "{call external.library.boo data=\"all\" /}";
    String expectedPyCode = "library.boo(opt_data, opt_ijData)";

    assertThatSoyFile(String.format(SOY_BASE, soyCode)).compilesToSourceContaining(expectedPyCode);


    soyCode = "{call external.library.boo data=\"$bar\" /}";
    expectedPyCode = "library.boo(opt_data.get('bar'), opt_ijData)";

    assertThatSoyFile(String.format(SOY_BASE, soyCode)).compilesToSourceContaining(expectedPyCode);
  }

  public void testBasicCall_params() {
    String soyCode = "{call .goo}\n"
        + "  {param goo: $moo /}\n"
        + "{/call}\n";
    String expectedPyCode = "goo({'goo': opt_data.get('moo')}, opt_ijData)";

    assertThatSoyFile(String.format(SOY_BASE, soyCode)).compilesToSourceContaining(expectedPyCode);


    soyCode = "{call .goo}\n"
        + "  {param goo kind=\"text\"}Hello{/param}\n"
        + "{/call}\n";
    expectedPyCode = "goo({'goo': sanitize.UnsanitizedText('Hello')}, opt_ijData)";

    assertThatSoyFile(String.format(SOY_BASE, soyCode)).compilesToSourceContaining(expectedPyCode);


    soyCode = "{call .goo}\n"
        + "  {param goo: $moo /}\n"
        + "  {param moo kind=\"text\"}Hello{/param}\n"
        + "{/call}\n";
    expectedPyCode =
        "goo({'goo': opt_data.get('moo'), 'moo': sanitize.UnsanitizedText('Hello')}, opt_ijData)";

    assertThatSoyFile(String.format(SOY_BASE, soyCode)).compilesToSourceContaining(expectedPyCode);


    soyCode = "{call .goo data=\"$bar\"}"
        + "  {param goo: $moo /}\n"
        + "{/call}\n";
    expectedPyCode =
        "goo(runtime.merge_into_dict({'goo': opt_data.get('moo')}, opt_data.get('bar')), "
                                  + "opt_ijData)";

    assertThatSoyFile(String.format(SOY_BASE, soyCode)).compilesToSourceContaining(expectedPyCode);
  }

  public void testBasicCall_blockParams() {
    String soyCode = "{call .goo}\n"
        + "  {param moo kind=\"text\"}\n"
        + "    {for $i in range(3)}{$i}{/for}\n"
        + "  {/param}\n"
        + "{/call}\n";
    String expectedPyCode = "goo({'moo': sanitize.UnsanitizedText(''.join(param###))}, opt_ijData)";

    assertThatSoyFile(String.format(SOY_BASE, soyCode)).compilesToSourceContaining(expectedPyCode);
  }

  public void testDelegateCall() {
    String soyCode = "{delcall moo.goo data=\"$bar\" /}";
    String expectedPyCode =
        "runtime.get_delegate_fn('moo.goo', '', True)(opt_data.get('bar'), opt_ijData)";

    assertThatSoyFile(String.format(SOY_BASE, soyCode)).compilesToSourceContaining(expectedPyCode);


    soyCode = "{delcall moo.goo data=\"$bar\" variant=\"'beta'\" /}";
    expectedPyCode =
        "runtime.get_delegate_fn('moo.goo', 'beta', True)(opt_data.get('bar'), opt_ijData)";

    assertThatSoyFile(String.format(SOY_BASE, soyCode)).compilesToSourceContaining(expectedPyCode);


    soyCode = "{delcall moo.goo data=\"$bar\" variant=\"'beta'\" allowemptydefault=\"false\" /}";
    expectedPyCode =
        "runtime.get_delegate_fn('moo.goo', 'beta', False)(opt_data.get('bar'), opt_ijData)";

    assertThatSoyFile(String.format(SOY_BASE, soyCode)).compilesToSourceContaining(expectedPyCode);
  }
}
