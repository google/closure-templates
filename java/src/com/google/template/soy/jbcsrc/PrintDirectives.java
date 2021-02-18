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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.template.soy.jbcsrc.ExpressionCompiler.BasicExpressionCompiler;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyJbcSrcPrintDirective;
import com.google.template.soy.jbcsrc.restricted.SoyJbcSrcPrintDirective.Streamable.AppendableAndOptions;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import java.util.ArrayList;
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

  @AutoValue
  abstract static class AppendableAndFlushBuffersDepth {
    static AppendableAndFlushBuffersDepth create(
        AppendableExpression appendableExpression, int flushDepth) {
      return new AutoValue_PrintDirectives_AppendableAndFlushBuffersDepth(
          appendableExpression, flushDepth);
    }

    abstract AppendableExpression appendable();

    abstract int flushBuffersDepth();
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
  static AppendableAndFlushBuffersDepth applyStreamingEscapingDirectives(
      List<SoyPrintDirective> directives,
      AppendableExpression appendable,
      JbcSrcPluginContext context) {
    checkArgument(!directives.isEmpty());
    List<StreamingDirectiveWithArgs> directivesToApply = new ArrayList<>();
    for (SoyPrintDirective directive : directives) {
      directivesToApply.add(
          StreamingDirectiveWithArgs.create((SoyJbcSrcPrintDirective.Streamable) directive));
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
  static AppendableAndFlushBuffersDepth applyStreamingPrintDirectives(
      List<PrintDirectiveNode> directives,
      AppendableExpression appendable,
      BasicExpressionCompiler basic,
      JbcSrcPluginContext renderContext) {
    checkArgument(!directives.isEmpty());
    List<StreamingDirectiveWithArgs> directivesToApply = new ArrayList<>();
    for (PrintDirectiveNode directive : directives) {
      directivesToApply.add(
          StreamingDirectiveWithArgs.create(
              (SoyJbcSrcPrintDirective.Streamable) directive.getPrintDirective(),
              basic.compileToList(directive.getArgs())));
    }
    return applyStreamingPrintDirectivesTo(directivesToApply, appendable, renderContext);
  }

  private static AppendableAndFlushBuffersDepth applyStreamingPrintDirectivesTo(
      List<StreamingDirectiveWithArgs> directivesToApply,
      AppendableExpression appendable,
      JbcSrcPluginContext context) {

    Expression currAppendable = appendable;

    // Apply the directives to the appendable in reverse
    // since we are wrapping the directives around the appendable we need to wrap the underlying
    // appendable with the last directive first. so iterate in reverse order.

    // how deep in the layers of wrappers the first directive is that requires a flushBuffers call.
    int flushBuffersDepth = -1;
    for (StreamingDirectiveWithArgs directiveToApply : Lists.reverse(directivesToApply)) {
      AppendableAndOptions next = directiveToApply.apply(context, currAppendable);
      currAppendable = next.appendable();
      if (next.closeable() || flushBuffersDepth >= 0) {
        flushBuffersDepth++;
      }
    }
    // mark the appendable as non-nullable.  If any of the wrappers are ever null it is a logical
    // error and we should fail with an NPE.
    return AppendableAndFlushBuffersDepth.create(
        AppendableExpression.forExpression(currAppendable.asNonNullable()), flushBuffersDepth);
  }

  @AutoValue
  abstract static class StreamingDirectiveWithArgs {
    static StreamingDirectiveWithArgs create(SoyJbcSrcPrintDirective.Streamable directive) {
      return new AutoValue_PrintDirectives_StreamingDirectiveWithArgs(
          directive, ImmutableList.of());
    }

    static StreamingDirectiveWithArgs create(
        SoyJbcSrcPrintDirective.Streamable directive, List<SoyExpression> arguments) {
      return new AutoValue_PrintDirectives_StreamingDirectiveWithArgs(
          directive, ImmutableList.copyOf(arguments));
    }

    abstract SoyJbcSrcPrintDirective.Streamable directive();

    abstract ImmutableList<SoyExpression> arguments();

    AppendableAndOptions apply(JbcSrcPluginContext context, Expression appendable) {
      return directive().applyForJbcSrcStreaming(context, appendable, arguments());
    }
  }

  private PrintDirectives() {}
}
