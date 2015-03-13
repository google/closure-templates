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

import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.template.soy.pysrc.SoyPySrcOptions;
import com.google.template.soy.pysrc.internal.GenPyExprsVisitor.GenPyExprsVisitorFactory;
import com.google.template.soy.shared.SharedTestUtils;
import com.google.template.soy.shared.internal.GuiceSimpleScope;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.BidiIsRtlFn;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.RuntimePath;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.TranslationPyModuleName;
import com.google.template.soy.soytree.SoyNode;

import java.util.List;

/**
 * Truth assertion which compiles the provided soy code and asserts that the generated Python code
 * matches the expected output.
 *
 */
public final class SoyCodeForPySubject extends Subject<SoyCodeForPySubject, String> {

  private static final Injector INJECTOR = Guice.createInjector(new PySrcModule());

  private static final String RUNTIME_PATH = "example.runtime";

  private String bidiIsRtlFn = "";

  private String translationPyModuleName = "";

  private boolean isFile;


  /**
   * A Subject for testing sections of Soy code. The provided data can either be an entire Soy file,
   * or just the body of a template. If just a body is provided, it is wrapped with a simple
   * template before compiling.
   *
   * @param failureStrategy The environment provided FailureStrategy.
   * @param code The input Soy code to be compiled and tested.
   * @param isFile Whether the provided code represents a full file.
   */
  SoyCodeForPySubject(FailureStrategy failureStrategy, String code, boolean isFile) {
    super(failureStrategy, code);
    this.isFile = isFile;
  }


  public SoyCodeForPySubject withBidi(String bidiIsRtlFn) {
    this.bidiIsRtlFn = bidiIsRtlFn;
    return this;
  }

  public SoyCodeForPySubject withTranslationModule(String translationPyModuleName) {
    this.translationPyModuleName = translationPyModuleName;
    return this;
  }

  public void compilesTo(String expectedPyOutput) {
    if (isFile) {
      assertThat(compileFile()).isEqualTo(expectedPyOutput);
    } else {
      assertThat(compileBody()).isEqualTo(expectedPyOutput);
    }
  }

  public void compilesToSourceContaining(String expectedPyOutput) {
    if (isFile) {
      assertThat(compileFile()).contains(expectedPyOutput);
    } else {
      assertThat(compileBody()).contains(expectedPyOutput);
    }
  }

  public void compilesWithException(Class<? extends Exception> expectedClass) {
    try {
      if (isFile) {
        compileFile();
      } else{
        compileBody();
      }
      fail("Compilation suceeded when it should have failed.");
    } catch (Exception actual) {
      assertThat(actual).isInstanceOf(expectedClass);
    }
  }

  private GenPyCodeVisitor getGenPyCodeVisitor() {
    // Setup default configs.
    SoyPySrcOptions pySrcOptions = new SoyPySrcOptions(RUNTIME_PATH, bidiIsRtlFn,
        translationPyModuleName);
    GuiceSimpleScope apiCallScope = SharedTestUtils.simulateNewApiCall(INJECTOR, null, null);
    apiCallScope.seed(SoyPySrcOptions.class, pySrcOptions);
    apiCallScope.seed(Key.get(String.class, RuntimePath.class), RUNTIME_PATH);

    // Add customizable bidi fn and translation module.
    apiCallScope.seed(Key.get(String.class, BidiIsRtlFn.class), bidiIsRtlFn);
    apiCallScope.seed(Key.get(String.class, TranslationPyModuleName.class),
        translationPyModuleName);

    // Execute the compiler.
    return INJECTOR.getInstance(GenPyCodeVisitor.class);
  }

  private String compileFile() {
    SoyNode node = SharedTestUtils.parseSoyFiles(getSubject()).getParseTree();
    List<String> fileContents = getGenPyCodeVisitor().exec(node);
    return fileContents.get(0).replaceAll("([a-zA-Z]+)\\d+", "$1###");
  }

  private String compileBody() {
    SoyNode node = SharedTestUtils.getNode(
        SharedTestUtils.parseStrictSoyCode(getSubject()).getParseTree(), 0);

    // Setup the GenPyCodeVisitor's state before the node is visited.
    GenPyCodeVisitor genPyCodeVisitor = getGenPyCodeVisitor();
    genPyCodeVisitor.pyCodeBuilder = new PyCodeBuilder();
    genPyCodeVisitor.pyCodeBuilder.pushOutputVar("output");
    genPyCodeVisitor.pyCodeBuilder.setOutputVarInited();
    genPyCodeVisitor.localVarExprs = new LocalVariableStack();
    genPyCodeVisitor.localVarExprs.pushFrame();
    genPyCodeVisitor.genPyExprsVisitor =
        INJECTOR.getInstance(GenPyExprsVisitorFactory.class).create(genPyCodeVisitor.localVarExprs);

    genPyCodeVisitor.visit(node); // note: we're calling visit(), not exec()

    return genPyCodeVisitor.pyCodeBuilder.getCode().replaceAll("([a-zA-Z]+)\\d+", "$1###");
  }


  //-----------------------------------------------------------------------------------------------
  // Public static functions for starting a SoyCodeForPySubject test.


  private static final SubjectFactory<SoyCodeForPySubject, String> SOYCODE =
      new SubjectFactory<SoyCodeForPySubject, String>() {
        @Override
        public SoyCodeForPySubject getSubject(FailureStrategy failureStrategy, String code) {
          return new SoyCodeForPySubject(failureStrategy, code, false);
        }
      };

  private static final SubjectFactory<SoyCodeForPySubject, String> SOYFILE =
      new SubjectFactory<SoyCodeForPySubject, String>() {
        @Override
        public SoyCodeForPySubject getSubject(FailureStrategy failureStrategy, String file) {
          return new SoyCodeForPySubject(failureStrategy, file, true);
        }
      };

  public static SoyCodeForPySubject assertThatSoyCode(String code) {
    return assertAbout(SOYCODE).that(code);
  }

  public static SoyCodeForPySubject assertThatSoyFile(String file) {
    return assertAbout(SOYFILE).that(file);
  }
}
