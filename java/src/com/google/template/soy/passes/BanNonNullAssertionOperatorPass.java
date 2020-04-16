/*
 * Copyright 2020 Google Inc.
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
import com.google.template.soy.exprtree.OperatorNodes.AssertNonNullOpNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;

/** Report errors on nonnull assertion operators (!) until they're ready for release. */
final class BanNonNullAssertionOperatorPass implements CompilerFilePass {

  private static final SoyErrorKind NON_NULL_ASSERTION_BANNED =
      SoyErrorKind.of(
          "Non-null assertion operator not supported, use the ''checkNotNull'' function instead.");

  private final ErrorReporter errorReporter;

  BanNonNullAssertionOperatorPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (AssertNonNullOpNode assertNonNullOpNode :
        SoyTreeUtils.getAllNodesOfType(file, AssertNonNullOpNode.class)) {
      errorReporter.report(assertNonNullOpNode.getSourceLocation(), NON_NULL_ASSERTION_BANNED);
    }
  }
}
