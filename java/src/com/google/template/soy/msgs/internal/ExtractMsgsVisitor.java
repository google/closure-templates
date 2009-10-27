/*
 * Copyright 2008 Google Inc.
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
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.restricted.SoyMsg;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;

import java.util.List;


/**
 * Visitor for extracting messages from a Soy parse tree.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> {@link #exec} should be called on a full parse tree. All messages will be extracted and
 * returned in a {@code SoyMsgBundle} (locale "en").
 *
 * @author Kai Huang
 */
public class ExtractMsgsVisitor extends AbstractSoyNodeVisitor<SoyMsgBundle> {


  /** List of messages collected during the pass. */
  List<SoyMsg> msgs;

  /** Current Soy file path (during a pass). */
  String currentSource;


  @Override protected void setup() {
    msgs = Lists.newArrayList();
    currentSource = null;
  }


  /** Returns a SoyMsgBundle containing all the extracted messages (locale string is null). */
  @Override protected SoyMsgBundle getResult() {
    return new SoyMsgBundle(null, msgs);
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for concrete classes.


  @Override protected void visitInternal(SoyFileNode node) {

    currentSource = node.getFileName();
    visitChildren(node);
    currentSource = null;
  }


  @Override protected void visitInternal(MsgNode node) {

    Pair<List<SoyMsgPart>, Long> msgPartsAndId = MsgUtils.buildMsgPartsAndComputeMsgId(node);
    msgs.add(new SoyMsg(
        msgPartsAndId.second, null, node.getMeaning(), node.getDesc(), node.isHidden(),
        node.getContentType(), currentSource, msgPartsAndId.first));
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for interfaces.


  @Override protected void visitInternal(SoyNode node) {
    // Nothing to do for non-parent node.
  }


  @Override protected void visitInternal(ParentSoyNode<? extends SoyNode> node) {
    visitChildren(node);
  }

}
