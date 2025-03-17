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

package com.google.template.soy.conformance;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.soytree.FileMetadata.Extern;

/**
 * Conformance rule banning particular Soy functions. For built-in functions and plug-in functions
 * the string representation is the global name of the function, e.g. `parseInt`. For an extern the
 * string representation is `{name} from 'path/to/file.soy'` (like an import statement).
 */
final class BannedFunction extends Rule<FunctionNode> {

  private final ImmutableSet<String> bannedFunctions;

  BannedFunction(ImmutableSet<String> bannedFunctions, SoyErrorKind error) {
    super(error);
    this.bannedFunctions = bannedFunctions;
  }

  @Override
  protected void doCheckConformance(FunctionNode node, ErrorReporter errorReporter) {
    String functionStr = node.getFunctionName();
    if (functionStr.isEmpty()) {
      if (node.isResolved()) {
        Object functImpl = node.getSoyFunction();
        if (functImpl instanceof Extern) {
          functionStr = externStringRepresentation((Extern) functImpl);
        }
      }
    }

    if (!functionStr.isEmpty() && bannedFunctions.contains(functionStr)) {
      errorReporter.report(node.getSourceLocation(), error);
    }
  }

  static String externStringRepresentation(Extern ref) {
    return String.format("{%s} from '%s'", ref.getName(), ref.getPath().path());
  }
}
