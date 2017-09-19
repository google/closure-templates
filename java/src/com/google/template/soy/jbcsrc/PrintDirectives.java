/*
 * Copyright 2017 Google Inc.
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
package com.google.template.soy.jbcsrc;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.jbcsrc.ExpressionCompiler.BasicExpressionCompiler;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.SoyJbcSrcPrintDirective;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import java.util.List;

/** Utilities for working with {@link SoyPrintDirective print directives}. */
final class PrintDirectives {

  static boolean areAllPrintDirectivesStreamable(PrintNode node) {
    for (PrintDirectiveNode directiveNode : node.getChildren()) {
      if (!(directiveNode.getPrintDirective() instanceof SoyJbcSrcPrintDirective.Streamable)) {
        return false;
      }
    }
    return true;
  }

  static boolean areAllPrintDirectivesStreamable(CallNode node) {
    return areAllPrintDirectivesStreamable(node.getEscapingDirectives());
  }

  static boolean areAllPrintDirectivesStreamable(MsgFallbackGroupNode node) {
    return areAllPrintDirectivesStreamable(node.getEscapingDirectives());
  }

  static boolean areAllPrintDirectivesStreamable(
      ImmutableList<SoyPrintDirective> escapingDirectives) {
    for (SoyPrintDirective directive : escapingDirectives) {
      if (!(directive instanceof SoyJbcSrcPrintDirective.Streamable)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Applies all the streaming print directives to the appendable.
   *
   * @param directives The directives. All are required to be {@link
   *     com.google.template.soy.jbcsrc.restricted.SoyJbcSrcPrintDirective.Streamable streamable}
   * @param appendable The appendable to wrap
   * @param context The render context for the plugins
   * @return The wrapped appendable
   */
  static Expression applyStreamingEscapingDirectives(
      List<SoyPrintDirective> directives, Expression appendable, JbcSrcPluginContext context) {
    for (SoyPrintDirective directive : directives) {
      appendable =
          ((SoyJbcSrcPrintDirective.Streamable) directive)
              .applyForJbcSrcStreaming(context, appendable, ImmutableList.of());
    }
    return appendable;
  }

  /**
   * Applies all the streaming print directives to the appendable.
   *
   * @param directives The directives. All are required to be {@link
   *     com.google.template.soy.jbcsrc.restricted.SoyJbcSrcPrintDirective.Streamable streamable}
   * @param appendable The appendable to wrap
   * @param basic The expression compiler to use for compiling the arguments
   * @param renderContext The render context for the plugins
   * @return The wrapped appendable
   */
  static Expression applyStreamingPrintDirectives(
      List<PrintDirectiveNode> directives,
      Expression appendable,
      BasicExpressionCompiler basic,
      JbcSrcPluginContext renderContext) {
    for (PrintDirectiveNode directive : directives) {
      appendable =
          ((SoyJbcSrcPrintDirective.Streamable) directive.getPrintDirective())
              .applyForJbcSrcStreaming(
                  renderContext, appendable, basic.compileToList(directive.getArgs()));
    }
    return appendable;
  }

  private PrintDirectives() {}
}
