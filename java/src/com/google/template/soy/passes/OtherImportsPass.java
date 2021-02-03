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

package com.google.template.soy.passes;

import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.ImportNode;
import com.google.template.soy.soytree.ImportNode.ImportType;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.types.UnknownType;

/** Add compiler warnings for any imports not of a recognized type. */
@RunBefore(ResolveNamesPass.class)
final class OtherImportsPass implements CompilerFilePass {

  private static final SoyErrorKind BAD_IMPORT_TYPE =
      SoyErrorKind.of("May not import from this type of source file.");

  private final ErrorReporter errorReporter;

  OtherImportsPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    SoyTreeUtils.allNodesOfType(file, ImportNode.class)
        .filter(n -> n.getImportType() == ImportType.UNKNOWN)
        .peek(n -> errorReporter.report(n.getPathSourceLocation(), BAD_IMPORT_TYPE))
        .flatMap(n -> n.getIdentifiers().stream())
        .forEach(n -> n.setType(UnknownType.getInstance()));
  }
}
