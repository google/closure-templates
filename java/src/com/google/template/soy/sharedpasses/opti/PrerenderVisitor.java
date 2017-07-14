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

package com.google.template.soy.sharedpasses.opti;

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import com.google.template.soy.shared.restricted.SoyPurePrintDirective;
import com.google.template.soy.sharedpasses.render.Environment;
import com.google.template.soy.sharedpasses.render.RenderException;
import com.google.template.soy.sharedpasses.render.RenderVisitor;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.CssNode;
import com.google.template.soy.soytree.DebuggerNode;
import com.google.template.soy.soytree.LogNode;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.TemplateRegistry;
import javax.annotation.Nullable;

/**
 * Visitor for prerendering the template subtree rooted at a given SoyNode. This is possible when
 * all data values are known at compile time.
 *
 * <p>Package-private helper for {@link SimplifyVisitor}.
 *
 * <p>The rendered output will be appended to the Appendable provided to the constructor.
 *
 */
final class PrerenderVisitor extends RenderVisitor {

  /**
   * @param soyJavaDirectivesMap Map of all SoyJavaPrintDirectives (name to directive).
   * @param preevalVisitorFactory Factory for creating an instance of PreevalVisitor.
   * @param outputBuf The Appendable to append the output to.
   * @param templateRegistry A registry of all templates.
   */
  PrerenderVisitor(
      ImmutableMap<String, ? extends SoyJavaPrintDirective> soyJavaDirectivesMap,
      PreevalVisitorFactory preevalVisitorFactory,
      Appendable outputBuf,
      @Nullable TemplateRegistry templateRegistry) {
    super(
        soyJavaDirectivesMap,
        preevalVisitorFactory,
        outputBuf,
        templateRegistry,
        SoyValueConverter.EMPTY_DICT,
        /* ijData= */ null,
        /* activeDelPackageNames= */ null,
        /* msgBundle= */ null,
        /* xidRenamingMap= */ null,
        /* cssRenamingMap= */ null,
        /* debugSoyTemplateInfo= */ false);
  }

  @Override
  protected PrerenderVisitor createHelperInstance(Appendable outputBuf, SoyRecord data) {

    return new PrerenderVisitor(
        soyJavaDirectivesMap,
        (PreevalVisitorFactory) evalVisitorFactory,
        outputBuf,
        templateRegistry);
  }

  @Override
  public Void exec(SoyNode soyNode) {
    // Set the environment to be empty for each node.  This will set all params to Undefined.
    env = Environment.prerenderingEnvironment();
    // Note: This is a catch-all to turn RuntimeExceptions that aren't RenderExceptions into
    // RenderExceptions during prerendering.

    try {
      return super.exec(soyNode);

    } catch (RenderException e) {
      throw e;

    } catch (RuntimeException e) {
      throw RenderException.create("Failed prerender due to exception: " + e.getMessage(), e);
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.

  @Override
  protected void visitMsgFallbackGroupNode(MsgFallbackGroupNode node) {
    throw RenderException.create("Cannot prerender MsgFallbackGroupNode.");
  }

  @Override
  protected void visitCssNode(CssNode node) {
    throw RenderException.create("Cannot prerender CssNode.");
  }

  @Override
  protected void visitCallDelegateNode(CallDelegateNode node) {
    throw RenderException.create("Cannot prerender CallDelegateNode.");
  }

  @Override
  protected void visitLogNode(LogNode node) {
    throw RenderException.create("Cannot prerender LogNode.");
  }

  @Override
  protected void visitDebuggerNode(DebuggerNode node) {
    throw RenderException.create("Cannot prerender DebuggerNode.");
  }

  @Override
  protected void visitPrintNode(PrintNode node) {
    for (PrintDirectiveNode directiveNode : node.getChildren()) {
      if (!isSoyPurePrintDirective(directiveNode)) {
        throw RenderException.create("Cannot prerender a node with some impure print directive.");
      }
    }
    super.visitPrintNode(node);
  }

  @Override
  protected void visitPrintDirectiveNode(PrintDirectiveNode node) {
    if (!isSoyPurePrintDirective(node)) {
      throw RenderException.create("Cannot prerender impure print directive.");
    }
    super.visitPrintDirectiveNode(node);
  }

  private boolean isSoyPurePrintDirective(PrintDirectiveNode node) {
    SoyJavaPrintDirective directive = soyJavaDirectivesMap.get(node.getName());
    return directive != null
        && directive.getClass().isAnnotationPresent(SoyPurePrintDirective.class);
  }
}
