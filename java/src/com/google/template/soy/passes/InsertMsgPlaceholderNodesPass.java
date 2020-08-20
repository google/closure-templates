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

import static com.google.template.soy.soytree.MessagePlaceholder.PHEX_ATTR;
import static com.google.template.soy.soytree.MessagePlaceholder.PHNAME_ATTR;

import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.HtmlAttributeValueNode;
import com.google.template.soy.soytree.HtmlCloseTagNode;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.HtmlTagNode;
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
import com.google.template.soy.soytree.VeLogNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A compiler pass to insert {@link MsgPlaceholderNode placeholders} into {@link MsgNode messages}.
 *
 * <p>Also validates correct use of the {@code phname} and {@code phex} attributes; these attributes
 * can only be set within a <code>{msg ...}...{/msg}</code> context.
 */
final class InsertMsgPlaceholderNodesPass implements CompilerFilePass {
  private static final SoyErrorKind INVALID_PLACEHOLDER =
      SoyErrorKind.of(
          "''{0}'' attributes are only valid on placeholders inside of '''{'msg...'' tags.{1}",
          StyleAllowance.NO_PUNCTUATION);

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
    final List<SoyNode> nodesToReplace = new ArrayList<>();
    final IdGenerator nodeIdGen;
    final ErrorReporter errorReporter;
    boolean isValidMsgPlaceholderPosition = false;

    Visitor(IdGenerator nodeIdGen, ErrorReporter errorReporter) {
      this.errorReporter = errorReporter;
      this.nodeIdGen = nodeIdGen;
    }

    @Override
    protected void visitMsgNode(MsgNode msgNode) {
      msgNode.ensureSubstUnitInfoHasNotBeenAccessed();
      isValidMsgPlaceholderPosition = true;
      visitChildren(msgNode);
      isValidMsgPlaceholderPosition = false;
      for (SoyNode node : nodesToReplace) {
        ParentSoyNode<?> parent = node.getParent();
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
        MsgPlaceholderInitialNode newNode;
        if (node instanceof HtmlTagNode) {
          VeLogNode veLogParent = null;
          // If it is the open tag of a VeLogNode, then make sure the placeholder knows about it.
          // NOTE: we don't tell the close tags that they are part of a velog because it isn't
          // necessary, close tags are all the same, even when it comes to velogging. (all we need
          // to do is call exitLoggableElement()).
          if (parent instanceof VeLogNode && index == 0) {
            veLogParent = (VeLogNode) parent;
          }
          newNode =
              MsgHtmlTagNode.fromNode(
                  nodeIdGen.genId(), (HtmlTagNode) node, veLogParent, errorReporter);
        } else {
          // print, and call nodes don't get additional wrappers.
          newNode = (MsgPlaceholderInitialNode) node;
        }
        ((MsgBlockNode) parent).addChild(index, new MsgPlaceholderNode(nodeIdGen.genId(), newNode));
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
      maybeAddPlaceholderAndVisitChildren(node);
      checkPlaceholderNode(node);
    }

    @Override
    protected void visitCallNode(CallNode node) {
      maybeAddPlaceholderAndVisitChildren(node);
      checkPlaceholderNode(node);
    }

    @Override
    protected void visitVeLogNode(VeLogNode node) {
      // visit children directly since they are still 'in' the message
      visitChildren(node);
    }

    @Override
    protected void visitMsgHtmlTagNode(MsgHtmlTagNode node) {
      throw new AssertionError("Unexpected node: " + node.toSourceString());
    }

    @Override
    protected void visitHtmlOpenTagNode(HtmlOpenTagNode node) {
      maybeAddPlaceholderAndVisitChildren(node);
      // NOTE: it is OK for these nodes to have phname attrs outside of msg blocks for backwards
      // compatibility
    }

    @Override
    protected void visitHtmlCloseTagNode(HtmlCloseTagNode node) {
      maybeAddPlaceholderAndVisitChildren(node);

      if (!isValidMsgPlaceholderPosition) {
        // phname and phex are the only allowed close tag attribute, and even then it is only
        // allowed inside of messages
        for (String name : Arrays.asList("phname", "phex")) {
          HtmlAttributeNode attr = node.getDirectAttributeNamed(name);
          if (attr != null) {
            errorReporter.report(attr.getSourceLocation(), INVALID_PLACEHOLDER, name, "");
          }
        }
      }
    }

    @Override
    protected void visitRawTextNode(RawTextNode node) {
      // do nothing
      // overridden so it doesn't default to visitSoyNode and report an error.
    }

    @Override
    protected void visitHtmlAttributeNode(HtmlAttributeNode node) {
      // TODO(lukes): This will happen for cases like: <a {msg desc="..."}class=var{/msg}>  which
      // is actually reported as an error later on in the autoescaper, consider if it makes sense to
      // move that logic here.  For now we just traverse the node to prevent reporting an error
      visitChildren(node);
    }

    @Override
    protected void visitHtmlAttributeValueNode(HtmlAttributeValueNode node) {
      // TODO(lukes): This will happen for cases like: <a title={msg desc="..."}Hello{/msg}>  which
      // is actually reported as an error later on in the autoescaper, consider if it makes sense to
      // move that logic here.  For now we just traverse the node to prevent reporting an error
      // overridden so it doesn't default to visitSoyNode and report an error.
      visitChildren(node);
    }

    @Override
    protected void visitMsgPlaceholderNode(MsgPlaceholderNode node) {
      throw new AssertionError("Unexpected node: " + node.toSourceString());
    }

    private void maybeAddPlaceholderAndVisitChildren(SoyNode node) {
      if (isValidMsgPlaceholderPosition) {
        nodesToReplace.add(node);
      }
      if (node instanceof ParentSoyNode<?>) {
        // inside of a potential placeholder node, we shouldn't find other placeholder nodes.
        boolean oldIsValidMsgPlaceholderPosition = isValidMsgPlaceholderPosition;
        isValidMsgPlaceholderPosition = false;
        visitChildren((ParentSoyNode<?>) node);
        isValidMsgPlaceholderPosition = oldIsValidMsgPlaceholderPosition;
      }
    }

    @Override
    protected void visitSoyNode(SoyNode node) {
      // If we are in a message node, and this isn't a block node (like {plural} or {select} or a
      // {case} inside of one of those), then report an error. All commands that aren't an error
      // have been overridden above.
      if (isValidMsgPlaceholderPosition && !(node instanceof MsgBlockNode)) {
        errorReporter.report(node.getSourceLocation(), UNEXPECTED_COMMAND_IN_MSG);
        // don't visit children, otherwise every child will report an error also (happens with
        // ifnode and ifcondnode in particular)
        return;
      }
      if (node instanceof MsgPlaceholderInitialNode) {
        checkPlaceholderNode((MsgPlaceholderInitialNode) node);
      }
      if (node instanceof ParentSoyNode<?>) {
        visitChildren((ParentSoyNode<?>) node);
      }
    }

    private void checkPlaceholderNode(MsgPlaceholderInitialNode node) {
      if (isValidMsgPlaceholderPosition) {
        return;
      }
      boolean hasUserSuppliedName = node.getPlaceholder().userSuppliedName().isPresent();
      boolean hasExample = node.getPlaceholder().example().isPresent();
      if (hasUserSuppliedName || hasExample) {
        MsgNode msg = node.getNearestAncestor(MsgNode.class);
        String extra = "";
        if (msg != null) {
          // this means we are in a message, just not in a placeholder position, this is likely
          // because we are the child of another placeholder node, most likely an html tag
          // See b/135952248
          SoyNode current = node.getParent();
          while (current != msg && !(current instanceof HtmlTagNode)) {
            current = current.getParent();
          }
          if (current != msg) {
            extra = " Did you mean to put this attribute on the surrounding html tag?";
          }
        }
        if (hasUserSuppliedName) {
          errorReporter.report(node.getSourceLocation(), INVALID_PLACEHOLDER, PHNAME_ATTR, extra);
        }
        if (hasExample) {
          errorReporter.report(node.getSourceLocation(), INVALID_PLACEHOLDER, PHEX_ATTR, extra);
        }
      }
    }
  }
}
