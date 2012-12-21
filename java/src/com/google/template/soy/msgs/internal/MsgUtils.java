/*
 * Copyright 2009 Google Inc.
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

package com.google.template.soy.msgs.internal;

import com.google.common.collect.Lists;
import com.google.template.soy.internal.base.Pair;
import com.google.template.soy.msgs.restricted.SoyMsgIdComputer;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
import com.google.template.soy.msgs.restricted.SoyMsgPlaceholderPart;
import com.google.template.soy.msgs.restricted.SoyMsgPluralCaseSpec;
import com.google.template.soy.msgs.restricted.SoyMsgPluralPart;
import com.google.template.soy.msgs.restricted.SoyMsgPluralRemainderPart;
import com.google.template.soy.msgs.restricted.SoyMsgRawTextPart;
import com.google.template.soy.msgs.restricted.SoyMsgSelectPart;
import com.google.template.soy.soytree.CaseOrDefaultNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.MsgPlaceholderNode;
import com.google.template.soy.soytree.MsgPluralCaseNode;
import com.google.template.soy.soytree.MsgPluralDefaultNode;
import com.google.template.soy.soytree.MsgPluralNode;
import com.google.template.soy.soytree.MsgPluralRemainderNode;
import com.google.template.soy.soytree.MsgSelectCaseNode;
import com.google.template.soy.soytree.MsgSelectDefaultNode;
import com.google.template.soy.soytree.MsgSelectNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;

import java.util.List;


/**
 * Soy-specific utilities for working with messages.
 *
 * @author Kai Huang
 */
public class MsgUtils {


  private MsgUtils() {}


  // -----------------------------------------------------------------------------------------------
  // Utilities assuming a specific dual format: use unbraced placeholders for regular messages and
  // use braced placeholders for plural/select messages.


  /**
   * Builds the list of SoyMsgParts and computes the unique message id for the given MsgNode,
   * assuming a specific dual format.
   *
   * Note: The field {@code idUsingBracedPhs} in the return value is simply set to -1L.
   *
   * @param msgNode The message parsed from the Soy source.
   * @return A {@code MsgPartsAndIds} object, asssuming a specific dual format, with field
   *     {@code idUsingBracedPhs} set to -1L.
   */
  public static MsgPartsAndIds buildMsgPartsAndComputeMsgIdForDualFormat(MsgNode msgNode) {

    if (msgNode.isPlrselMsg()) {
      MsgPartsAndIds mpai = buildMsgPartsAndComputeMsgIds(msgNode, true);
      return new MsgPartsAndIds(mpai.parts, mpai.idUsingBracedPhs, -1L);
    } else {
      return buildMsgPartsAndComputeMsgIds(msgNode, false);
    }
  }


  /**
   * Computes the unique message id for the given MsgNode, assuming a specific dual format.
   * @param msgNode The message parsed from the Soy source.
   * @return The message id, assuming a specific dual format.
   */
  public static long computeMsgIdForDualFormat(MsgNode msgNode) {
    return msgNode.isPlrselMsg() ? computeMsgIdUsingBracedPhs(msgNode) : computeMsgId(msgNode);
  }


  // -----------------------------------------------------------------------------------------------
  // Flexible utilities. Currently private to prevent accidental usage, but can be public if needed.


  /**
   * Value class for the return value of {@code buildMsgPartsAndComputeMsgId()}.
   */
  public static class MsgPartsAndIds {

    /** The parts that make up the message content. */
    public final List<SoyMsgPart> parts;
    /** A unique id for this message (same across all translations). */
    public final long id;
    /** An alternate unique id for this message. Only use this if you use braced placeholders. */
    public final long idUsingBracedPhs;

    private MsgPartsAndIds(List<SoyMsgPart> parts, long id, long idUsingBracedPhs) {
      this.parts = parts;
      this.id = id;
      this.idUsingBracedPhs = idUsingBracedPhs;
    }
  }


  /**
   * Builds the list of SoyMsgParts and computes the unique message id(s) for the given MsgNode.
   * @param msgNode The message parsed from the Soy source.
   * @param doComputeMsgIdUsingBracedPhs Whether to compute the alternate message id using braced
   *     placeholders. If set to false, then the field {@code idUsingBracedPhs} in the return value
   *     is simply set to -1L.
   * @return A {@code MsgPartsAndIds} object.
   */
  private static MsgPartsAndIds buildMsgPartsAndComputeMsgIds(
      MsgNode msgNode, boolean doComputeMsgIdUsingBracedPhs) {

    List<SoyMsgPart> msgParts = buildMsgParts(msgNode);
    long msgId =
        SoyMsgIdComputer.computeMsgId(msgParts, msgNode.getMeaning(), msgNode.getContentType());
    long msgIdUsingBracedPhs = doComputeMsgIdUsingBracedPhs ?
        SoyMsgIdComputer.computeMsgIdUsingBracedPhs(
            msgParts, msgNode.getMeaning(), msgNode.getContentType()) :
        -1L;
    return new MsgPartsAndIds(msgParts, msgId, msgIdUsingBracedPhs);
  }


  /**
   * Computes the unique message id for the given MsgNode.
   * @param msgNode The message parsed from the Soy source.
   * @return The message id.
   */
  private static long computeMsgId(MsgNode msgNode) {
    return SoyMsgIdComputer.computeMsgId(
        buildMsgParts(msgNode), msgNode.getMeaning(), msgNode.getContentType());
  }


  /**
   * Computes the alternate unique id for this message. Only use this if you use braced
   * placeholders.
   * @param msgNode The message parsed from the Soy source.
   * @return The alternate message id using braced placeholders.
   */
  private static long computeMsgIdUsingBracedPhs(MsgNode msgNode) {
    return SoyMsgIdComputer.computeMsgIdUsingBracedPhs(
        buildMsgParts(msgNode), msgNode.getMeaning(), msgNode.getContentType());
  }


  // -----------------------------------------------------------------------------------------------
  // Helpers for building the list of message parts.


  /**
   * Builds the list of SoyMsgParts for the given MsgNode.
   * @param msgNode The message parsed from the Soy source.
   * @return The list of SoyMsgParts.
   */
  private static List<SoyMsgPart> buildMsgParts(MsgNode msgNode) {
    return buildMsgPartsForChildren(msgNode, msgNode);
  }


  /**
   * Builds the list of SoyMsgParts for all the children of a given parent node.
   * @param parent Can be MsgNode, MsgPluralCaseNode, MsgPluralDefaultNode,
   *     MsgSelectCaseNode, or MsgSelectDefaultNode.
   * @param msgNode The MsgNode containing 'parent'.
   */
  private static List<SoyMsgPart> buildMsgPartsForChildren(
      BlockNode parent, MsgNode msgNode) {

    List<SoyMsgPart> msgParts = Lists.newArrayList();

    for (StandaloneNode child : parent.getChildren()) {
      if (child instanceof RawTextNode) {
        String rawText = ((RawTextNode) child).getRawText();
        msgParts.add(new SoyMsgRawTextPart(rawText));
      } else if (child instanceof MsgPlaceholderNode) {
        String placeholderName = msgNode.getPlaceholderName((MsgPlaceholderNode) child);
        msgParts.add(new SoyMsgPlaceholderPart(placeholderName));
      } else if (child instanceof MsgPluralRemainderNode) {
        msgParts.add(new SoyMsgPluralRemainderPart(
            msgNode.getPluralVarName(child.getNearestAncestor(MsgPluralNode.class))));
      } else if (child instanceof MsgPluralNode) {
        msgParts.add(buildMsgPartForPlural((MsgPluralNode) child, msgNode));
      } else if (child instanceof MsgSelectNode) {
        msgParts.add(buildMsgPartForSelect((MsgSelectNode) child, msgNode));
      }
    }

    return msgParts;
  }


  /**
   * Builds the list of SoyMsgParts for the given MsgPluralNode.
   * @param msgPluralNode The plural node parsed from the Soy source.
   * @param msgNode The MsgNode containing 'msgPluralNode'.
   * @return A SoyMsgPluralPart.
   */
  private static SoyMsgPluralPart buildMsgPartForPlural(
      MsgPluralNode msgPluralNode, MsgNode msgNode) {

    // This is the list of the cases.
    List<Pair<SoyMsgPluralCaseSpec, List<SoyMsgPart>>> pluralCases = Lists.newArrayList();

    for (CaseOrDefaultNode child : msgPluralNode.getChildren()) {

      List<SoyMsgPart> caseMsgParts = buildMsgPartsForChildren(child, msgNode);
      SoyMsgPluralCaseSpec caseSpec;

      if (child instanceof MsgPluralCaseNode) {
        caseSpec = new SoyMsgPluralCaseSpec(((MsgPluralCaseNode) child).getCaseNumber());

      } else if (child instanceof MsgPluralDefaultNode) {
        caseSpec = new SoyMsgPluralCaseSpec("other");

      } else {
        throw new AssertionError("Unidentified node under a plural node.");
      }

      pluralCases.add(Pair.of(caseSpec, caseMsgParts));
    }

    return new SoyMsgPluralPart(
        msgNode.getPluralVarName(msgPluralNode), msgPluralNode.getOffset(), pluralCases);
  }


  /**
   * Builds the list of SoyMsgParts for the given MsgSelectNode.
   * @param msgSelectNode The select node parsed from the Soy source.
   * @param msgNode The MsgNode containing 'msgSelectNode'.
   * @return A SoyMsgSelectPart.
   */
  private static SoyMsgSelectPart buildMsgPartForSelect(
      MsgSelectNode msgSelectNode, MsgNode msgNode) {

    // This is the list of the cases.
    List<Pair<String, List<SoyMsgPart>>> selectCases = Lists.newArrayList();

    for (CaseOrDefaultNode child : msgSelectNode.getChildren()) {
      List<SoyMsgPart> caseMsgParts = buildMsgPartsForChildren(child, msgNode);
      String caseValue;
      if (child instanceof MsgSelectCaseNode) {
        caseValue = ((MsgSelectCaseNode) child).getCaseValue();
      } else if (child instanceof MsgSelectDefaultNode) {
        caseValue = null;
      } else {
        throw new AssertionError("Unidentified node under a select node.");
      }
      selectCases.add(Pair.of(caseValue, caseMsgParts));
    }

    return new SoyMsgSelectPart(msgNode.getSelectVarName(msgSelectNode), selectCases);
  }

}
