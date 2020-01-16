/*
 * Copyright 2011 Google Inc.
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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.error.SoyErrors;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateMetadata;
import com.google.template.soy.soytree.TemplateRegistry;

/**
 * Visitor to check that there are no external calls. Used by backends that disallow external calls,
 * such as the Tofu (JavaObj) backend.
 */
public final class StrictDepsPass implements CompilerFileSetPass {

  private static final SoyErrorKind CALL_TO_UNDEFINED_TEMPLATE =
      SoyErrorKind.of("Undefined template ''{0}''.{1}", StyleAllowance.NO_PUNCTUATION);
  private static final SoyErrorKind CALL_TO_INDIRECT_DEPENDENCY =
      SoyErrorKind.of(
          "Call is satisfied only by indirect dependency {0}. Add it as a direct dependency."
          ,
          StyleAllowance.NO_PUNCTUATION);

  private final ErrorReporter errorReporter;

  public StrictDepsPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public Result run(
      ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator, TemplateRegistry registry) {
    for (SoyFileNode file : sourceFiles) {
      for (CallBasicNode node : SoyTreeUtils.getAllNodesOfType(file, CallBasicNode.class)) {
        checkBasicCall(node, registry);
      }
    }
    return Result.CONTINUE;
  }

  // TODO(gboyer): Consider some deltemplate checking, but it's hard to make a coherent case for
  // deltemplates since it's legitimate to have zero implementations, or to have the implementation
  // in a different part of the dependency graph (if it's late-bound).
  private void checkBasicCall(CallBasicNode node, TemplateRegistry registry) {
    TemplateMetadata callee = registry.getBasicTemplateOrElement(node.getCalleeName());

    if (callee == null) {
      String extraErrorMessage =
          SoyErrors.getDidYouMeanMessage(
              registry.getBasicTemplateOrElementNames(), node.getCalleeName());
      errorReporter.report(
          node.getSourceLocation(),
          CALL_TO_UNDEFINED_TEMPLATE,
          node.getCalleeName(),
          extraErrorMessage);
    } else {
      SoyFileKind calleeKind = callee.getSoyFileKind();
      String callerFilePath = node.getSourceLocation().getFilePath();
      String calleeFilePath = callee.getSourceLocation().getFilePath();
      if (calleeKind == SoyFileKind.INDIRECT_DEP) {
        errorReporter.report(
            node.getSourceLocation(),
            CALL_TO_INDIRECT_DEPENDENCY,
            calleeFilePath);
      }
    }
  }
}
