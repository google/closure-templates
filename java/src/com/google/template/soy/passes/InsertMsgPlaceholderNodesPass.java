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

import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.MsgHtmlTagNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.MsgPlaceholderNode;
import com.google.template.soy.soytree.MsgPluralNode;
import com.google.template.soy.soytree.MsgSelectNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.MsgBlockNode;
import com.google.template.soy.soytree.SoyNode.MsgPlaceholderInitialNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import java.util.ArrayList;
import java.util.List;

/**
 * A compiler pass to insert {@link MsgPlaceholderNode placeholders} into {@link MsgNode messages}.
 *
 * <p>Also validates correct use of the {@code phname} attribute; {@code phname} attributes can only
 * be set within a <code>{msg ...}...{/msg}</code> context.
 */
final class InsertMsgPlaceholderNodesPass extends CompilerFilePass {
  private static final SoyErrorKind INVALID_PLACEHOLDER =
      SoyErrorKind.of("''phname'' attributes are only valid inside '''{'msg...'' tags");

  private static final SoyErrorKind UNEXPECTED_COMMAND_IN_MSG =
      SoyErrorKind.of(
          "Unexpected soy command in '''{'msg ...'}''' block. Only message placeholder commands "
              + "('{'print, '{'call and html tags) are allowed to be direct children of messages.");
  private final ErrorReporter errorReporter;

  InsertMsgPlaceholderNodesPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    new Visitor(nodeIdGen, errorReporter).exec(file);
  }

  private static final class Visitor extends AbstractSoyNodeVisitor<Void> {
    final List<SoyNode.MsgPlaceholderInitialNode> nodesToReplace = new ArrayList<>();
    final IdGenerator nodeIdGen;
    final ErrorReporter errorReporter;
    boolean inMsgNode = false;

    Visitor(IdGenerator nodeIdGen, ErrorReporter errorReporter) {
      this.errorReporter = errorReporter;
      this.nodeIdGen = nodeIdGen;
    }

    @Override
    protected void visitMsgNode(MsgNode msgNode) {
      msgNode.ensureSubstUnitInfoHasNotBeenAccessed();
      inMsgNode = true;
      visitChildren(msgNode);
      inMsgNode = false;
      for (SoyNode.MsgPlaceholderInitialNode node : nodesToReplace) {
        ParentSoyNode<StandaloneNode> parent = node.getParent();
        if (!(parent instanceof MsgBlockNode)) {
          throw new AssertionError(
              "Expected parent: "
                  + parent
                  + " of "
                  + node
                  + " to be a msgblocknode @ "
                  + parent.getSourceLocation());
        }
        int index = parent.getChildIndex(node);
        parent.removeChild(index);
        parent.addChild(index, new MsgPlaceholderNode(nodeIdGen.genId(), node));
      }
      nodesToReplace.clear();
    }

    // these two are overridden simply to avoid reporting errors in visitSoyNode (they aren't
    // technically MsgBlockNodes, but their children are)
    @Override
    protected void visitMsgPluralNode(MsgPluralNode node) {
      visitChildren(node);
    }

    @Override
    protected void visitMsgSelectNode(MsgSelectNode node) {
      visitChildren(node);
    }

    @Override
    protected void visitPrintNode(PrintNode node) {
      maybeAddAndVisitChildren(node);
      if (!inMsgNode && node.getUserSuppliedPhName() != null) {
        errorReporter.report(node.getSourceLocation(), INVALID_PLACEHOLDER);
      }
    }

    @Override
    protected void visitCallNode(CallNode node) {
      maybeAddAndVisitChildren(node);
      if (!inMsgNode && node.getUserSuppliedPhName() != null) {
        errorReporter.report(node.getSourceLocation(), INVALID_PLACEHOLDER);
      }
    }

    @Override
    protected void visitMsgHtmlTagNode(MsgHtmlTagNode node) {
      maybeAddAndVisitChildren(node);
    }

    @Override
    protected void visitRawTextNode(RawTextNode node) {
      // do nothing
      // overridden so it doesn't default to visitSoyNode and report an error.
    }

    @Override
    protected void visitMsgPlaceholderNode(MsgPlaceholderNode node) {
      throw new IllegalStateException(node.toSourceString());
    }

    private void maybeAddAndVisitChildren(SoyNode.MsgPlaceholderInitialNode node) {
      if (inMsgNode) {
        nodesToReplace.add(node);
      }
      if (node instanceof ParentSoyNode<?>) {
        // inside of a potential placeholder node, we shouldn't find other placeholder nodes.
        boolean oldInMsgNode = inMsgNode;
        inMsgNode = false;
        visitChildren((ParentSoyNode<?>) node);
        inMsgNode = oldInMsgNode;
      }
    }

    @Override
    protected void visitSoyNode(SoyNode node) {
      // If we are in a message node, and this isn't a block node (like {plural} or {select} or a
      // {case} inside of one of those), then report an error. All commands that aren't an error
      // have been overridden above.
      if (inMsgNode && !(node instanceof MsgBlockNode)) {
        errorReporter.report(node.getSourceLocation(), UNEXPECTED_COMMAND_IN_MSG);
        // don't visit children, otherwise every child will report an error also (happens with
        // ifnode and ifcondnode in particular)
        return;
      }
      if (!inMsgNode && node instanceof MsgPlaceholderInitialNode) {
        if (((MsgPlaceholderInitialNode) node).getUserSuppliedPhName() != null) {
          errorReporter.report(node.getSourceLocation(), INVALID_PLACEHOLDER);
        }
      }
      if (node instanceof ParentSoyNode<?>) {
        visitChildren((ParentSoyNode<?>) node);
      }
    }
  }
}
