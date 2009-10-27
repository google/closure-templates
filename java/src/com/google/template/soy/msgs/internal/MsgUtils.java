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

import com.google.common.base.Pair;
import com.google.common.collect.Lists;
import com.google.template.soy.msgs.restricted.SoyMsgIdComputer;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
import com.google.template.soy.msgs.restricted.SoyMsgPlaceholderPart;
import com.google.template.soy.msgs.restricted.SoyMsgRawTextPart;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.MsgPlaceholderNode;

import java.util.List;


/**
 * Soy-specific utilities for working with messages.
 *
 * @author Kai Huang
 */
public class MsgUtils {

  private MsgUtils() {}


  /**
   * Builds the list of SoyMsgParts for the given MsgNode.
   * @param msgNode The message parsed from the Soy source.
   * @return The list of SoyMsgParts.
   */
  public static List<SoyMsgPart> buildMsgParts(MsgNode msgNode) {

    List<SoyMsgPart> msgParts = Lists.newArrayList();
    for (SoyNode child : msgNode.getChildren()) {
      if (child instanceof RawTextNode) {
        String rawText = ((RawTextNode) child).getRawText();
        msgParts.add(new SoyMsgRawTextPart(rawText));
      } else if (child instanceof MsgPlaceholderNode) {
        String placeholderName = msgNode.getPlaceholderName((MsgPlaceholderNode) child);
        msgParts.add(new SoyMsgPlaceholderPart(placeholderName));
      }
    }
    return msgParts;
  }


  /**
   * Computes the unique message id for the given MsgNode.
   * @param msgNode The message parsed from the Soy source.
   * @return The message id.
   */
  public static long computeMsgId(MsgNode msgNode) {

    return SoyMsgIdComputer.computeMsgId(
        buildMsgParts(msgNode), msgNode.getMeaning(), msgNode.getContentType());
  }


  /**
   * Builds the list of SoyMsgParts and computes the unique message id for the given MsgNode.
   * @param msgNode The message parsed from the Soy source.
   * @return A pair whose first item is the list of SoyMsgParts and second item is the message id.
   */
  public static Pair<List<SoyMsgPart>, Long> buildMsgPartsAndComputeMsgId(MsgNode msgNode) {

    List<SoyMsgPart> msgParts = buildMsgParts(msgNode);
    long msgId =
        SoyMsgIdComputer.computeMsgId(msgParts, msgNode.getMeaning(), msgNode.getContentType());
    return Pair.of(msgParts, msgId);
  }

}
