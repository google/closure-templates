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

package com.google.template.soy.incrementaldomsrc;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.HtmlAttributeValueNode;
import com.google.template.soy.soytree.HtmlCloseTagNode;
import com.google.template.soy.soytree.HtmlCommentNode;
import com.google.template.soy.soytree.HtmlContext;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.LogNode;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.TagName;
import com.google.template.soy.soytree.TemplateNode;
import java.util.ArrayDeque;

/**
 * A visitor that populates the {@link HtmlContext} fields of {@link MsgFallbackGroupNode}, {@link
 * RawTextNode} and {@link PrintNode} and also enforces some additional restrictions for incremental
 * dom.
 *
 * <p>The additional restrictions are:
 *
 * <ul>
 *   <li>Tags must have literal names
 *   <li>Attributes with values must have literal names
 *   <li>{@code xid} and {@code css} commands can only appear in attribute value context.
 *   <li>Enforces the correct usage of self closing tags.
 * </ul>
 *
 * <p>TODO(lukes): A lot of this logic should really be handled by the autoescaper, but
 * incrementaldom doesn't currently run the autoescaper (which is weird, it probably should at least
 * to get attribute escaping correct).
 */
final class HtmlContextVisitor extends AbstractSoyNodeVisitor<Void> {


  private final ArrayDeque<HtmlContext> stateStack = new ArrayDeque<>();

  private static final ImmutableMap<TagName.RcDataTagName, HtmlContext> TAG_TO_CONTENT_TYPE =
      ImmutableMap.of(
          TagName.RcDataTagName.SCRIPT, HtmlContext.JS,
          TagName.RcDataTagName.TITLE, HtmlContext.HTML_RCDATA,
          TagName.RcDataTagName.TEXTAREA, HtmlContext.HTML_RCDATA,
          TagName.RcDataTagName.XMP, HtmlContext.HTML_RCDATA,
          TagName.RcDataTagName.STYLE, HtmlContext.CSS);

  @Override
  protected void visitTemplateNode(TemplateNode node) {
    checkState(stateStack.isEmpty());
    SanitizedContentKind contentKind = node.getContentKind();
    pushState(contextForKind(contentKind));
    visitChildren(node);
    popState();
    checkState(stateStack.isEmpty());
  }

  @Override
  protected void visitCallParamContentNode(CallParamContentNode node) {
    pushState(contextForKind(node.getContentKind()));
    visitChildren(node);
    popState();
  }

  @Override
  protected void visitLetContentNode(LetContentNode node) {
    pushState(contextForKind(node.getContentKind()));
    visitChildren(node);
    popState();
  }

  @Override
  protected void visitHtmlCloseTagNode(HtmlCloseTagNode node) {
    if (TAG_TO_CONTENT_TYPE.containsKey(node.getTagName().getRcDataTagName())) {
      popState();
    }
    pushState(HtmlContext.HTML_TAG_NAME);
    visitChildren(node);
    popState();
  }

  @Override
  protected void visitHtmlOpenTagNode(HtmlOpenTagNode node) {

    pushState(HtmlContext.HTML_TAG_NAME);
    visit(node.getChild(0));
    popState();
    pushState(HtmlContext.HTML_TAG);
    for (int i = 1; i < node.numChildren(); i++) {
      visit(node.getChild(i));
    }
    popState();
    if (TAG_TO_CONTENT_TYPE.containsKey(node.getTagName().getRcDataTagName())) {
      pushState(TAG_TO_CONTENT_TYPE.get(node.getTagName().getRcDataTagName()));
    }
  }

  @Override
  protected void visitHtmlAttributeNode(HtmlAttributeNode node) {
    boolean hasValue = node.hasValue();
    if (hasValue) {
      pushState(HtmlContext.HTML_ATTRIBUTE_NAME);
    }
    visit(node.getChild(0)); // visit the name (or dynamic attribute)
    if (hasValue) {
      popState();
      for (int i = 1; i < node.numChildren(); i++) {
        visit(node.getChild(i));
      }
    }
  }

  @Override
  protected void visitHtmlAttributeValueNode(HtmlAttributeValueNode node) {
    // This is consistent with the old HtmlTransformVisitor, but doesn't really make sense, sometime
    // this is JS, URL or CSS...
    pushState(HtmlContext.HTML_NORMAL_ATTR_VALUE);
    super.visitHtmlAttributeValueNode(node);
    popState();
  }

  @Override
  protected void visitLogNode(LogNode node) {
    // The contents of a {log} statement are always text.
    pushState(HtmlContext.TEXT);
    visitChildren(node);
    popState();
  }

  @Override
  protected void visitMsgFallbackGroupNode(MsgFallbackGroupNode node) {
    node.setHtmlContext(getState());
    visitChildren(node);
  }

  @Override
  protected void visitPrintNode(PrintNode node) {
    node.setHtmlContext(getState());
  }

  @Override
  protected void visitCallNode(CallNode node) {
    node.setHtmlContext(getState());
    visitChildren((ParentSoyNode<?>) node);
  }

  @Override
  protected void visitHtmlCommentNode(HtmlCommentNode node) {
    // This is technically RCDATA but for our purposes TEXT is fine.
    pushState(HtmlContext.TEXT);
    visitChildren(node);
    popState();
  }

  @Override
  protected void visitRawTextNode(RawTextNode node) {
    node.setHtmlContext(getState());
  }

  @Override
  protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }

  private void pushState(HtmlContext context) {
    stateStack.push(context);
  }

  private void popState() {
    stateStack.pop();
  }

  private HtmlContext getState() {
    return stateStack.peek();
  }

  private HtmlContext contextForKind(SanitizedContentKind contentKind) {
    switch (contentKind) {
      case HTML:
        return HtmlContext.HTML_PCDATA;
      case ATTRIBUTES:
        return HtmlContext.HTML_TAG;
      default:
        return HtmlContext.TEXT;
    }
  }
}
