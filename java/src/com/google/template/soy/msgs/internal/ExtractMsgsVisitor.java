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

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.internal.MsgUtils.MsgPartsAndIds;
import com.google.template.soy.msgs.restricted.SoyMsg;
import com.google.template.soy.msgs.restricted.SoyMsgBundleImpl;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import java.util.Collections;
import java.util.List;

/**
 * Visitor for extracting messages from a Soy parse tree.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p>{@link #exec} should be called on a full parse tree. All messages will be extracted and
 * returned in a {@code SoyMsgBundle} (locale "en").
 *
 */
public final class ExtractMsgsVisitor extends AbstractSoyNodeVisitor<SoyMsgBundle> {

  private static final Ordering<SoyMsg> SOURCE_LOCATION_ORDERING =
      new Ordering<SoyMsg>() {
        @Override
        public int compare(SoyMsg left, SoyMsg right) {
          // the messages sorted by this comparator only have one source location.
          // messages gain extra source locations when merged together in a bundle.
          return Iterables.getOnlyElement(left.getSourceLocations())
              .compareTo(Iterables.getOnlyElement(right.getSourceLocations()));
        }
      };

  /** List of messages collected during the pass. */
  private List<SoyMsg> msgs;

  /**
   * Returns a SoyMsgBundle containing all messages extracted from the given SoyFileSetNode or
   * SoyFileNode (locale string is null).
   */
  @Override
  public SoyMsgBundle exec(SoyNode node) {
    Preconditions.checkArgument(node instanceof SoyFileSetNode || node instanceof SoyFileNode);

    msgs = Lists.newArrayList();
    visit(node);
    Collections.sort(msgs, SOURCE_LOCATION_ORDERING);
    return new SoyMsgBundleImpl(null, msgs);
  }

  /**
   * Returns a SoyMsgBundle containing all messages extracted from the given nodes (locale string is
   * null).
   */
  public SoyMsgBundle execOnMultipleNodes(Iterable<? extends SoyNode> nodes) {
    msgs = Lists.newArrayList();
    for (SoyNode node : nodes) {
      visit(node);
    }
    Collections.sort(msgs, SOURCE_LOCATION_ORDERING);
    return new SoyMsgBundleImpl(null, msgs);
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.

  @Override
  protected void visitMsgNode(MsgNode node) {
    MsgPartsAndIds msgPartsAndIds = MsgUtils.buildMsgPartsAndComputeMsgIdForDualFormat(node);
    SoyMsg.Builder builder = SoyMsg.builder().setId(msgPartsAndIds.id);
    if (node.getMeaning() != null) {
      builder.setMeaning(node.getMeaning());
    }
    SoyMsg msg =
        builder
            .setDesc(node.getDesc())
            .setIsHidden(node.isHidden())
            .setContentType(node.getContentType())
            .addSourceLocation(node.getSourceLocation())
            .setIsPlrselMsg(node.isPlrselMsg())
            .setParts(msgPartsAndIds.parts)
            .build();
    msgs.add(msg);
  }

  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.

  @Override
  protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode<?>) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }
}
