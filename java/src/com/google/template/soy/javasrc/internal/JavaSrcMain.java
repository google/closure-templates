/*
 * Copyright 2009 Google Inc.
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

package com.google.template.soy.javasrc.internal;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.internal.i18n.SoyBidiUtils;
import com.google.template.soy.javasrc.SoyJavaSrcOptions;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.internal.InsertMsgsVisitor;
import com.google.template.soy.msgs.internal.InsertMsgsVisitor.EncounteredPluralSelectMsgException;
import com.google.template.soy.shared.internal.ApiCallScopeUtils;
import com.google.template.soy.shared.internal.GuiceSimpleScope;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.ApiCall;
import com.google.template.soy.sharedpasses.opti.SimplifyVisitor;
import com.google.template.soy.soytree.SoyFileSetNode;

import javax.annotation.Nullable;


/**
 * Main entry point for the Java Src backend (output target).
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class JavaSrcMain {


  /** The scope object that manages the API call scope. */
  private final GuiceSimpleScope apiCallScope;

  /** The instanceof of SimplifyVisitor to use. */
  private final SimplifyVisitor simplifyVisitor;

  /** Provider for getting an instance of OptimizeBidiCodeGenVisitor. */
  private final Provider<OptimizeBidiCodeGenVisitor> optimizeBidiCodeGenVisitorProvider;

  /** Provider for getting an instance of GenJavaCodeVisitor. */
  private final Provider<GenJavaCodeVisitor> genJavaCodeVisitorProvider;


  /**
   * @param apiCallScope The scope object that manages the API call scope.
   * @param simplifyVisitor The instance of SimplifyVisitor to use.
   * @param optimizeBidiCodeGenVisitorProvider Provider for getting an instance of
   *     OptimizeBidiCodeGenVisitor.
   * @param genJavaCodeVisitorProvider Provider for getting an instance of GenJavaCodeVisitor.
   */
  @Inject
  public JavaSrcMain(
      @ApiCall GuiceSimpleScope apiCallScope, SimplifyVisitor simplifyVisitor,
      Provider<OptimizeBidiCodeGenVisitor> optimizeBidiCodeGenVisitorProvider,
      Provider<GenJavaCodeVisitor> genJavaCodeVisitorProvider) {
    this.apiCallScope = apiCallScope;
    this.simplifyVisitor = simplifyVisitor;
    this.optimizeBidiCodeGenVisitorProvider = optimizeBidiCodeGenVisitorProvider;
    this.genJavaCodeVisitorProvider = genJavaCodeVisitorProvider;
  }


  /**
   * Generates Java source code given a Soy parse tree, an options object, and an optional bundle of
   * translated messages.
   *
   * @param soyTree The Soy parse tree to generate Java source code for.
   * @param javaSrcOptions The compilation options relevant to this backend.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the Soy
   *     source.
   * @return A list of strings where each string represents the Java source code that belongs in one
   *     Java file. The generated Java files correspond one-to-one to the original Soy source files.
   * @throws SoySyntaxException If a syntax error is found.
   */
  public String genJavaSrc(
      SoyFileSetNode soyTree, SoyJavaSrcOptions javaSrcOptions, @Nullable SoyMsgBundle msgBundle)
      throws SoySyntaxException {

    try {
      (new InsertMsgsVisitor(msgBundle, false)).exec(soyTree);
    } catch (EncounteredPluralSelectMsgException e) {
      throw new SoySyntaxException("JavaSrc backend doesn't support plural/select messages.");
    }

    apiCallScope.enter();
    try {
      // Seed the scoped parameters.
      apiCallScope.seed(SoyJavaSrcOptions.class, javaSrcOptions);
      BidiGlobalDir bidiGlobalDir =
          SoyBidiUtils.decodeBidiGlobalDir(javaSrcOptions.getBidiGlobalDir());
      ApiCallScopeUtils.seedSharedParams(apiCallScope, msgBundle, bidiGlobalDir);

      // Do the code generation.
      optimizeBidiCodeGenVisitorProvider.get().exec(soyTree);
      simplifyVisitor.exec(soyTree);
      return genJavaCodeVisitorProvider.get().exec(soyTree);

    } finally {
      apiCallScope.exit();
    }
  }

}
