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

package com.google.template.soy.sharedpasses.render;

import com.google.template.soy.data.SoyDataException;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.internal.MsgUtils;
import com.google.template.soy.msgs.restricted.PlaceholderName;
import com.google.template.soy.msgs.restricted.SoyMsgPluralPartForRendering;
import com.google.template.soy.msgs.restricted.SoyMsgRawParts;
import com.google.template.soy.msgs.restricted.SoyMsgSelectPartForRendering;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CaseOrDefaultNode;
import com.google.template.soy.soytree.EscapingMode;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.MsgHtmlTagNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.MsgPlaceholderNode;
import com.google.template.soy.soytree.MsgPluralCaseNode;
import com.google.template.soy.soytree.MsgPluralDefaultNode;
import com.google.template.soy.soytree.MsgPluralNode;
import com.google.template.soy.soytree.MsgSelectCaseNode;
import com.google.template.soy.soytree.MsgSelectDefaultNode;
import com.google.template.soy.soytree.MsgSelectNode;
import com.google.template.soy.soytree.SoyNode;
import com.ibm.icu.util.ULocale;
import javax.annotation.Nullable;

/**
 * Assistant visitor for RenderVisitor to handle messages.
 */
final class RenderVisitorAssistantForMsgs extends AbstractSoyNodeVisitor<Void> {

  /** Master instance of RenderVisitor. */
  private final RenderVisitor master;

  /** The bundle of translated messages, or null to use the messages from the Soy source. */
  private final SoyMsgBundle msgBundle;

  /**
   * @param master The master RenderVisitor instance.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the Soy
   *     source.
   */
  RenderVisitorAssistantForMsgs(RenderVisitor master, SoyMsgBundle msgBundle) {
    this.master = master;
    this.msgBundle = msgBundle;
  }

  @Override
  public Void exec(SoyNode node) {
    throw new AssertionError();
  }

  /** This method must only be called by the master RenderVisitor. */
  void visitForUseByMaster(SoyNode node) {
    visit(node);
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.

  @Override
  protected void visitMsgFallbackGroupNode(MsgFallbackGroupNode node) {

    boolean foundTranslation = false;
    if (msgBundle != null) {
      for (MsgNode msg : node.getChildren()) {
        SoyMsgRawParts translation =
            msgBundle.getMsgPartsForRendering(MsgUtils.computeMsgIdForDualFormat(msg));
        if (translation != null) {
          renderMsgFromTranslation(msg, translation, msgBundle.getLocale());
          foundTranslation = true;
          break;
        }
        SoyMsgRawParts translationByAlternateId =
            msg.getAlternateId().isPresent()
                ? msgBundle.getMsgPartsForRendering(msg.getAlternateId().getAsLong())
                : null;
        if (translationByAlternateId != null) {
          renderMsgFromTranslation(msg, translationByAlternateId, msgBundle.getLocale());
          foundTranslation = true;
          break;
        }
      }
    }
    if (!foundTranslation) {
      renderMsgFromSource(node.getChild(0));
    }
  }

  /**
   * Private helper for visitMsgFallbackGroupNode() to render a message from its translation.
   *
   * <p>TODO(lukes): Refactor this to share the implementation with the JbcSrcRuntime.
   */
  void renderMsgFromTranslation(MsgNode msg, SoyMsgRawParts msgParts, @Nullable ULocale locale) {
    if (msgParts instanceof SoyMsgPluralPartForRendering) {
      new PlrselMsgPartsVisitor(msg, locale).visitPart((SoyMsgPluralPartForRendering) msgParts);

    } else if (msgParts instanceof SoyMsgSelectPartForRendering) {
      new PlrselMsgPartsVisitor(msg, locale).visitPart((SoyMsgSelectPartForRendering) msgParts);

    } else {
      msgParts.forEach(
          msgPart -> {
            if (msgPart instanceof String) {
              String s = (String) msgPart;
              if (msg.getEscapingMode() == EscapingMode.ESCAPE_HTML) {
                // Note that "&" is not replaced because the translation can contain HTML entities.
                s = s.replace("<", "&lt;");
              }
              RenderVisitor.append(master.getCurrOutputBufForUseByAssistants(), s);

            } else if (msgPart instanceof PlaceholderName) {
              String placeholderName = ((PlaceholderName) msgPart).name();
              visit(msg.getRepPlaceholderNode(placeholderName));

            } else {
              throw new AssertionError();
            }
          });
    }
  }

  /** Private helper for visitMsgFallbackGroupNode() to render a message from its source. */
  private void renderMsgFromSource(MsgNode msg) {
    visitChildren(msg);
  }

  @Override
  protected void visitMsgNode(MsgNode node) {
    throw new AssertionError();
  }

  @Override
  protected void visitMsgPluralNode(MsgPluralNode node) {
    ExprRootNode pluralExpr = node.getExpr();
    double pluralValue;
    try {
      pluralValue = master.evalForUseByAssistants(pluralExpr, node).floatValue();
    } catch (SoyDataException e) {
      throw RenderException.createWithSource(
          String.format(
              "Plural expression \"%s\" doesn't evaluate to number.", pluralExpr.toSourceString()),
          e,
          node);
    }

    // Check each case.
    for (CaseOrDefaultNode child : node.getChildren()) {
      if (child instanceof MsgPluralDefaultNode) {
        // This means it didn't match any other case.
        visitChildren(child);
        break;

      } else {
        if (((MsgPluralCaseNode) child).getCaseNumber() == pluralValue) {
          visitChildren(child);
          break;
        }
      }
    }
  }

  @Override
  protected void visitMsgSelectNode(MsgSelectNode node) {
    ExprRootNode selectExpr = node.getExpr();
    String selectValue;
    try {
      selectValue = master.evalForUseByAssistants(selectExpr, node).coerceToString();
    } catch (SoyDataException e) {
      throw RenderException.createWithSource(
          String.format(
              "Select expression \"%s\" doesn't evaluate to string.", selectExpr.toSourceString()),
          e,
          node);
    }

    // Check each case.
    for (CaseOrDefaultNode child : node.getChildren()) {
      if (child instanceof MsgSelectDefaultNode) {
        // This means it didn't match any other case.
        visitChildren(child);

      } else {
        if (((MsgSelectCaseNode) child).getCaseValue().equals(selectValue)) {
          visitChildren(child);
          return;
        }
      }
    }
  }

  @Override
  protected void visitMsgPlaceholderNode(MsgPlaceholderNode node) {
    visitChildren(node);
  }

  @Override
  protected void visitMsgHtmlTagNode(MsgHtmlTagNode node) {
    // Note: We don't default to the fallback implementation because we don't need to add
    // another frame to the environment.
    visitChildren(node);
  }

  // -----------------------------------------------------------------------------------------------
  // Helper class for traversing a translated plural/select message.

  /**
   * Visitor for processing {@code SoyMsgPluralPart} and {@code SoyMsgSelectPart} objects.
   *
   * <p>Visits the parts hierarchy, evaluates each part and appends the result into the parent
   * class' StringBuffer object.
   *
   * <p>In addition to writing to output, this inner class uses the outer class's master's eval()
   * method to evaluate the expressions associated with the nodes.
   */
  private class PlrselMsgPartsVisitor {

    /** The parent message node for the parts dealt here. */
    private final MsgNode msgNode;

    /** The locale for the translated message considered. */
    private final ULocale locale;

    /**
     * Constructor.
     *
     * @param msgNode The parent message node for the parts dealt here.
     * @param locale The locale of the Soy message.
     */
    public PlrselMsgPartsVisitor(MsgNode msgNode, ULocale locale) {
      this.msgNode = msgNode;
      this.locale = locale;
    }

    /**
     * Processes a {@code SoyMsgSelectPart} and appends the rendered output to the {@code
     * StringBuilder} object in {@code RenderVisitor}.
     *
     * @param selectPart The Select part.
     */
    private void visitPart(SoyMsgSelectPartForRendering selectPart) {

      String selectVarName = selectPart.getSelectVarName().name();
      MsgSelectNode repSelectNode = msgNode.getRepSelectNode(selectVarName);

      // Associate the select variable with the value.
      String correctSelectValue;
      ExprRootNode selectExpr = repSelectNode.getExpr();
      try {
        correctSelectValue = master.evalForUseByAssistants(selectExpr, repSelectNode).stringValue();
      } catch (SoyDataException e) {
        throw RenderException.createWithSource(
            String.format(
                "Select expression \"%s\" doesn't evaluate to string.",
                selectExpr.toSourceString()),
            e,
            repSelectNode);
      }

      SoyMsgRawParts caseParts = selectPart.lookupCase(correctSelectValue);
      if (caseParts != null) {
        renderMsgFromTranslation(msgNode, caseParts, locale);
      }
    }

    /**
     * Processes a {@code SoyMsgPluralPart} and appends the rendered output to the {@code
     * StringBuilder} object in {@code RenderVisitor}. It uses the message node cached in this
     * object to get the corresponding Plural node, gets its variable value and offset, and computes
     * the remainder value to be used to render the {@code SoyMsgPluralRemainderPart} later.
     *
     * @param pluralPart The Plural part.
     */
    private void visitPart(SoyMsgPluralPartForRendering pluralPart) {

      MsgPluralNode repPluralNode = msgNode.getRepPluralNode(pluralPart.getPluralVarName().name());
      double correctPluralValue;
      ExprRootNode pluralExpr = repPluralNode.getExpr();
      try {
        correctPluralValue = master.evalForUseByAssistants(pluralExpr, repPluralNode).floatValue();
      } catch (SoyDataException e) {
        throw RenderException.createWithSource(
            String.format(
                "Plural expression \"%s\" doesn't evaluate to number.",
                pluralExpr.toSourceString()),
            e,
            repPluralNode);
      }

      // Handle cases.
      SoyMsgRawParts caseParts = pluralPart.lookupCase(correctPluralValue, locale);

      renderMsgFromTranslation(msgNode, caseParts, locale);
    }

  }

  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.

  @Override
  protected void visitSoyNode(SoyNode node) {
    master.visitForUseByAssistants(node);
  }
}
