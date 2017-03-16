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

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.SoySyntaxException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for GenPyCodeVisitor.
 *
 * <p>TODO(dcphillips): Add non-inlined 'if' test after adding LetNode support.
 *
 */
@RunWith(JUnit4.class)
public final class GenPyCodeVisitorTest {

  private static final String SOY_NAMESPACE = "{namespace boo.foo autoescape=\"strict\"}\n";
  private static final String DUMMY_SOY_FILE = SOY_NAMESPACE + "{template .dummy}{/template}\n";

  private static final String EXPECTED_PYFILE_START =
      "# coding=utf-8\n"
          + "\"\"\" This file was automatically generated from no-path.\n"
          + "Please don't edit this file by hand.\n"
          + "\n"
          + "SOY_NAMESPACE: 'boo.foo'.\n"
          + "\n"
          + "Templates in namespace boo.foo.\n"
          + "\"\"\"\n"
          + "\n"
          + "from __future__ import unicode_literals\n"
          + "import collections\n"
          + "import math\n"
          + "import random\n"
          + "import sys\n"
          + "from example.runtime import bidi\n"
          + "from example.runtime import directives\n"
          + "from example.runtime import runtime\n"
          + "from example.runtime import sanitize\n"
          + "\n"
          + "NAMESPACE_MANIFEST = {\n"
          + "}\n"
          + "\n"
          + "try:\n"
          + "  str = unicode\n"
          + "except NameError:\n"
          + "  pass\n"
          + "\n";

  private static final String SANITIZATION_APPROVAL =
      "approval=sanitize.IActuallyUnderstandSoyTypeSafetyAndHaveSecurityApproval("
          + "'Internally created Sanitization.')";

  @Test
  public void testSoyFile() {
    assertThatSoyFile(DUMMY_SOY_FILE).compilesToSourceContaining(EXPECTED_PYFILE_START);

    // TODO(dcphillips): Add external template dependency import test once templates are supported.
  }

  @Test
  public void testNamespacedImport() {
    String soyFile =
        SOY_NAMESPACE
            + "{template .helloWorld}\n"
            + "  {call foo.bar.baz.quz /}\n"
            + "{/template}\n";
    String expectedImport = "namespaced_import('baz', namespace='foo.bar')";

    assertThatSoyFile(soyFile).compilesToSourceContaining(expectedImport);
  }

  @Test
  public void testAbsoluteImport() {
    String soyFile =
        SOY_NAMESPACE
            + "{template .helloWorld}\n"
            + "  {call foo.bar.baz.quz /}\n"
            + "{/template}\n";
    String expectedImport = "import google.foo.bar.baz as baz";

    ImmutableMap.Builder<String, String> namespaceManifest = new ImmutableMap.Builder<>();
    namespaceManifest.put("foo.bar.baz", "google.foo.bar.baz");

    assertThatSoyFile(soyFile)
        .withNamespaceManifest(namespaceManifest.build())
        .compilesToSourceContaining(expectedImport);
  }

  @Test
  public void testNamespaceManifest() {
    String soyFile =
        SOY_NAMESPACE
            + "{template .helloWorld}\n"
            + "  {call foo.bar.baz.quz /}\n"
            + "{/template}\n";
    String expectedManifest =
        "NAMESPACE_MANIFEST = {\n" + "    'foo.bar.baz': 'google.foo.bar.baz',\n" + "}\n";

    ImmutableMap.Builder<String, String> namespaceManifest = new ImmutableMap.Builder<>();
    namespaceManifest.put("foo.bar.baz", "google.foo.bar.baz");

    assertThatSoyFile(soyFile)
        .withNamespaceManifest(namespaceManifest.build())
        .compilesToSourceContaining(expectedManifest);
  }

  @Test
  public void testEnvironmentConfiguration() {
    String soyFile =
        SOY_NAMESPACE
            + "{template .helloWorld}\n"
            + "  {call foo.bar.baz.quz /}\n"
            + "{/template}\n";
    String expectedEnviromentConfirmation =
        "namespaced_import('baz', namespace='foo.bar', "
            + "environment_path='runtime.custom_environment')";

    assertThatSoyFile(soyFile)
        .withEnvironmentModule("runtime.custom_environment")
        .compilesToSourceContaining(expectedEnviromentConfirmation);
  }

  @Test
  public void testBidiConfiguration() {
    String exptectedBidiConfig = "from example import bidi as external_bidi\n";

    assertThatSoyFile(DUMMY_SOY_FILE)
        .withBidi("example.bidi.fn")
        .compilesToSourceContaining(exptectedBidiConfig);
  }

  @Test
  public void testTranslationConfiguration() {
    String exptectedTranslationConfig =
        "from example.translate import SimpleTranslator\n"
            + "translator_impl = SimpleTranslator()\n";

    assertThatSoyFile(DUMMY_SOY_FILE)
        .withTranslationClass("example.translate.SimpleTranslator")
        .compilesToSourceContaining(exptectedTranslationConfig);
  }

  @Test
  public void testBlankTemplate() {
    String soyFile = SOY_NAMESPACE + "{template .helloWorld}\n" + "{/template}\n";

    String expectedPyFile =
        EXPECTED_PYFILE_START
            + "\n\n"
            + "def helloWorld(data={}, ijData={}):\n"
            + "  output = []\n"
            + "  return sanitize.SanitizedHtml(''.join(output), "
            + SANITIZATION_APPROVAL
            + ")\n";

    assertThatSoyFile(soyFile).compilesTo(expectedPyFile);
  }

  @Test
  public void testSimpleTemplate() {
    String soyFile =
        SOY_NAMESPACE + "{template .helloWorld}\n" + "  Hello World!\n" + "{/template}\n";

    String expectedPyFile =
        EXPECTED_PYFILE_START
            + "\n\n"
            + "def helloWorld(data={}, ijData={}):\n"
            + "  output = []\n"
            + "  output.append('Hello World!')\n"
            + "  return sanitize.SanitizedHtml(''.join(output), "
            + SANITIZATION_APPROVAL
            + ")\n";

    assertThatSoyFile(soyFile).compilesTo(expectedPyFile);
  }

  @Test
  public void testOutputScope() {
    String soyFile =
        SOY_NAMESPACE
            + "{template .helloWorld}\n"
            + "  {@param foo : ?}\n"
            + "  {@param boo : ?}\n"
            + "  {if $foo}\n"
            + "    {for $i in range(5)}\n"
            + "      {$boo[$i]}\n"
            + "    {/for}\n"
            + "  {else}\n"
            + "    Blah\n"
            + "  {/if}\n"
            + "{/template}\n";

    String expectedPyFile =
        EXPECTED_PYFILE_START
            + "\n\n"
            + "def helloWorld(data={}, ijData={}):\n"
            + "  output = []\n"
            + "  if data.get('foo'):\n"
            + "    for i### in xrange(0, 5, 1):\n"
            + "      output.append(str(runtime.key_safe_data_access(data.get('boo'), i###)))\n"
            + "  else:\n"
            + "    output.append('Blah')\n"
            + "  return sanitize.SanitizedHtml(''.join(output), "
            + SANITIZATION_APPROVAL
            + ")\n";

    assertThatSoyFile(soyFile).compilesTo(expectedPyFile);
  }

  @Test
  public void testSwitch() {
    String soyCode =
        "{@param boo : ?}\n"
            + "{switch $boo}\n"
            + "  {case 0}\n"
            + "     Hello\n"
            + "  {case 1}\n"
            + "     World\n"
            + "  {default}\n"
            + "     !\n"
            + "{/switch}\n";
    String expectedPyCode =
        "switchValue = data.get('boo')\n"
            + "if runtime.type_safe_eq(switchValue, 0):\n"
            + "  output.append('Hello')\n"
            + "elif runtime.type_safe_eq(switchValue, 1):\n"
            + "  output.append('World')\n"
            + "else:\n"
            + "  output.append('!')\n";
    assertThatSoyCode(soyCode).compilesTo(expectedPyCode);
  }

  @Test
  public void testSwitch_defaultOnly() {
    String soyCode =
        "{@param boo : ?}\n"
            + "{switch $boo}\n"
            + "  {default}\n"
            + "     Hello World!\n"
            + "{/switch}\n";
    String expectedPyCode = "switchValue = data.get('boo')\n" + "output.append('Hello World!')\n";
    assertThatSoyCode(soyCode).compilesTo(expectedPyCode);
  }

  @Test
  public void testFor() {
    String soyCode =
        "{@param boo : ?}\n" + "{for $i in range(5)}\n" + "  {$boo[$i]}\n" + "{/for}\n";
    String expectedPyCode =
        "for i### in xrange(0, 5, 1):\n"
            + "  output.append(str(runtime.key_safe_data_access(data.get('boo'), i###)))\n";
    assertThatSoyCode(soyCode).compilesTo(expectedPyCode);

    soyCode = "{@param boo : ?}\n" + "{for $i in range(5, 10)}\n" + "  {$boo[$i]}\n" + "{/for}\n";
    expectedPyCode =
        "for i### in xrange(5, 10, 1):\n"
            + "  output.append(str(runtime.key_safe_data_access(data.get('boo'), i###)))\n";
    assertThatSoyCode(soyCode).compilesTo(expectedPyCode);

    soyCode =
        "  {@param boo : ?}\n"
            + "  {@param goo : ?}\n"
            + "  {@param foo : ?}\n"
            + "{for $i in range($foo, $boo, $goo)}\n"
            + "  {$boo[$i]}\n"
            + "{/for}\n";
    expectedPyCode =
        "for i### in xrange(data.get('foo'), data.get('boo'), data.get('goo')):\n"
            + "  output.append(str(runtime.key_safe_data_access(data.get('boo'), i###)))\n";
    assertThatSoyCode(soyCode).compilesTo(expectedPyCode);
  }

  @Test
  public void testForeach() {
    String soyCode =
        "{@param operands : ?}\n"
            + "{foreach $operand in $operands}\n"
            + "  {$operand}\n"
            + "{/foreach}\n";

    // There's no simple way to account for all instances of the id in these variables, so for now
    // we just hardcode '3'.
    String expectedPyCode =
        "operandList### = data.get('operands')\n"
            + "for operandIndex###, operandData### in enumerate(operandList###):\n"
            + "  output.append(str(operandData###))\n";

    assertThatSoyCode(soyCode).compilesTo(expectedPyCode);

    soyCode =
        "{@param operands : ?}\n"
            + "{foreach $operand in $operands}\n"
            + "  {isFirst($operand) ? 1 : 0}\n"
            + "  {isLast($operand) ? 1 : 0}\n"
            + "  {index($operand)}\n"
            + "{/foreach}\n";

    expectedPyCode =
        "operandList### = data.get('operands')\n"
            + "for operandIndex###, operandData### in enumerate(operandList###):\n"
            + "  output.extend([str(1 if operandIndex### == 0 else 0),"
            + "str(1 if operandIndex### == len(operandList###) - 1 else 0),"
            + "str(operandIndex###)])\n";

    assertThatSoyCode(soyCode).compilesTo(expectedPyCode);
  }

  @Test
  public void testForeach_ifempty() {
    String soyCode =
        "{@param operands : ?}\n"
            + "{@param foo : ?}\n"
            + "{foreach $operand in $operands}\n"
            + "  {$operand}\n"
            + "{ifempty}\n"
            + "  {$foo}"
            + "{/foreach}\n";

    String expectedPyCode =
        "operandList### = data.get('operands')\n"
            + "if operandList###:\n"
            + "  for operandIndex###, operandData### in enumerate(operandList###):\n"
            + "    output.append(str(operandData###))\n"
            + "else:\n"
            + "  output.append(str(data.get('foo')))\n";

    assertThatSoyCode(soyCode).compilesTo(expectedPyCode);
  }

  @Test
  public void testLetValue() {
    assertThatSoyCode("{@param boo : ?}\n" + "{let $foo: $boo /}\n")
        .compilesTo("foo__soy### = data.get('boo')\n");
  }

  @Test
  public void testLetContent() {
    String soyCode =
        "{@param boo : ?}\n" + "{let $foo kind=\"html\"}\n" + "  Hello {$boo}\n" + "{/let}\n";

    String expectedPyCode =
        "foo__soy### = ['Hello ',str(data.get('boo'))]\n"
            + "foo__soy### = sanitize.SanitizedHtml(''.join(foo__soy###), "
            + SANITIZATION_APPROVAL
            + ")\n";

    assertThatSoyCode(soyCode).compilesTo(expectedPyCode);
  }

  @Test
  public void testLetContent_notComputableAsExpr() {
    String soyCode =
        "{@param boo : ?}\n"
            + "{let $foo kind=\"html\"}\n"
            + "  {for $num in range(5)}\n"
            + "    {$num}\n"
            + "  {/for}\n"
            + "  Hello {$boo}\n"
            + "{/let}\n";

    String expectedPyCode =
        "foo__soy### = []\n"
            + "for num### in xrange(0, 5, 1):\n"
            + "  foo__soy###.append(str(num###))\n"
            + "foo__soy###.extend(['Hello ',str(data.get('boo'))])\n"
            + "foo__soy### = sanitize.SanitizedHtml(''.join(foo__soy###), "
            + SANITIZATION_APPROVAL
            + ")\n";

    assertThatSoyCode(soyCode).compilesTo(expectedPyCode);
  }

  @Test
  public void testLog() {
    String soyCode = "{@param boo : ?}\n" + "{log}\n" + "  {$boo}\n" + "{/log}\n";

    String expectedPyCode =
        "logger_5 = []\n" + "logger_5.append(str(data.get('boo')))\n" + "print logger_5\n" + "";

    assertThatSoyCode(soyCode).compilesTo(expectedPyCode);
  }

  @Test
  public void testDebugger() {
    String soyCode = "{debugger}";

    String expectedPyCode = "pdb.set_trace()\n";

    assertThatSoyCode(soyCode).compilesTo(expectedPyCode);
  }

  @Test
  public void testLetContent_noContentKind() {
    String soyCode = "{@param boo : ?}\n" + "{let $foo}\n" + "  Hello {$boo}\n" + "{/let}\n";

    assertThatSoyCode(soyCode).compilesWithException(SoySyntaxException.class);
  }

  @Test
  public void testCallReturnsString() {
    String soyCode = "{call .foo data=\"all\" /}";

    String expectedPyCode = "output.append(str(ns.foo(data, ijData)))\n";

    assertThatSoyCode(soyCode).compilesTo(expectedPyCode);
  }
}
