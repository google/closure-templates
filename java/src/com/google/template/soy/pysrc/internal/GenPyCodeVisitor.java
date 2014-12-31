/*
 * Copyright 2014 Google Inc.
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

import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.RuntimePath;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.SoyNode;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;


/**
 * Note: Unimplemented shell for future implementation
 * TODO(dcphillips): Implement!
 *
 * <p>Visitor for generating full Python code (i.e. statements) for parse tree nodes.
 *
 * <p> {@link #exec} should be called on a full parse tree. Python source code will be generated
 * for all the Soy files. The return value is a list of strings, each string being the content of
 * one generated Python file (corresponding to one Soy file).
 *
 */
final class GenPyCodeVisitor extends AbstractSoyNodeVisitor<List<String>> {

  /** The module path for the runtime libraries. */
  private final String runtimePath;

  /**
   * @param runtimePath The module path for the runtime libraries.
   */
  @Inject
  GenPyCodeVisitor(@RuntimePath String runtimePath) {
    this.runtimePath = runtimePath;
  }

  @Override public List<String> exec(SoyNode node) {
    List<String> pyFilesContents = new ArrayList<String>();
    visit(node);
    return pyFilesContents;
  }

}
