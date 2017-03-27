/*
 * Copyright 2016 Google Inc.
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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForeachNode;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.HtmlAttributeValueNode;
import com.google.template.soy.soytree.HtmlAttributeValueNode.Quotes;
import com.google.template.soy.soytree.HtmlCloseTagNode;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TemplateNode;
import java.util.ArrayList;
import java.util.List;

/**
 * Replaces {@link HtmlOpenTagNode} and {@link HtmlCloseTagNode} with a set of RawTextNodes and the
 * children.
 *
 * <p>This pass ensures that the rest of the compiler can remain agnostic about these nodes.
 */
final class DesugarHtmlNodesPass extends CompilerFilePass {

  @Override
  public void run(SoyFileNode file, IdGenerator idGenerator) {
    new RewritingVisitor(idGenerator).exec(file);
    // Whether or not we have replaced any nodes, we still need to merge adjacent RawTextNodes since
    // the parser may have divided them up in such a way that the Autoescaper can't handle it. In
    // particular the autoescaper expects to be able to see whole close tags, so it can handle
    // </script> as a single rawtextnode, but not as </,script,> (3 raw text nodes).
    new CombineConsecutiveRawTextNodesVisitor(idGenerator).exec(file);
  }

  private static final class RewritingVisitor extends AbstractSoyNodeVisitor<Void> {
    private final IdGenerator idGenerator;

    /** Tracks whether we need a space character before the next attribute character. */
    boolean needsSpaceForAttribute;

    /** Tracks all the nodes that should replace the current node. */
    final List<StandaloneNode> replacements = new ArrayList<>();

    RewritingVisitor(IdGenerator idGenerator) {
      this.idGenerator = idGenerator;
    }

    @Override
    protected void visitTemplateNode(TemplateNode node) {
      needsSpaceForAttribute = false;
      visitChildren(node);
    }

    @Override
    protected void visitHtmlCloseTagNode(HtmlCloseTagNode node) {
      needsSpaceForAttribute = true;
      visitChildren(node);
      needsSpaceForAttribute = false;

      replacements.add(createPrefix("</", node));
      replacements.add(node.getTagName().getNode());
      replacements.addAll(node.getChildren());
      replacements.add(createSuffix(">", node));
    }

    @Override
    protected void visitHtmlOpenTagNode(HtmlOpenTagNode node) {
      needsSpaceForAttribute = true;
      visitChildren(node);
      needsSpaceForAttribute = false;

      replacements.add(createPrefix("<", node));
      replacements.add(node.getTagName().getNode());
      replacements.addAll(node.getChildren());
      replacements.add(createSuffix(node.isSelfClosing() ? "/>" : ">", node));
    }

    @Override
    protected void visitHtmlAttributeValueNode(HtmlAttributeValueNode node) {
      visitChildren(node);

      Quotes quotes = node.getQuotes();
      if (quotes == Quotes.NONE) {
        replacements.addAll(node.getChildren());
      } else {
        replacements.add(createPrefix(quotes.getQuotationCharacter(), node));
        replacements.addAll(node.getChildren());
        replacements.add(createSuffix(quotes.getQuotationCharacter(), node));
      }
    }

    /**
     * Returns a new RawTextNode with the given content at the beginning of the {@code context}
     * node.
     */
    private RawTextNode createPrefix(String prefix, SoyNode context) {
      SourceLocation location = context.getSourceLocation().getBeginLocation();
      // location points to the first character, so if the content is longer than one character
      // extend the source location to cover it.  e.g. content might be "</"
      if (prefix.length() > 1) {
        location = location.offsetEndCol(prefix.length() - 1);
      }
      return new RawTextNode(idGenerator.genId(), prefix, location);
    }

    /** Returns a new RawTextNode with the given content at the end of the {@code context} node. */
    private RawTextNode createSuffix(String suffix, SoyNode context) {
      SourceLocation location = context.getSourceLocation().getEndLocation();
      // location points to the last character, so if the content is longer than one character
      // extend the source location to cover it.  e.g. content might be "/>"
      if (suffix.length() > 1) {
        location = location.offsetStartCol(suffix.length() - 1);
      }
      return new RawTextNode(idGenerator.genId(), suffix, location);
    }

    @Override
    protected void visitHtmlAttributeNode(HtmlAttributeNode node) {
      visitChildren(node);

      // prefix the value with a single whitespace character.  This makes it unambiguous with the
      // preceding attribute/tag name.
      // There are some cases where we don't need this:
      // 1. if the attribute children don't render anything, e.g. {$foo ?: ''}
      //    -This would only be fixable by modifying the code generators to dynamically insert the
      //     space character
      // 2. if the preceding node is a quoted attribute value
      //    -This would always work, but is technically out of spec so we should probably avoid it.
      if (needsSpaceForAttribute) {
        // TODO(lukes): in this case, if the attribute is dynamic and ultimately renders the
        // empty string, we will render an extra space.
        replacements.add(
            new RawTextNode(idGenerator.genId(), " ", node.getSourceLocation().getBeginLocation()));
      } else {
        // After any attribute, the next attribute will need a space character.
        needsSpaceForAttribute = true;
      }
      replacements.add(node.getChild(0));
      if (node.hasValue()) {
        replacements.add(new RawTextNode(idGenerator.genId(), "=", node.getEqualsLocation()));
        // normally there would only be 1 child, but rewriting may have split it into multiple
        replacements.addAll(node.getChildren().subList(1, node.numChildren()));
      }
    }

    @Override
    protected void visitCallParamContentNode(CallParamContentNode node) {
      boolean oldInTag = needsSpaceForAttribute;
      needsSpaceForAttribute = false;
      visitChildren(node);
      needsSpaceForAttribute = oldInTag;
    }

    @Override
    protected void visitLetContentNode(LetContentNode node) {
      boolean prev = needsSpaceForAttribute;
      needsSpaceForAttribute = false;
      visitChildren(node);
      needsSpaceForAttribute = prev;
    }

    @Override
    protected void visitIfNode(IfNode node) {
      visitControlFlowBranches(node.getChildren());
    }

    @Override
    protected void visitForNode(ForNode node) {
      visitControlFlowBranches(ImmutableList.<BlockNode>of(node));
    }

    @Override
    protected void visitForeachNode(ForeachNode node) {
      visitControlFlowBranches(node.getChildren());
    }

    @Override
    protected void visitSwitchNode(SwitchNode node) {
      visitControlFlowBranches(node.getChildren());
    }

    private void visitControlFlowBranches(List<BlockNode> branches) {
      // If any one of the branches sets needsSpaceForAttribute to true, then it should get set to
      // true for the whole control flow block.
      boolean start = needsSpaceForAttribute;
      boolean end = needsSpaceForAttribute;
      for (BlockNode child : branches) {
        visitChildren(child);
        end |= needsSpaceForAttribute;
        needsSpaceForAttribute = start;
      }
      needsSpaceForAttribute = end;
    }

    @Override
    protected void visitSoyNode(SoyNode node) {
      if (node instanceof SoyNode.ParentSoyNode) {
        visitChildren((ParentSoyNode<?>) node);
      }
    }

    @Override
    protected void visitChildren(ParentSoyNode<?> node) {
      if (node instanceof BlockNode) {
        BlockNode blockNode = (BlockNode) node;
        List<? extends SoyNode> children = node.getChildren();
        for (int i = 0; i < children.size(); i++) {
          visit(children.get(i));
          if (!replacements.isEmpty()) {
            blockNode.removeChild(i);
            blockNode.addChildren(i, replacements);
            i += replacements.size() - 1;
            replacements.clear();
          }
        }
      } else {
        super.visitChildren(node);
      }
      checkState(replacements.isEmpty());
    }
  }
}
