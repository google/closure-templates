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

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.template.soy.pysrc.SoyPySrcOptions;
import com.google.template.soy.shared.SharedTestUtils;
import com.google.template.soy.shared.internal.GuiceSimpleScope;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.RuntimePath;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.TranslationPyModuleName;
import com.google.template.soy.soytree.SoyNode;

import junit.framework.TestCase;

import java.util.List;

/**
 * Unit tests for GenPyCodeVisitor.
 *
 */
public final class GenPyCodeVisitorTest extends TestCase {

  private static final Injector INJECTOR = Guice.createInjector(new PySrcModule());

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
      + "from foo.bar import SimpleTranslator\n"
      + "translator_impl = SimpleTranslator()"
      + "\n"
      + "\n"
      + "SOY_NAMESPACE = 'boo.foo'\n"
      + "try:\n"
      + "  str = unicode\n"
      + "except NameError:\n"
      + "  pass\n"
      + "\n";

  private SoyPySrcOptions pySrcOptions;

  private GenPyCodeVisitor genPyCodeVisitor;

  @Override protected void setUp() {
    pySrcOptions = new SoyPySrcOptions("", "", "");
    GuiceSimpleScope apiCallScope = SharedTestUtils.simulateNewApiCall(INJECTOR, null, null);
    apiCallScope.seed(SoyPySrcOptions.class, pySrcOptions);
    apiCallScope.seed(Key.get(String.class, RuntimePath.class), "example.runtime");
    apiCallScope.seed(Key.get(String.class, TranslationPyModuleName.class),
        "foo.bar.SimpleTranslator");
    genPyCodeVisitor = INJECTOR.getInstance(GenPyCodeVisitor.class);
  }

  public void testSoyFile() {
    String soyCode = "{namespace boo.foo autoescape=\"strict\"}\n";

    assertGeneratedPyFile(soyCode, EXPECTED_PYFILE_START);

    // TODO(dcphillips): Add external template dependency import test once templates are supported.
  }

  public void testBlankTemplate() {
    String soyCode = "{namespace boo.foo autoescape=\"strict\"}\n"
        + "{template .helloWorld kind=\"html\"}\n"
        + "{/template}\n";

    String expectedPyCode = EXPECTED_PYFILE_START + "\n\n"
        + "def helloWorld(opt_data=None, opt_ijData=None):\n"
        + "  output = []\n"
        + "  return sanitize.SanitizedHtml(''.join(output))\n";
    assertGeneratedPyFile(soyCode, expectedPyCode);
  }

  public void testSimpleTemplate() {
    String soyCode = "{namespace boo.foo autoescape=\"strict\"}\n"
        + "{template .helloWorld kind=\"html\"}\n"
        + "  Hello World!\n"
        + "{/template}\n";

    String expectedPyCode = EXPECTED_PYFILE_START + "\n\n"
        + "def helloWorld(opt_data=None, opt_ijData=None):\n"
        + "  output = []\n"
        + "  output.append('Hello World!')\n"
        + "  return sanitize.SanitizedHtml(''.join(output))\n";
    assertGeneratedPyFile(soyCode, expectedPyCode);
  }


  // -----------------------------------------------------------------------------------------------
  // Test Utilities.

  private void assertGeneratedPyFile(String soyCode, String expectedPyCode) {
    String generatedCode = getGeneratedPyFile(soyCode);
    assertEquals(expectedPyCode, generatedCode);
  }

  /**
   * Generates Python code from the given soy 'file'.
   *
   * @param soyCode The soy code.
   */
  private String getGeneratedPyFile(String soyCode) {
    SoyNode node = SharedTestUtils.parseSoyFiles(soyCode);

    List<String> fileContents = genPyCodeVisitor.exec(node);
    return fileContents.get(0);
  }
}
