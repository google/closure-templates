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

package com.google.template.soy.passes;

import com.google.common.io.BaseEncoding;
import com.google.common.primitives.Longs;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.QuoteStyle;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.OperatorNodes.ConditionalOpNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.msgs.internal.MsgUtils;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.LocalVarNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.defn.LocalVar;

/**
 * Validates uses of the {@code msgId} function.
 *
 * <p>Must run after the ResolveNamesPass and the CheckNonEmptyMsgNodesPass since we depend on
 * finding local variable definitions and empty message nodes don't have valid ids. Should run
 * before ResolveExpressionTypesPass so that we don't need to worry about assigning types here.
 */
final class MsgIdFunctionPass extends CompilerFilePass {
  private static final SoyErrorKind MSG_VARIABLE_NOT_IN_SCOPE =
      SoyErrorKind.of(
          "Function ''msgId'' must take a let variable containing a single msg "
              + "as its only argument.{0}",
          StyleAllowance.NO_PUNCTUATION);

  private final ErrorReporter errorReporter;

  MsgIdFunctionPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    outer:
    for (FunctionNode fn : SoyTreeUtils.getAllNodesOfType(file, FunctionNode.class)) {
      if (fn.getSoyFunction() == BuiltinFunction.MSG_ID) {
        if (fn.numChildren() != 1) {
          // if it isn't == 1, then an error has already been reported
          continue;
        }
        ExprNode msgVariable = fn.getChild(0);
        if (!(msgVariable instanceof VarRefNode)) {
          badFunctionCall(fn, " It is not a variable.");
          continue;
        }
        VarDefn defn = ((VarRefNode) msgVariable).getDefnDecl();
        if (!(defn instanceof LocalVar)) {
          badFunctionCall(fn, " It is not a let variable.");
          continue;
        }
        LocalVarNode declaringNode = ((LocalVar) defn).declaringNode();
        if (!(declaringNode instanceof LetContentNode)) {
          badFunctionCall(fn, " It is not a let.");
          continue;
        }
        LetContentNode letNode = (LetContentNode) declaringNode;
        MsgFallbackGroupNode fallbackGroupNode = null;
        for (SoyNode child : letNode.getChildren()) {
          if (child instanceof RawTextNode && ((RawTextNode) child).getRawText().isEmpty()) {
            continue;
          } else if (child instanceof MsgFallbackGroupNode) {
            if (fallbackGroupNode == null) {
              fallbackGroupNode = (MsgFallbackGroupNode) child;
            } else {
              badFunctionCall(fn, " There is more than one msg.");
              continue outer;
            }
          } else {
            badFunctionCall(fn, " There is a non-msg child of the let.");
            continue outer;
          }
        }
        if (fallbackGroupNode == null) {
          badFunctionCall(fn, " There was no msg in the referenced let.");
          continue;
        }
        handleMsgIdCall(fn, fallbackGroupNode);
      }
    }
  }

  private void badFunctionCall(FunctionNode fn, String explanation) {
    errorReporter.report(
        fn.getChild(0).getSourceLocation(), MSG_VARIABLE_NOT_IN_SCOPE, explanation);
    fn.getParent()
        .replaceChild(fn, new StringNode("error", QuoteStyle.SINGLE, fn.getSourceLocation()));
  }

  /**
   * Rewrites calls to msgId($msgVar) to either a static constant message id or a conditional if
   * there is a fallback.
   */
  private void handleMsgIdCall(FunctionNode fn, MsgFallbackGroupNode msgNode) {
    ExprNode replacement;
    long primaryMsgId = MsgUtils.computeMsgIdForDualFormat(msgNode.getChild(0));
    if (msgNode.numChildren() == 1) {
      // easy peasy
      replacement = createMsgIdNode(primaryMsgId, fn.getSourceLocation());
    } else {
      long fallbackMsgId = MsgUtils.computeMsgIdForDualFormat(msgNode.getChild(1));
      ConditionalOpNode condOpNode = new ConditionalOpNode(fn.getSourceLocation());
      FunctionNode isPrimaryMsgInUse =
          new FunctionNode(BuiltinFunction.IS_PRIMARY_MSG_IN_USE, fn.getSourceLocation());
      // We add the varRef, and the 2 message ids to the funcitonnode as arguments so they are
      // trivial to access in the backends.  This is a little hacky however since we never generate
      // code for these things.
      // We could formalize the hack by providing a way to stash arbitrary data in the FunctionNode
      // and then just pack this up in a non-AST datastructure.
      isPrimaryMsgInUse.addChild(fn.getChild(0));
      isPrimaryMsgInUse.addChild(new IntegerNode(primaryMsgId, fn.getSourceLocation()));
      isPrimaryMsgInUse.addChild(new IntegerNode(fallbackMsgId, fn.getSourceLocation()));
      condOpNode.addChild(isPrimaryMsgInUse);
      condOpNode.addChild(createMsgIdNode(primaryMsgId, fn.getSourceLocation()));
      condOpNode.addChild(createMsgIdNode(fallbackMsgId, fn.getSourceLocation()));
      replacement = condOpNode;
    }
    fn.getParent().replaceChild(fn, replacement);
  }

  private StringNode createMsgIdNode(long id, SourceLocation location) {
    return new StringNode(formatMsgId(id), QuoteStyle.SINGLE, location);
  }

  // encodes the id as web safe base64 string
  private static String formatMsgId(long id) {
    return BaseEncoding.base64Url().encode(Longs.toByteArray(id));
  }
}
