/*
 * Copyright 2012 Google Inc.
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

package com.google.template.soy.parsepasses.contextautoesc;

import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.AutoescapeMode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.RenderUnitNode;
import com.google.template.soy.soytree.TemplateNode;


/**
 * Visitor performing escaping sanity checks over all input -- not just input affected by the
 * contextual autoescaping inference engine.
 *
 * Checks that typed {@code {param}} and {@code {let}} nodes only appear in contextually
 * autoescaped templates.
 *
 * Checks that internal-only directives such as {@code |text} are not used.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p>{@link #exec} should be called on a full parse tree.
 *
 * @author Christoph Kern
 */
public final class CheckEscapingSanityVisitor extends AbstractSoyNodeVisitor<Void> {
  /** Current escaping mode. */
  private AutoescapeMode autoescapeMode;


  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.


  /** Returns whether the template currently being visited is contextually autoescaped. */
  private boolean isCurrTemplateContextuallyAutoescaped() {
    return (autoescapeMode == AutoescapeMode.CONTEXTUAL)
        || (autoescapeMode == AutoescapeMode.STRICT);
  }

  @Override protected void visitTemplateNode(TemplateNode node) {
    autoescapeMode = node.getAutoescapeMode();
    visitChildren(node);
  }

  @Override protected void visitPrintDirectiveNode(PrintDirectiveNode node) {
    EscapingMode escapingMode = EscapingMode.fromDirective(node.getName());
    if (escapingMode != null && escapingMode.isInternalOnly) {
       throw SoyAutoescapeException.createWithNode(
           "Print directive " + node.getName() + " is only for internal use by the Soy compiler.",
           node);
    }
  }

  @Override protected void visitLetContentNode(LetContentNode node) {
    visitRenderUnitNode(node, "let", "{let $x: $y /}");
  }

  @Override protected void visitCallParamContentNode(CallParamContentNode node) {
    visitRenderUnitNode(node, "param", "{param x: $y /}");
  }

  private void visitRenderUnitNode(
      RenderUnitNode node, String nodeName, String selfClosingExample) {
    final AutoescapeMode oldMode = autoescapeMode;
    if (node.getContentKind() != null) {
      if (!isCurrTemplateContextuallyAutoescaped()) {
        throw SoyAutoescapeException.createWithNode(
            "{" + nodeName + "} node with 'kind' attribute is only permitted in contextually " +
                "autoescaped templates: " + node.toSourceString(),
            node);
      }
      // Temporarily enter strict mode.
      autoescapeMode = AutoescapeMode.STRICT;
    } else if (autoescapeMode == AutoescapeMode.STRICT) {
      throw SoyAutoescapeException.createWithNode(
          "In strict templates, {" + nodeName + "}...{/" + nodeName + "} blocks require an " +
              "explicit kind=\"<type>\". This restriction will be lifted soon once a reasonable " +
              "default is chosen. (Note that " + selfClosingExample + " is NOT subject to this " +
          "restriction). Cause: " + node.getTagString(),
          node);
    }
    visitChildren(node);
    // Pop out of strict mode if we entered it just for this unit.
    autoescapeMode = oldMode;
  }

  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.


  @Override protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode<?>) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }
}
