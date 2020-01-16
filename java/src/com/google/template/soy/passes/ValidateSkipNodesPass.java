/*
 * Copyright 2018 Google Inc.
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
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.SkipNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode;

/** Checks for validity of skip nodes wrt their host node. */
final class ValidateSkipNodesPass implements CompilerFilePass {

  private static final SoyErrorKind SOY_SKIP_OPEN_TAG_CLOSE_AMBIGUOUS =
      SoyErrorKind.of("Skip element open tags must map to exactly one close tag.");

  private final ErrorReporter errorReporter;

  public ValidateSkipNodesPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (TemplateNode template : file.getChildren()) {
      int id = 0;
      for (SkipNode skipNode : SoyTreeUtils.getAllNodesOfType(template, SkipNode.class)) {
        HtmlOpenTagNode openTag = (HtmlOpenTagNode) skipNode.getParent();
        openTag.setSkipRoot();
        if (!openTag.isSelfClosing() && openTag.getTaggedPairs().size() > 1) {
          errorReporter.report(openTag.getSourceLocation(), SOY_SKIP_OPEN_TAG_CLOSE_AMBIGUOUS);
        } else {
          skipNode.setSkipId(template.getTemplateName() + "-skip-" + id++);
        }
      }
    }
  }
}
