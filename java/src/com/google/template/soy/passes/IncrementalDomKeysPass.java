/*
 * Copyright 2018 Google Inc.
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
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.HtmlCloseTagNode;
import com.google.template.soy.soytree.HtmlContext;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.KeyNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SkipNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.VeLogNode;
import com.google.template.soy.types.SanitizedType.HtmlType;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;

/** Checks for validity of skip nodes wrt their host node. */
final class IncrementalDomKeysPass implements CompilerFilePass {
  private final boolean disableAllTypeChecking;

  public IncrementalDomKeysPass(boolean disableAllTypeChecking) {
    this.disableAllTypeChecking = disableAllTypeChecking;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    new IncrementalDomKeysPassVisitor(disableAllTypeChecking).exec(file);
  }

  private static final class IncrementalDomKeysPassVisitor extends AbstractSoyNodeVisitor<Void> {
    // Tracks the counter to use for a tag's generated key:
    // - When encountering an open tag without a manual key, the counter at the top of the stack is
    //   incremented.
    // - When encountering an open tag with a manual key, a new counter is pushed onto the stack.
    // - When encountering a close tag that corresponds to an open tag with a manual key, the
    //   topmost counter is then popped from the stack.
    private ArrayDeque<AtomicInteger> keyCounterStack;
    private ArrayDeque<Boolean> htmlKeyStack;
    private TemplateNode template;
    private boolean mustEmitKeyNodes = false;
    private boolean templateContainsUnpredictableContent = false;
    private final boolean disableAllTypeChecking;

    public IncrementalDomKeysPassVisitor(boolean disableAllTypeChecking) {
      this.disableAllTypeChecking = disableAllTypeChecking;
    }

    @Override
    public void visitTemplateNode(TemplateNode templateNode) {
      htmlKeyStack = new ArrayDeque<>();
      keyCounterStack = new ArrayDeque<>();
      keyCounterStack.push(new AtomicInteger());
      template = templateNode;
      templateContainsUnpredictableContent = false;
      visitBlockNode(templateNode);
    }
    /**
     * For all dynamic content, the exact number of nodes produced is unknown and could change from
     * render to render. thus, we need to key all content within the block AND all content after the
     * block.
     */
    private void visitBlockNode(ParentSoyNode<?> node) {
      // Everything within and after the block node must be keyed as it may be conditionally
      // rendered.
      mustEmitKeyNodes = true;
      visitChildren((ParentSoyNode) node);
    }

    /**
     * A print node could contain dynamic content that produces HTML of varying lengths, so key
     * everything after a well.
     */
    @Override
    public void visitPrintNode(PrintNode node) {
      if (!disableAllTypeChecking
          && node.getExpr().getRoot().getType().isAssignableFromStrict(HtmlType.getInstance())) {
        mustEmitKeyNodes = true;
        htmlKeyStack.push(true);
      }
    }
    /**
     * We don't know how this content is going to be used, so key everything. But because we haven't
     * rendered this let block yet, we don't set any mustEmitKeyNodes values after.
     */
    @Override
    public void visitLetContentNode(LetContentNode node) {
      var oldMustEmitKeyNodes = mustEmitKeyNodes;
      mustEmitKeyNodes = true;
      visitChildren((ParentSoyNode) node);
      mustEmitKeyNodes = oldMustEmitKeyNodes;
    }

    @Override
    public void visitSoyNode(SoyNode node) {
      var isTemplateNode = node instanceof TemplateNode || node instanceof VeLogNode;
      var isHtmlContextBlock =
          node instanceof HtmlContext.HtmlContextHolder
              && node instanceof ParentSoyNode
              && ((HtmlContext.HtmlContextHolder) node).getHtmlContext() == HtmlContext.HTML_PCDATA;
      if (isTemplateNode || isHtmlContextBlock) {
        visitBlockNode((ParentSoyNode) node);
        return;
      }
      if (node instanceof ParentSoyNode) {
        visitChildren((ParentSoyNode) node);
      }
    }

    @Override
    public void visitHtmlOpenTagNode(HtmlOpenTagNode openTagNode) {
      KeyNode keyNode = openTagNode.getKeyNode();
      // Templates that contain unbalanced or dynamic tags are not eligible for key performance
      // things, as
      // we don't really have a good understanding of how the DOM will look.
      boolean isUnpredictableTagPositions =
          openTagNode.getTaggedPairs().size() != 1
              || openTagNode.getTaggedPairs().get(0).getTaggedPairs().size() != 1
              || openTagNode.getTaggedPairs().get(0).getParent() != openTagNode.getParent();
      boolean isSelfClosing = openTagNode.isSelfClosing();
      if (!openTagNode.getTagName().isStatic() && (!isSelfClosing && isUnpredictableTagPositions)) {
        templateContainsUnpredictableContent = true;
      }
      if (keyNode != null) {
        keyCounterStack.push(new AtomicInteger());
      } else {
        openTagNode.setIsDynamic(
            mustEmitKeyNodes || templateContainsUnpredictableContent || openTagNode.isSkipRoot());
        openTagNode.setKeyId(incrementKeyForTemplate(template, openTagNode.isElementRoot()));
      }
      visitChildren(openTagNode);
      htmlKeyStack.push(mustEmitKeyNodes);
      mustEmitKeyNodes = false;
    }

    @Override
    public void visitHtmlCloseTagNode(HtmlCloseTagNode closeTagNode) {
      if (closeTagNode.getTaggedPairs().size() == 1) {
        HtmlOpenTagNode openTag = (HtmlOpenTagNode) closeTagNode.getTaggedPairs().get(0);
        if (openTag.getKeyNode() != null && !(openTag.getParent() instanceof SkipNode)) {
          keyCounterStack.pop();
        }
        if (!htmlKeyStack.isEmpty()) {
          mustEmitKeyNodes = htmlKeyStack.pop();
        }
      }
    }

    private String incrementKeyForTemplate(TemplateNode template, boolean isElementRoot) {
      if (isElementRoot) {
        return template.getTemplateName() + "-root";
      }
      AtomicInteger keyCounter = keyCounterStack.peek();
      return template.getTemplateName() + "-" + keyCounter.getAndIncrement();
    }
  }
}
