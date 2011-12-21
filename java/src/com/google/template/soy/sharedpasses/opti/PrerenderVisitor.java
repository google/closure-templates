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

import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.shared.restricted.SoyJavaRuntimePrintDirective;
import com.google.template.soy.sharedpasses.render.RenderException;
import com.google.template.soy.sharedpasses.render.RenderVisitor;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.CssNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.jssrc.GoogMsgNode;
import com.google.template.soy.soytree.jssrc.GoogMsgRefNode;

import java.util.Deque;
import java.util.Map;

import javax.annotation.Nullable;


/**
 * Visitor for prerendering the template subtree rooted at a given SoyNode. This is possible when
 * all data values are known at compile time.
 *
 * Package-private helper for {@link SimplifyVisitor}.
 *
 * <p> The rendered output will be appended to the {@code outputSb} provided to the constructor.
 *
 */
class PrerenderVisitor extends RenderVisitor {


  /**
   * @param soyJavaRuntimeDirectivesMap Map of all SoyJavaRuntimePrintDirectives (name to
   *     directive).
   * @param preevalVisitorFactory Factory for creating an instance of PreevalVisitor.
   * @param prerenderVisitorFactory Factory for creating an instance of PrerenderVisitor.
   * @param outputSb The Appendable to append the output to.
   * @param templateRegistry A registry of all templates.
   * @param data The current template data.
   * @param env The current environment, or null if this is the initial call.
   */
  PrerenderVisitor(
      Map<String, SoyJavaRuntimePrintDirective> soyJavaRuntimeDirectivesMap,
      PreevalVisitorFactory preevalVisitorFactory, PrerenderVisitorFactory prerenderVisitorFactory,
      Appendable outputSb, @Nullable TemplateRegistry templateRegistry,
      @Nullable SoyMapData data, @Nullable Deque<Map<String, SoyData>> env) {

    super(
        soyJavaRuntimeDirectivesMap, preevalVisitorFactory, prerenderVisitorFactory, outputSb,
        templateRegistry, data, null, env, null, null, null);
  }


  @Override public Void exec(SoyNode soyNode) {

    // Note: This is a catch-all to turn RuntimeExceptions that aren't RenderExceptions into
    // RenderExceptions during prerendering.

    try {
      return super.exec(soyNode);

    } catch (RenderException e) {
      throw e;

    } catch (RuntimeException e) {
      throw new RenderException("Failed prerender due to exception: " + e.getMessage());
    }
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.


  @Override protected void visitMsgNode(MsgNode node) {
    throw new RenderException("Cannot prerender MsgNode.");
  }


  @Override protected void visitGoogMsgNode(GoogMsgNode node) {
    throw new RenderException("Cannot prerender GoogMsgNode.");
  }


  @Override protected void visitGoogMsgRefNode(GoogMsgRefNode node) {
    throw new RenderException("Cannot prerender GoogMsgRefNode.");
  }


  @Override protected void visitCssNode(CssNode node) {
    throw new RenderException("Cannot prerender CssNode.");
  }


  @Override protected void visitCallDelegateNode(CallDelegateNode node) {
    throw new RenderException("Cannot prerender CallDelegateNode.");
  }

}
