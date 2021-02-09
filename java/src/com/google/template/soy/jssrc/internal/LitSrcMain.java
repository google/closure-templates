/*
 * Copyright 2021 Google Inc.
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

package com.google.template.soy.jssrc.internal;

import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.types.SoyTypeRegistry;
import java.util.List;

/**
 * Main entry point for the lit-html JS Src backend (output target).
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public class LitSrcMain {

  public LitSrcMain(SoyTypeRegistry typeRegistry) {}

  /**
   * Generates lit-html JS source code given a Soy parse tree, an options object, and an optional
   * bundle of translated messages.
   *
   * @param soyTree The Soy parse tree to generate JS source code for.
   * @param errorReporter The Soy error reporter that collects errors during code generation.
   * @return A list of strings where each string represents the JS source code that belongs in one
   *     JS file. The generated JS files correspond one-to-one to the original Soy source files.
   */
  public List<String> genJsSrc(SoyFileSetNode soyTree, ErrorReporter errorReporter) {

    return new GenLitCodeVisitor().gen(soyTree, errorReporter);
  }
}
