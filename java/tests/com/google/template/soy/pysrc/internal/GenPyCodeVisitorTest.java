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
import com.google.template.soy.shared.internal.GuiceSimpleScope;
import com.google.template.soy.shared.internal.SharedTestUtils;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.RuntimePath;
import com.google.template.soy.soytree.SoyFileSetNode;

import junit.framework.TestCase;

import java.util.List;

/**
 * Unit tests for GenPyCodeVisitor.
 *
 */
public final class GenPyCodeVisitorTest extends TestCase {

  private static final Injector INJECTOR = Guice.createInjector(new PySrcModule());

  private SoyPySrcOptions pySrcOptions;

  private GenPyCodeVisitor genPyCodeVisitor;

  @Override protected void setUp() {
    pySrcOptions = new SoyPySrcOptions("", "");
    GuiceSimpleScope apiCallScope = SharedTestUtils.simulateNewApiCall(INJECTOR, null, null);
    apiCallScope.seed(SoyPySrcOptions.class, pySrcOptions);
    apiCallScope.seed(Key.get(String.class, RuntimePath.class), "example.runtime");
    genPyCodeVisitor = INJECTOR.getInstance(GenPyCodeVisitor.class);
  }

  public void testSoyFile() {

    String testFileContent = "{namespace boo.foo autoescape=\"deprecated-noncontextual\"}\n";

    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(testFileContent);

    // ------ Not using Closure ------
    String expectedPyFileContentStart =
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

    List<String> pyFilesContents = genPyCodeVisitor.exec(soyTree);
    assertEquals(expectedPyFileContentStart, pyFilesContents.get(0));

    // TODO(dcphillips): Add external template dependency import test once templates are supported.
  }
}
