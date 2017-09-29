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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.jbcsrc.ExpressionCompiler.BasicExpressionCompiler;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyJbcSrcPrintDirective;
import com.google.template.soy.jbcsrc.restricted.SoyJbcSrcPrintDirective.Streamable.AppendableAndOptions;
import com.google.template.soy.jbcsrc.runtime.JbcSrcRuntime;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import java.util.ArrayList;
import java.util.List;

/** Utilities for working with {@link SoyPrintDirective print directives}. */
final class PrintDirectives {

  private static final MethodRef RUNTIME_PROPAGATE_CLOSE =
      MethodRef.create(
          JbcSrcRuntime.class,
          "propagateClose",
          LoggingAdvisingAppendable.class,
          ImmutableList.class);

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
  static AppendableAndOptions applyStreamingEscapingDirectives(
      List<SoyPrintDirective> directives, Expression appendable, JbcSrcPluginContext context) {
    List<DirectiveWithArgs> directivesToApply = new ArrayList<>();
    for (SoyPrintDirective directive : directives) {
      directivesToApply.add(
          DirectiveWithArgs.create((SoyJbcSrcPrintDirective.Streamable) directive));
    }
    return applyStreamingPrintDirectivesTo(directivesToApply, appendable, context);
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
  static AppendableAndOptions applyStreamingPrintDirectives(
      List<PrintDirectiveNode> directives,
      Expression appendable,
      BasicExpressionCompiler basic,
      JbcSrcPluginContext renderContext) {
    List<DirectiveWithArgs> directivesToApply = new ArrayList<>();
    for (PrintDirectiveNode directive : directives) {
      directivesToApply.add(
          DirectiveWithArgs.create(
              (SoyJbcSrcPrintDirective.Streamable) directive.getPrintDirective(),
              basic.compileToList(directive.getArgs())));
    }
    return applyStreamingPrintDirectivesTo(directivesToApply, appendable, renderContext);
  }

  private static <T> AppendableAndOptions applyStreamingPrintDirectivesTo(
      List<DirectiveWithArgs> directivesToApply,
      Expression appendable,
      JbcSrcPluginContext context) {
    AppendableAndOptions prev = AppendableAndOptions.create(appendable);
    List<Expression> closeables = new ArrayList<>();
    // Apply the directives to the appendable in reverse
    // since we are wrapping the directives around the appendable we need to wrap the underlying
    // appendable with the last directive first. so iterate in reverse order.
    for (DirectiveWithArgs directiveToApply : Lists.reverse(directivesToApply)) {
      AppendableAndOptions curr = directiveToApply.apply(context, prev.appendable());
      if (curr.closeable()) {
        closeables.add(curr.appendable());
      }
      prev = curr;
    }
    // Check if we need to apply a wrapper to make sure close propagates to all the right places
    // this is necessary if there are multiple closeable wrappers.
    if (closeables.isEmpty()) {
      return prev;
    }
    // there is exactly one closeable and it is closeable, we don't need a wrapper
    if (closeables.size() == 1 && prev.appendable() == closeables.get(0)) {
      return prev;
    }
    // there is either more than one closeable, or it is not the first one, so we need a wrapper
    // We need to reverse the list of closeables so that we close them in the correct order.
    // for example, given '|foo|bar'  we will first wrap the delegate with bar and then with foo but
    // we need to close foo first.
    return AppendableAndOptions.createCloseable(
        RUNTIME_PROPAGATE_CLOSE.invoke(
            prev.appendable(), BytecodeUtils.asImmutableList(Lists.reverse(closeables))));
  }

  @AutoValue
  abstract static class DirectiveWithArgs {
    static DirectiveWithArgs create(SoyJbcSrcPrintDirective.Streamable directive) {
      return new AutoValue_PrintDirectives_DirectiveWithArgs(
          directive, ImmutableList.<SoyExpression>of());
    }

    static DirectiveWithArgs create(
        SoyJbcSrcPrintDirective.Streamable directive, List<SoyExpression> arguments) {
      return new AutoValue_PrintDirectives_DirectiveWithArgs(directive, arguments);
    }

    abstract SoyJbcSrcPrintDirective.Streamable directive();

    abstract List<SoyExpression> arguments();

    AppendableAndOptions apply(JbcSrcPluginContext context, Expression appendable) {
      return directive().applyForJbcSrcStreaming(context, appendable, arguments());
    }
  }

  private PrintDirectives() {}
}
