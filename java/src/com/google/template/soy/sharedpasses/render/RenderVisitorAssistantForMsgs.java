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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.SoyDataException;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.internal.MsgUtils;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
import com.google.template.soy.msgs.restricted.SoyMsgPlaceholderPart;
import com.google.template.soy.msgs.restricted.SoyMsgPluralPart;
import com.google.template.soy.msgs.restricted.SoyMsgPluralRemainderPart;
import com.google.template.soy.msgs.restricted.SoyMsgRawTextPart;
import com.google.template.soy.msgs.restricted.SoyMsgSelectPart;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CaseOrDefaultNode;
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
import java.util.List;
import javax.annotation.Nullable;

/**
 * Assistant visitor for RenderVisitor to handle messages.
 *
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
        ImmutableList<SoyMsgPart> translation =
            msgBundle.getMsgParts(MsgUtils.computeMsgIdForDualFormat(msg));
        if (!translation.isEmpty()) {
          renderMsgFromTranslation(msg, translation, msgBundle.getLocale());
          foundTranslation = true;
          break;
        }
      }
    }
    if (!foundTranslation) {
      renderMsgFromSource(node.getChild(0));
    }
  }

  /** Private helper for visitMsgFallbackGroupNode() to render a message from its translation. */
  private void renderMsgFromTranslation(
      MsgNode msg, ImmutableList<SoyMsgPart> msgParts, @Nullable ULocale locale) {
    SoyMsgPart firstPart = msgParts.get(0);

    if (firstPart instanceof SoyMsgPluralPart) {
      new PlrselMsgPartsVisitor(msg, locale).visitPart((SoyMsgPluralPart) firstPart);

    } else if (firstPart instanceof SoyMsgSelectPart) {
      new PlrselMsgPartsVisitor(msg, locale).visitPart((SoyMsgSelectPart) firstPart);

    } else {
      for (SoyMsgPart msgPart : msgParts) {

        if (msgPart instanceof SoyMsgRawTextPart) {
          RenderVisitor.append(
              master.getCurrOutputBufForUseByAssistants(),
              ((SoyMsgRawTextPart) msgPart).getRawText());

        } else if (msgPart instanceof SoyMsgPlaceholderPart) {
          String placeholderName = ((SoyMsgPlaceholderPart) msgPart).getPlaceholderName();
          visit(msg.getRepPlaceholderNode(placeholderName));

        } else {
          throw new AssertionError();
        }
      }
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
      pluralValue = master.evalForUseByAssistants(pluralExpr, node).numberValue();
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
      selectValue = master.evalForUseByAssistants(selectExpr, node).stringValue();
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
    private void visitPart(SoyMsgSelectPart selectPart) {

      String selectVarName = selectPart.getSelectVarName();
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

      List<SoyMsgPart> caseParts = selectPart.lookupCase(correctSelectValue);

      if (caseParts != null) {

        for (SoyMsgPart casePart : caseParts) {

          if (casePart instanceof SoyMsgSelectPart) {
            visitPart((SoyMsgSelectPart) casePart);

          } else if (casePart instanceof SoyMsgPluralPart) {
            visitPart((SoyMsgPluralPart) casePart);

          } else if (casePart instanceof SoyMsgPlaceholderPart) {
            visitPart((SoyMsgPlaceholderPart) casePart);

          } else if (casePart instanceof SoyMsgRawTextPart) {
            appendRawTextPart((SoyMsgRawTextPart) casePart);

          } else {
            throw RenderException.create(
                    "Unsupported part of type "
                        + casePart.getClass().getName()
                        + " under a select case.")
                .addStackTraceElement(repSelectNode);
          }
        }
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
    private void visitPart(SoyMsgPluralPart pluralPart) {

      MsgPluralNode repPluralNode = msgNode.getRepPluralNode(pluralPart.getPluralVarName());
      double correctPluralValue;
      ExprRootNode pluralExpr = repPluralNode.getExpr();
      try {
        correctPluralValue = master.evalForUseByAssistants(pluralExpr, repPluralNode).numberValue();
      } catch (SoyDataException e) {
        throw RenderException.createWithSource(
            String.format(
                "Plural expression \"%s\" doesn't evaluate to number.",
                pluralExpr.toSourceString()),
            e,
            repPluralNode);
      }

      // Handle cases.
      List<SoyMsgPart> caseParts = pluralPart.lookupCase((int) correctPluralValue, locale);

      for (SoyMsgPart casePart : caseParts) {

        if (casePart instanceof SoyMsgPlaceholderPart) {
          visitPart((SoyMsgPlaceholderPart) casePart);

        } else if (casePart instanceof SoyMsgRawTextPart) {
          appendRawTextPart((SoyMsgRawTextPart) casePart);

        } else if (casePart instanceof SoyMsgPluralRemainderPart) {
          appendPluralRemainder(correctPluralValue - pluralPart.getOffset());

        } else {
          // Plural parts will not have nested plural/select parts.  So, this is an error.
          throw RenderException.create(
                  "Unsupported part of type "
                      + casePart.getClass().getName()
                      + " under a plural case.")
              .addStackTraceElement(repPluralNode);
        }
      }
    }

    /**
     * Processes a {@code SoyMsgPluralRemainderPart} and appends the rendered output to the {@code
     * StringBuilder} object in {@code RenderVisitor}. Since this is precomputed when visiting the
     * {@code SoyMsgPluralPart} object, it is directly used here.
     */
    private void appendPluralRemainder(double currentPluralRemainderValue) {
      RenderVisitor.append(
          master.getCurrOutputBufForUseByAssistants(), String.valueOf(currentPluralRemainderValue));
    }

    /**
     * Process a {@code SoyMsgPlaceholderPart} and updates the internal data structures.
     *
     * @param msgPlaceholderPart the Placeholder part.
     */
    private void visitPart(SoyMsgPlaceholderPart msgPlaceholderPart) {

      // Since the content of a placeholder is not altered by translation, just render
      // the corresponding placeholder node.
      visit(msgNode.getRepPlaceholderNode(msgPlaceholderPart.getPlaceholderName()));
    }

    /**
     * Processes a {@code SoyMsgRawTextPart} and appends the contained text to the {@code
     * StringBuilder} object in {@code RenderVisitor}.
     *
     * @param rawTextPart The raw text part.
     */
    private void appendRawTextPart(SoyMsgRawTextPart rawTextPart) {
      RenderVisitor.append(master.getCurrOutputBufForUseByAssistants(), rawTextPart.getRawText());
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.

  @Override
  protected void visitSoyNode(SoyNode node) {
    master.visitForUseByAssistants(node);
  }
}
