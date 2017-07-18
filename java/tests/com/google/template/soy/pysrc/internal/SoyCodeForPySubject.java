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

import com.google.common.collect.ImmutableMap;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.SoyModule;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.pysrc.SoyPySrcOptions;
import com.google.template.soy.pysrc.internal.GenPyExprsVisitor.GenPyExprsVisitorFactory;
import com.google.template.soy.pysrc.internal.PyApiCallScopeBindingAnnotations.PyCurrentManifest;
import com.google.template.soy.shared.AutoEscapingType;
import com.google.template.soy.shared.SharedTestUtils;
import com.google.template.soy.shared.internal.ApiCallScopeUtils;
import com.google.template.soy.shared.internal.GuiceSimpleScope;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.ApiCall;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import java.util.List;

/**
 * Truth assertion which compiles the provided soy code and asserts that the generated Python code
 * matches the expected output.
 *
 */
public final class SoyCodeForPySubject extends Subject<SoyCodeForPySubject, String> {

  private static final String RUNTIME_PATH = "example.runtime";

  private String bidiIsRtlFn = "";

  private String environmentModulePath = "";

  private String translationClass = "";

  private ImmutableMap<String, String> namespaceManifest = ImmutableMap.<String, String>of();

  private boolean isFile;

  private final Injector injector;

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
    this.injector = Guice.createInjector(new SoyModule());
  }

  public SoyCodeForPySubject withEnvironmentModule(String environmentModulePath) {
    this.environmentModulePath = environmentModulePath;
    return this;
  }

  public SoyCodeForPySubject withBidi(String bidiIsRtlFn) {
    this.bidiIsRtlFn = bidiIsRtlFn;
    return this;
  }

  public SoyCodeForPySubject withTranslationClass(String translationClass) {
    this.translationClass = translationClass;
    return this;
  }

  public SoyCodeForPySubject withNamespaceManifest(ImmutableMap<String, String> namespaceManifest) {
    this.namespaceManifest = namespaceManifest;
    return this;
  }

  /**
   * Asserts that the subject compiles to the expected Python output.
   *
   * <p>During compilation, freestanding bodies are compiled as strict templates with the output
   * variable already being initialized. Additionally, any automatically generated variables have
   * generated IDs replaced with '###'. Thus 'name123' would become 'name###'.
   *
   * @param expectedPyOutput The expected Python result of compilation.
   */
  public void compilesTo(String expectedPyOutput) {
    if (isFile) {
      assertThat(compileFile()).isEqualTo(expectedPyOutput);
    } else {
      assertThat(compileBody()).isEqualTo(expectedPyOutput);
    }
  }

  /**
   * Asserts that the subject compiles to Python output which contains the expected output.
   *
   * <p>Compilation follows the same rules as {@link #compilesTo}.
   *
   * @param expectedPyOutput The expected Python result of compilation.
   */
  public void compilesToSourceContaining(String expectedPyOutput) {
    if (isFile) {
      assertThat(compileFile()).contains(expectedPyOutput);
    } else {
      assertThat(compileBody()).contains(expectedPyOutput);
    }
  }

  /**
   * Asserts that the subject compilation throws the expected exception.
   *
   * <p>Compilation follows the same rules as {@link #compilesTo}.
   *
   * @param expectedClass The class of the expected exception.
   */
  public void compilesWithException(Class<? extends Exception> expectedClass) {
    try {
      if (isFile) {
        compileFile();
      } else {
        compileBody();
      }
      fail("Compilation suceeded when it should have failed.");
    } catch (Exception actual) {
      assertThat(actual).isInstanceOf(expectedClass);
    }
  }

  private GuiceSimpleScope.InScope enterScope() {
    // Setup default configs.
    SoyPySrcOptions pySrcOptions =
        new SoyPySrcOptions(
            RUNTIME_PATH,
            environmentModulePath,
            bidiIsRtlFn,
            translationClass,
            namespaceManifest,
            false);
    GuiceSimpleScope apiCallScope1 =
        injector.getInstance(Key.get(GuiceSimpleScope.class, ApiCall.class));

    GuiceSimpleScope.InScope scope = apiCallScope1.enter();
    ApiCallScopeUtils.seedSharedParams(scope, null, BidiGlobalDir.LTR);
    scope.seed(SoyPySrcOptions.class, pySrcOptions);
    scope.seed(
        new Key<ImmutableMap<String, String>>(PyCurrentManifest.class) {},
        ImmutableMap.<String, String>of());
    return scope;
  }

  private String compileFile() {
    SoyFileSetNode node = SoyFileSetParserBuilder.forFileContents(actual()).parse().fileSet();
    try (GuiceSimpleScope.InScope inScope = enterScope()) {
      List<String> fileContents =
          injector.getInstance(GenPyCodeVisitor.class).gen(node, ErrorReporter.exploding());
      return fileContents.get(0).replaceAll("([a-zA-Z]+)\\d+", "$1###");
    }
  }

  private String compileBody() {
    SoyNode node =
        SharedTestUtils.getNode(
            SoyFileSetParserBuilder.forTemplateContents(AutoEscapingType.STRICT, actual())
                .declaredSyntaxVersion(SyntaxVersion.V2_0)
                .parse()
                .fileSet(),
            0);

    // Setup the GenPyCodeVisitor's state before the node is visited.
    try (GuiceSimpleScope.InScope inScope = enterScope()) {
      GenPyCodeVisitor genPyCodeVisitor = injector.getInstance(GenPyCodeVisitor.class);
      genPyCodeVisitor.pyCodeBuilder = new PyCodeBuilder();
      genPyCodeVisitor.pyCodeBuilder.pushOutputVar("output");
      genPyCodeVisitor.pyCodeBuilder.setOutputVarInited();
      genPyCodeVisitor.localVarExprs = new LocalVariableStack();
      genPyCodeVisitor.localVarExprs.pushFrame();
      genPyCodeVisitor.genPyExprsVisitor =
          injector
              .getInstance(GenPyExprsVisitorFactory.class)
              .create(genPyCodeVisitor.localVarExprs, ErrorReporter.exploding());

      genPyCodeVisitor.visitForTesting(node, ErrorReporter.exploding());

      return genPyCodeVisitor.pyCodeBuilder.getCode().replaceAll("([a-zA-Z]+)\\d+", "$1###");
    }
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
