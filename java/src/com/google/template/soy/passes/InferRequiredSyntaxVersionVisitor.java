/*
 * Copyright 2013 Google Inc.
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

import com.google.common.base.Preconditions;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.HeaderParam;
import com.google.template.soy.soytree.defn.TemplateParam;
import java.util.List;

/**
 * Visitor to infer the required syntax version of a Soy file due to features used.
 *
 * <p>The node passed to {@code exec()} must be a {@code SoyFileNode}.
 *
 */
public final class InferRequiredSyntaxVersionVisitor extends AbstractSoyNodeVisitor<SyntaxVersion> {

  /** The highest known required syntax version so far (during a pass). */
  private SyntaxVersion knownRequiredSyntaxVersion;

  @Override
  public SyntaxVersion exec(SoyNode node) {
    Preconditions.checkArgument(node instanceof SoyFileNode);

    knownRequiredSyntaxVersion = SyntaxVersion.V1_0;
    visit(node);
    return knownRequiredSyntaxVersion;
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.

  @Override
  protected void visitTemplateNode(TemplateNode node) {
    if (knownRequiredSyntaxVersion.num < SyntaxVersion.V2_3.num) {
      List<TemplateParam> params = node.getParams();
      for (TemplateParam param : params) {
        if (param instanceof HeaderParam) {
          knownRequiredSyntaxVersion = SyntaxVersion.V2_3;
          break;
        }
      }
    }
    if (knownRequiredSyntaxVersion.num < SyntaxVersion.V2_4.num
        && !node.getInjectedParams().isEmpty()) {
      knownRequiredSyntaxVersion = SyntaxVersion.V2_4;
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.

  @Override
  protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode<?>) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }
}
