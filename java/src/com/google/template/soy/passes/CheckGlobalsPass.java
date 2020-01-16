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

package com.google.template.soy.passes;

import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;

/**
 * An optional pass for ensuring that all globals have had their values resolved.
 *
 * <p>This is optional because the {@code jssrc} backend allows for unbound globals and many
 * projects rely on it. All other backends require globals to be substituted.
 *
 */
final class CheckGlobalsPass implements CompilerFilePass {
  private static final SoyErrorKind UNBOUND_GLOBAL = SoyErrorKind.of("Unbound global ''{0}''.");

  private final ErrorReporter errorReporter;

  CheckGlobalsPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (GlobalNode global : SoyTreeUtils.getAllNodesOfType(file, GlobalNode.class)) {
      if (!global.isResolved() && !global.shouldSuppressUnknownGlobalErrors()) {
        errorReporter.report(global.getSourceLocation(), UNBOUND_GLOBAL, global.getName());
      }
    }
  }
}
