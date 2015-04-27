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

import static com.google.template.soy.pysrc.internal.SoyCodeForPySubject.assertThatSoyCode;
import static com.google.template.soy.pysrc.internal.SoyCodeForPySubject.assertThatSoyFile;

import com.google.template.soy.base.SoySyntaxException;

import junit.framework.TestCase;

/**
 * Unit tests for GenPyCodeVisitor.
 *
 * <p>TODO(dcphillips): Add non-inlined 'if' test after adding LetNode support.
 *
 */
public final class GenPyCodeVisitorTest extends TestCase {

  private static final String SOY_NAMESPACE = "{namespace boo.foo autoescape=\"strict\"}\n";

  private static final String EXPECTED_PYFILE_START =
      "# coding=utf-8\n"
      + "\"\"\" This file was automatically generated from no-path.\n"
      + "Please don't edit this file by hand.\n"
      + "\n"
      + "Templates in namespace boo.foo.\n"
      + "\"\"\"\n"
      + "\n"
      + "from __future__ import unicode_literals\n"
      + "import math\n"
      + "import random\n"
      + "from example.runtime import bidi\n"
      + "from example.runtime import directives\n"
      + "from example.runtime import runtime\n"
      + "from example.runtime import sanitize\n"
      + "\n"
      + "\n"
      + "SOY_NAMESPACE = 'boo.foo'\n"
      + "try:\n"
      + "  str = unicode\n"
      + "except NameError:\n"
      + "  pass\n"
      + "\n";


  public void testSoyFile() {
    assertThatSoyFile(SOY_NAMESPACE).compilesToSourceContaining(EXPECTED_PYFILE_START);

    // TODO(dcphillips): Add external template dependency import test once templates are supported.
  }

  public void testBidiConfiguration() {
    String exptectedBidiConfig = "from example import bidi as external_bidi\n";

    assertThatSoyFile(SOY_NAMESPACE).withBidi("example.bidi.fn")
        .compilesToSourceContaining(exptectedBidiConfig);
  }

  public void testTranslationConfiguration() {
    String exptectedTranslationConfig = "from example.translate import SimpleTranslator\n"
        + "translator_impl = SimpleTranslator()\n";

    assertThatSoyFile(SOY_NAMESPACE).withTranslationClass("example.translate.SimpleTranslator")
        .compilesToSourceContaining(exptectedTranslationConfig);
  }

  public void testBlankTemplate() {
    String soyFile = SOY_NAMESPACE
        + "{template .helloWorld}\n"
        + "{/template}\n";

    String expectedPyFile = EXPECTED_PYFILE_START + "\n\n"
        + "def helloWorld(opt_data=None, opt_ijData=None):\n"
        + "  output = []\n"
        + "  return sanitize.SanitizedHtml(''.join(output))\n";

    assertThatSoyFile(soyFile).compilesTo(expectedPyFile);
  }

  public void testSimpleTemplate() {
    String soyFile = SOY_NAMESPACE
        + "{template .helloWorld}\n"
        + "  Hello World!\n"
        + "{/template}\n";

    String expectedPyFile = EXPECTED_PYFILE_START + "\n\n"
        + "def helloWorld(opt_data=None, opt_ijData=None):\n"
        + "  output = []\n"
        + "  output.append('Hello World!')\n"
        + "  return sanitize.SanitizedHtml(''.join(output))\n";

    assertThatSoyFile(soyFile).compilesTo(expectedPyFile);
  }

  public void testOutputScope() {
    String soyFile = SOY_NAMESPACE
        + "{template .helloWorld}\n"
        + "  {if $foo}\n"
        + "    {for $i in range(5)}\n"
        + "      {$boo[$i]}\n"
        + "    {/for}\n"
        + "  {else}\n"
        + "    Blah\n"
        + "  {/if}\n"
        + "{/template}\n";

    String expectedPyFile = EXPECTED_PYFILE_START + "\n\n"
        + "def helloWorld(opt_data=None, opt_ijData=None):\n"
        + "  opt_data = opt_data or {}\n"
        + "  output = []\n"
        + "  if opt_data.get('foo'):\n"
        + "    for i### in xrange(5):\n"
        + "      output.append(str(runtime.key_safe_data_access(opt_data.get('boo'), i###)))\n"
        + "  else:\n"
        + "    output.append('Blah')\n"
        + "  return sanitize.SanitizedHtml(''.join(output))\n";

    assertThatSoyFile(soyFile).compilesTo(expectedPyFile);
  }

  public void testSwitch() {
    String soyCode =
        "{switch $boo}\n"
        + "  {case 0}\n"
        + "     Hello\n"
        + "  {case 1}\n"
        + "     World\n"
        + "  {default}\n"
        + "     !\n"
        + "{/switch}\n";
    String expectedPyCode =
        "switchValue = opt_data.get('boo')\n"
        + "if runtime.type_safe_eq(switchValue, 0):\n"
        + "  output.append('Hello')\n"
        + "elif runtime.type_safe_eq(switchValue, 1):\n"
        + "  output.append('World')\n"
        + "else:\n"
        + "  output.append('!')\n";
    assertThatSoyCode(soyCode).compilesTo(expectedPyCode);
  }

  public void testSwitch_defaultOnly() {
    String soyCode =
        "{switch $boo}\n"
        + "  {default}\n"
        + "     Hello World!\n"
        + "{/switch}\n";
    String expectedPyCode =
        "switchValue = opt_data.get('boo')\n"
        + "output.append('Hello World!')\n";
    assertThatSoyCode(soyCode).compilesTo(expectedPyCode);
  }

  public void testFor() {
    String soyCode =
        "{for $i in range(5)}\n"
        + "  {$boo[$i]}\n"
        + "{/for}\n";
    String expectedPyCode =
        "for i### in xrange(5):\n"
        + "  output.append(str(runtime.key_safe_data_access(opt_data.get('boo'), i###)))\n";
    assertThatSoyCode(soyCode).compilesTo(expectedPyCode);

    soyCode =
        "{for $i in range(5, 10)}\n"
        + "  {$boo[$i]}\n"
        + "{/for}\n";
    expectedPyCode =
        "for i### in xrange(5, 10):\n"
        + "  output.append(str(runtime.key_safe_data_access(opt_data.get('boo'), i###)))\n";
    assertThatSoyCode(soyCode).compilesTo(expectedPyCode);

    soyCode =
        "{for $i in range($foo, $boo, $goo)}\n"
        + "  {$boo[$i]}\n"
        + "{/for}\n";
    expectedPyCode =
        "for i### in xrange(opt_data.get('foo'), opt_data.get('boo'), opt_data.get('goo')):\n"
        + "  output.append(str(runtime.key_safe_data_access(opt_data.get('boo'), i###)))\n";
    assertThatSoyCode(soyCode).compilesTo(expectedPyCode);
  }

  public void testForeach() {
    String soyCode =
        "{foreach $operand in $operands}\n"
        + "  {$operand}\n"
        + "{/foreach}\n";

    // There's no simple way to account for all instances of the id in these variables, so for now
    // we just hardcode '3'.
    String expectedPyCode =
        "operandList### = opt_data.get('operands')\n"
        + "for operandIndex###, operandData### in enumerate(operandList###):\n"
        + "  output.append(str(operandData###))\n";

    assertThatSoyCode(soyCode).compilesTo(expectedPyCode);

    soyCode =
        "{foreach $operand in $operands}\n"
        + "  {isFirst($operand)}\n"
        + "  {isLast($operand)}\n"
        + "  {index($operand)}\n"
        + "{/foreach}\n";

    expectedPyCode =
        "operandList### = opt_data.get('operands')\n"
        + "for operandIndex###, operandData### in enumerate(operandList###):\n"
        + "  output.extend([str(operandIndex### == 0),"
                         + "str(operandIndex### == len(operandList###) - 1),"
                         + "str(operandIndex###)])\n";

    assertThatSoyCode(soyCode).compilesTo(expectedPyCode);
  }

  public void testForeach_ifempty() {
    String soyCode =
        "{foreach $operand in $operands}\n"
        + "  {$operand}\n"
        + "{ifempty}\n"
        + "  {$foo}"
        + "{/foreach}\n";

    String expectedPyCode =
        "operandList### = opt_data.get('operands')\n"
        + "if operandList###:\n"
        + "  for operandIndex###, operandData### in enumerate(operandList###):\n"
        + "    output.append(str(operandData###))\n"
        + "else:\n"
        + "  output.append(str(opt_data.get('foo')))\n";

    assertThatSoyCode(soyCode).compilesTo(expectedPyCode);
  }

  public void testLetValue() {
    assertThatSoyCode("{let $foo: $boo /}\n").compilesTo("foo__soy### = opt_data.get('boo')\n");
  }

  public void testLetContent() {
    String soyCode =
        "{let $foo kind=\"html\"}\n"
        + "  Hello {$boo}\n"
        + "{/let}\n";

    String expectedPyCode =
        "foo__soy### = ['Hello ',str(opt_data.get('boo'))]\n"
        + "foo__soy### = sanitize.SanitizedHtml(''.join(foo__soy###))\n";

    assertThatSoyCode(soyCode).compilesTo(expectedPyCode);
  }

  public void testLetContent_notComputableAsExpr() {
    String soyCode =
        "{let $foo kind=\"html\"}\n"
        + "  {for $num in range(5)}\n"
        + "    {$num}\n"
        + "  {/for}\n"
        + "  Hello {$boo}\n"
        + "{/let}\n";

    String expectedPyCode =
        "foo__soy### = []\n"
        + "for num### in xrange(5):\n"
        + "  foo__soy###.append(str(num###))\n"
        + "foo__soy###.extend(['Hello ',str(opt_data.get('boo'))])\n"
        + "foo__soy### = sanitize.SanitizedHtml(''.join(foo__soy###))\n";

    assertThatSoyCode(soyCode).compilesTo(expectedPyCode);
  }

  public void testLetContent_noContentKind() {
    String soyCode =
        "{let $foo}\n"
        + "  Hello {$boo}\n"
        + "{/let}\n";

    assertThatSoyCode(soyCode).compilesWithException(SoySyntaxException.class);
  }

  public void testCallReturnsString() {
    String soyCode = "{call .foo data=\"all\" /}";

    String expectedPyCode = "output.append(str(ns.foo(opt_data, opt_ijData)))\n";

    assertThatSoyCode(soyCode).compilesTo(expectedPyCode);
  }
}
