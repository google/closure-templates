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

import com.google.common.collect.Maps;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.SoyDataException;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.internal.base.Pair;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.internal.MsgUtils;
import com.google.template.soy.msgs.restricted.SoyMsg;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
import com.google.template.soy.msgs.restricted.SoyMsgPlaceholderPart;
import com.google.template.soy.msgs.restricted.SoyMsgPluralCaseSpec;
import com.google.template.soy.msgs.restricted.SoyMsgPluralPart;
import com.google.template.soy.msgs.restricted.SoyMsgPluralRemainderPart;
import com.google.template.soy.msgs.restricted.SoyMsgRawTextPart;
import com.google.template.soy.msgs.restricted.SoyMsgSelectPart;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CaseOrDefaultNode;
import com.google.template.soy.soytree.MsgHtmlTagNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.MsgPlaceholderNode;
import com.google.template.soy.soytree.MsgPluralCaseNode;
import com.google.template.soy.soytree.MsgPluralDefaultNode;
import com.google.template.soy.soytree.MsgPluralNode;
import com.google.template.soy.soytree.MsgPluralRemainderNode;
import com.google.template.soy.soytree.MsgSelectCaseNode;
import com.google.template.soy.soytree.MsgSelectDefaultNode;
import com.google.template.soy.soytree.MsgSelectNode;
import com.google.template.soy.soytree.SoyNode;

import com.ibm.icu.text.PluralRules;
import com.ibm.icu.util.ULocale;

import java.util.Deque;
import java.util.List;
import java.util.Map;


/**
 * Assistant visitor for RenderVisitor to handle messages.
 *
 * @author Kai Huang
 */
class RenderVisitorAssistantForMsgs extends AbstractSoyNodeVisitor<Void> {


  /** Master instance of RenderVisitor. */
  private final RenderVisitor master;

  /** The current environment. */
  private final Deque<Map<String, SoyData>> env;

  /** The bundle of translated messages, or null to use the messages from the Soy source. */
  private final SoyMsgBundle msgBundle;

  /** Holds the value of the remainder for the current enclosing plural node. */
  private int currPluralRemainderValue;


  /**
   * @param master The master RenderVisitor instance.
   * @param env The current environment.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the Soy
   *     source.
   */
  RenderVisitorAssistantForMsgs(
      RenderVisitor master, Deque<Map<String, SoyData>> env, SoyMsgBundle msgBundle) {
    this.master = master;
    this.env = env;
    this.msgBundle = msgBundle;
    this.currPluralRemainderValue = -1;
  }


  @Override public Void exec(SoyNode node) {
    throw new AssertionError();
  }


  /**
   * This method must only be called by the master RenderVisitor.
   */
  void visitForUseByMaster(SoyNode node) {
    visit(node);
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.


  @Override protected void visitMsgNode(MsgNode node) {

    boolean doAddEnvFrame = node.needsEnvFrameDuringInterp() != Boolean.FALSE /*true or unknown*/;
    if (doAddEnvFrame) {
      env.push(Maps.<String, SoyData>newHashMap());
    }

    SoyMsg soyMsg;
    if (msgBundle != null) {
      long msgId = MsgUtils.computeMsgIdForDualFormat(node);
      soyMsg = msgBundle.getMsg(msgId);
    } else {
      soyMsg = null;
    }

    if (soyMsg != null) {
      // Case 1: Localized message is provided by the msgBundle.

      List<SoyMsgPart> msgParts = soyMsg.getParts();

      if (msgParts.size() > 0) {
        SoyMsgPart firstPart = msgParts.get(0);

        if (firstPart instanceof SoyMsgPluralPart) {
          new PlrselMsgPartsVisitor(node, new ULocale(soyMsg.getLocaleString()))
              .visitPart((SoyMsgPluralPart) firstPart);

        } else if (firstPart instanceof SoyMsgSelectPart) {
          new PlrselMsgPartsVisitor(node, new ULocale(soyMsg.getLocaleString()))
              .visitPart((SoyMsgSelectPart) firstPart);

        } else {
          for (SoyMsgPart msgPart : msgParts) {

            if (msgPart instanceof SoyMsgRawTextPart) {
              RenderVisitor.append(master.getCurrOutputBufForUseByAssistants(),
                  ((SoyMsgRawTextPart) msgPart).getRawText());

            } else if (msgPart instanceof SoyMsgPlaceholderPart) {
              String placeholderName = ((SoyMsgPlaceholderPart) msgPart).getPlaceholderName();
              visit(node.getRepPlaceholderNode(placeholderName));

            } else {
              throw new AssertionError();
            }
          }

        }
      }

    } else {
      // Case 2: No msgBundle or message not found. Just use the message from the Soy source.
      visitChildren(node);
    }

    if (doAddEnvFrame) {
      env.pop();
    }
  }


  @Override protected void visitMsgPluralNode(MsgPluralNode node) {
    ExprRootNode<?> pluralExpr = node.getExpr();
    int pluralValue;
    try {
      pluralValue = master.evalForUseByAssistants(pluralExpr).integerValue();
    } catch (SoyDataException e) {
      throw new RenderException(
            String.format("Plural expression \"%s\" doesn't evaluate to integer.",
                pluralExpr.toSourceString()));
    }

    currPluralRemainderValue = pluralValue - node.getOffset();

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

    currPluralRemainderValue = -1;
  }


  @Override protected void visitMsgPluralRemainderNode(MsgPluralRemainderNode node) {
    RenderVisitor.append(master.getCurrOutputBufForUseByAssistants(),
        Integer.toString(currPluralRemainderValue));
  }


  @Override protected void visitMsgSelectNode(MsgSelectNode node) {
    ExprRootNode<?> selectExpr = node.getExpr();
    String selectValue;
    try {
      selectValue = master.evalForUseByAssistants(selectExpr).stringValue();
    } catch (SoyDataException e) {
      throw new RenderException(
          String.format("Select expression \"%s\" doesn't evaluate to string.",
                        selectExpr.toSourceString()));
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


  @Override protected void visitMsgPlaceholderNode(MsgPlaceholderNode node) {
    visitChildren(node);
  }


  @Override protected void visitMsgHtmlTagNode(MsgHtmlTagNode node) {
    // Note: We don't default to the fallback implementation because we don't need to add
    // another frame to the environment.
    visitChildren(node);
  }


  // -----------------------------------------------------------------------------------------------
  // Helper class for traversing a translated plural/select message.


  /**
   * Visitor for processing {@code SoyMsgPluralPart} and {@code SoyMsgSelectPart} objects.
   *
   * Visits the parts hierarchy, evaluates each part and appends the result into the
   * parent class' StringBuffer object.
   *
   * In addition to writing to output, this inner class uses the outer class's master's eval()
   * method to evaluate the expressions associated with the nodes.
   */
  private class PlrselMsgPartsVisitor {


    /** The parent message node for the parts dealt here. */
    private final MsgNode msgNode;

    /** The locale for the translated message considered. */
    private final ULocale locale;

    /** Holds the value of the remainder for the current enclosing plural part. */
    private int currentPluralRemainderValue;


    /**
     * Constructor.
     * @param msgNode The parent message node for the parts dealt here.
     * @param locale The locale of the Soy message.
     */
    public PlrselMsgPartsVisitor(MsgNode msgNode, ULocale locale) {
      this.msgNode = msgNode;
      this.locale = locale;
    }


    /**
     * Processes a {@code SoyMsgSelectPart} and appends the rendered output to
     * the {@code StringBuilder} object in {@code RenderVisitor}.
     * @param selectPart The Select part.
     */
    private void visitPart(SoyMsgSelectPart selectPart) {

      String selectVarName = selectPart.getSelectVarName();
      MsgSelectNode repSelectNode = msgNode.getRepSelectNode(selectVarName);

      // Associate the select variable with the value.
      String correctSelectValue;
      ExprRootNode<?> selectExpr = repSelectNode.getExpr();
      try {
        correctSelectValue = master.evalForUseByAssistants(selectExpr).stringValue();
      } catch (SoyDataException e) {
        throw new RenderException(
            String.format("Select expression \"%s\" doesn't evaluate to string.",
                selectExpr.toSourceString()));
      }

      List<SoyMsgPart> caseParts = null;
      List<SoyMsgPart> defaultParts = null;

      // Handle cases.
      for (Pair<String, List<SoyMsgPart>> case0 : selectPart.getCases()) {
        if (case0.first == null) {
          defaultParts = case0.second;
        } else if (case0.first.equals(correctSelectValue)) {
          caseParts = case0.second;
          break;
        }
      }

      if (caseParts == null) {
        caseParts = defaultParts;
      }

      if (caseParts != null) {

        for (SoyMsgPart casePart : caseParts) {

          if (casePart instanceof SoyMsgSelectPart) {
            visitPart((SoyMsgSelectPart) casePart);

          } else if (casePart instanceof SoyMsgPluralPart) {
            visitPart((SoyMsgPluralPart) casePart);

          } else if (casePart instanceof SoyMsgPlaceholderPart) {
            visitPart((SoyMsgPlaceholderPart) casePart);

          } else if (casePart instanceof SoyMsgRawTextPart) {
            visitPart((SoyMsgRawTextPart) casePart);

          } else {
            throw new RenderException("Unsupported part of type " + casePart.getClass().getName() +
                " under a select case.");

          }
        }
      }
    }


    /**
     * Processes a {@code SoyMsgPluralPart} and appends the rendered output to
     * the {@code StringBuilder} object in {@code RenderVisitor}.
     * It uses the message node cached in this object to get the corresponding
     * Plural node, gets its variable value and offset, and computes the remainder value to
     * be used to render the {@code SoyMsgPluralRemainderPart} later.
     * @param pluralPart The Plural part.
     */
    private void visitPart(SoyMsgPluralPart pluralPart) {

      MsgPluralNode repPluralNode = msgNode.getRepPluralNode(pluralPart.getPluralVarName());
      int correctPluralValue;
      ExprRootNode<?> pluralExpr = repPluralNode.getExpr();
      try {
        correctPluralValue = master.evalForUseByAssistants(pluralExpr).integerValue();
      } catch (SoyDataException e) {
        throw new RenderException(
            String.format("Plural expression \"%s\" doesn't evaluate to integer.",
                pluralExpr.toSourceString()));
      }

      currentPluralRemainderValue = correctPluralValue - repPluralNode.getOffset();

      // Handle cases.
      List<SoyMsgPart> caseParts = null;

      // Check whether the plural value matches any explicit numeric value.
      boolean hasNonExplicitCases = false;
      List<SoyMsgPart> otherCaseParts = null;
      for (Pair<SoyMsgPluralCaseSpec, List<SoyMsgPart>> case0 : pluralPart.getCases()) {

        SoyMsgPluralCaseSpec pluralCaseSpec = case0.first;
        SoyMsgPluralCaseSpec.Type caseType = pluralCaseSpec.getType();
        if (caseType == SoyMsgPluralCaseSpec.Type.EXPLICIT) {
          if (pluralCaseSpec.getExplicitValue() == correctPluralValue) {
            caseParts = case0.second;
            break;
          }

        } else if (caseType == SoyMsgPluralCaseSpec.Type.OTHER) {
          otherCaseParts = case0.second;

        } else {
          hasNonExplicitCases = true;

        }
      }

      if (caseParts == null && !hasNonExplicitCases) {
        caseParts = otherCaseParts;
      }

      if (caseParts == null) {
        // Didn't match any numeric value.  Check which plural rule it matches.
        String pluralKeyword = PluralRules.forLocale(locale).select(currentPluralRemainderValue);
        SoyMsgPluralCaseSpec.Type correctCaseType =
            new SoyMsgPluralCaseSpec(pluralKeyword).getType();


        // Iterate the cases once again for non-numeric keywords.
        for (Pair<SoyMsgPluralCaseSpec, List<SoyMsgPart>> case0 : pluralPart.getCases()) {

          if (case0.first.getType() == correctCaseType) {
            caseParts = case0.second;
            break;
          }
        }
      }

      if (caseParts != null) {
        for (SoyMsgPart casePart : caseParts) {

          if (casePart instanceof SoyMsgPlaceholderPart) {
            visitPart((SoyMsgPlaceholderPart) casePart);

          } else if (casePart instanceof SoyMsgRawTextPart) {
            visitPart((SoyMsgRawTextPart) casePart);

          } else if (casePart instanceof SoyMsgPluralRemainderPart) {
            visitPart((SoyMsgPluralRemainderPart) casePart);

          } else {
            // Plural parts will not have nested plural/select parts.  So, this is an error.
            throw new RenderException("Unsupported part of type " + casePart.getClass().getName() +
                " under a plural case.");

          }
        }
      }
    }


    /**
     * Processes a {@code SoyMsgPluralRemainderPart} and appends the rendered output to
     * the {@code StringBuilder} object in {@code RenderVisitor}.  Since this is precomputed
     * when visiting the {@code SoyMsgPluralPart} object, it is directly used here.
     * @param remainderPart The {@code SoyMsgPluralRemainderPart} object.
     */
    @SuppressWarnings("UnusedDeclaration")  // for IntelliJ
    private void visitPart(SoyMsgPluralRemainderPart remainderPart) {
      RenderVisitor.append(master.getCurrOutputBufForUseByAssistants(),
          Integer.toString(currentPluralRemainderValue));
    }


    /**
     * Process a {@code SoyMsgPlaceholderPart} and updates the internal data structures.
     * @param msgPlaceholderPart the Placeholder part.
     */
    private void visitPart(SoyMsgPlaceholderPart msgPlaceholderPart) {

      // Since the content of a placeholder is not altered by translation, just render
      // the corresponding placeholder node.
      visit(msgNode.getRepPlaceholderNode(msgPlaceholderPart.getPlaceholderName()));
    }


    /**
     * Processes a {@code SoyMsgRawTextPart} and appends the contained text to
     * the {@code StringBuilder} object in {@code RenderVisitor}.
     * @param rawTextPart The raw text part.
     */
    private void visitPart(SoyMsgRawTextPart rawTextPart) {
      RenderVisitor.append(master.getCurrOutputBufForUseByAssistants(), rawTextPart.getRawText());
    }

  }


  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.


  @Override protected void visitSoyNode(SoyNode node) {
    master.visitForUseByAssistants(node);
  }

}
