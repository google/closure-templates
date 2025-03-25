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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.basicdirectives.BasicEscapeDirective;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.HtmlAttributeValueNode;
import com.google.template.soy.soytree.HtmlAttributeValueNode.Quotes;
import com.google.template.soy.soytree.HtmlCloseTagNode;
import com.google.template.soy.soytree.HtmlCommentNode;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.HtmlTagNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.MsgPluralNode;
import com.google.template.soy.soytree.MsgSelectNode;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.RenderUnitNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TemplateNode;
import java.util.List;
import java.util.Optional;

/**
 * Replaces {@link HtmlOpenTagNode} and {@link HtmlCloseTagNode} with a set of RawTextNodes and the
 * children.
 *
 * <p>This pass ensures that the rest of the compiler can remain agnostic about these nodes.
 */
public final class DesugarHtmlNodesPass implements CompilerFileSetPass {

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    for (SoyFileNode fileNode : sourceFiles) {
      run(fileNode, idGenerator);
    }
    return Result.CONTINUE;
  }

  @VisibleForTesting
  public void run(SoyNode node, IdGenerator idGenerator) {
    new RewritingVisitor(idGenerator).exec(node);
  }

  private static final class RewritingVisitor extends AbstractSoyNodeVisitor<Void> {
    private final IdGenerator idGenerator;

    /** Tracks whether we need a space character before the next attribute character. */
    boolean needsSpaceForAttribute;

    /**
     * Tracks whether we need a space character before the '/' of a self closing tag. This is only
     * necessary if there is an unquoted or dynamic attribute as the final attribute.
     */
    boolean needsSpaceSelfClosingTag;

    /** Tracks all the nodes that should replace the current node. */
    private Optional<ImmutableList<StandaloneNode>> replacements = Optional.empty();

    RewritingVisitor(IdGenerator idGenerator) {
      this.idGenerator = idGenerator;
    }

    @Override
    protected void visitTemplateNode(TemplateNode node) {
      needsSpaceForAttribute = false;
      visitChildren(node);
    }

    private void visitHtmlTagNode(HtmlTagNode tag) {
      needsSpaceForAttribute = true;

      visitChildren(tag);
      // Synthetic tags are not rendered to raw HTML text. They are only rendered in backends that
      // emit code for HTML tags, like the iDOM backend.
      //
      // Since this pass is not run by the iDOM back-end, just remove synthetic nodes here.
      if (tag.isSynthetic()) {
        replacements = Optional.of(ImmutableList.of());
      } else {
        replacements =
            Optional.of(
                ImmutableList.<StandaloneNode>builder()
                    .add(createPrefix(tag instanceof HtmlOpenTagNode ? "<" : "</", tag))
                    .addAll(tag.getChildren())
                    .add(
                        createSuffix(
                            tag instanceof HtmlOpenTagNode
                                    && ((HtmlOpenTagNode) tag).isSelfClosing()
                                ? (needsSpaceSelfClosingTag ? " />" : "/>")
                                : ">",
                            tag))
                    .build());
      }

      needsSpaceForAttribute = false;
      needsSpaceSelfClosingTag = false;
    }

    @Override
    protected void visitHtmlCloseTagNode(HtmlCloseTagNode node) {
      visitHtmlTagNode(node);
    }

    @Override
    protected void visitHtmlOpenTagNode(HtmlOpenTagNode openTag) {
      visitHtmlTagNode(openTag);
    }

    @Override
    protected void visitHtmlCommentNode(HtmlCommentNode node) {
      visitChildren(node);
      replacements =
          Optional.of(
              ImmutableList.<StandaloneNode>builder()
                  .add(createPrefix("<!--", node))
                  .addAll(node.getChildren())
                  .add(createSuffix("-->", node))
                  .build());
    }

    @Override
    protected void visitHtmlAttributeValueNode(HtmlAttributeValueNode node) {
      visitChildren(node);

      Quotes quotes = node.getQuotes();
      if (quotes == Quotes.NONE) {
        replacements = Optional.of(ImmutableList.copyOf(node.getChildren()));
        needsSpaceSelfClosingTag = true;
      } else {
        replacements =
            Optional.of(
                ImmutableList.<StandaloneNode>builder()
                    .add(createPrefix(quotes.getQuotationCharacter(), node))
                    .addAll(node.getChildren())
                    .add(createSuffix(quotes.getQuotationCharacter(), node))
                    .build());
        needsSpaceSelfClosingTag = false;
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
        location = location.offsetEndCol(suffix.length() - 1);
      }
      return new RawTextNode(idGenerator.genId(), suffix, location);
    }

    @Override
    protected void visitHtmlAttributeNode(HtmlAttributeNode node) {
      visitChildren(node);

      ImmutableList.Builder<StandaloneNode> builder = ImmutableList.builder();
      boolean needsDynamicSpace = node.getStaticKey() == null && !node.hasValue();
      boolean isFlushPendingLoggingAttribute = false;
      if (needsSpaceForAttribute && !needsDynamicSpace) {
        // prefix the value with a single whitespace character when the attribute is static. This
        // makes it unambiguous with the preceding attribute/tag name.
        builder.add(
            new RawTextNode(idGenerator.genId(), " ", node.getSourceLocation().getBeginLocation()));
      } else if (needsSpaceForAttribute && node.getStaticKey() == null) {
        // use a print directive to conditionally add a whitespace for dynamic attributes.
        SoyPrintDirective whitespaceDirective =
            new BasicEscapeDirective.WhitespaceHtmlAttributesDirective();
        var first = node.getChild(0);
        if (first instanceof PrintNode) {
          var printNode = (PrintNode) first;
          if (printNode.getExpr().getRoot() instanceof FunctionNode
              && ((FunctionNode) printNode.getExpr().getRoot()).getSoyFunction()
                  == BuiltinFunction.FLUSH_PENDING_LOGGING_ATTRIBUTES) {
            isFlushPendingLoggingAttribute = true;
          } else {
          PrintDirectiveNode whitespaceDirectiveNode =
              PrintDirectiveNode.createSyntheticNode(
                  idGenerator.genId(),
                  Identifier.create("|whitespaceHtmlAttributes", node.getSourceLocation()),
                  node.getSourceLocation());
          whitespaceDirectiveNode.setPrintDirective(whitespaceDirective);
            printNode.addChild(whitespaceDirectiveNode);
          }
        } else if (first instanceof CallNode) {
          CallNode typed = (CallNode) first;
          typed.setEscapingDirectives(
              ImmutableList.<SoyPrintDirective>builder()
                  .addAll(typed.getEscapingDirectives())
                  .add(whitespaceDirective)
                  .build());
        } else {
          throw new AssertionError(
              "Found node that is not PrintNode or CallNode inside HtmlAttributeNode");
        }
      } else {
        // After any attribute, the next attribute will need a space character.
        needsSpaceForAttribute = true;
      }
      builder.add(node.getChild(0));
      if (node.hasValue()) {
        builder.add(new RawTextNode(idGenerator.genId(), "=", node.getEqualsLocation()));
        // normally there would only be 1 child, but rewriting may have split it into multiple
        builder.addAll(node.getChildren().subList(1, node.numChildren()));
      }
      if (!node.hasValue() && node.getStaticKey() == null && !isFlushPendingLoggingAttribute) {
        // Add a space after the last attribute if it is dynamic and the tag is self-closing. If the
        // attribute value isn't quoted, a space is needed to disambiguate with the "/" character.
        needsSpaceSelfClosingTag = true;
      }
      replacements = Optional.of(builder.build());
    }

    @Override
    protected void visitCallParamContentNode(CallParamContentNode node) {
      visitRenderUnitNode(node);
    }

    @Override
    protected void visitLetContentNode(LetContentNode node) {
      visitRenderUnitNode(node);
    }

    private void visitRenderUnitNode(RenderUnitNode node) {
      boolean prevNeedsSpaceForAttribute = needsSpaceForAttribute;
      needsSpaceForAttribute = false;
      boolean prevNeedsSpaceForSelfClosingTag = needsSpaceSelfClosingTag;
      needsSpaceSelfClosingTag = false;
      visitChildren(node);
      needsSpaceForAttribute = prevNeedsSpaceForAttribute;
      needsSpaceSelfClosingTag = prevNeedsSpaceForSelfClosingTag;
    }

    @Override
    protected void visitIfNode(IfNode node) {
      visitControlFlowBranches(node.getChildren());
    }

    @Override
    protected void visitForNode(ForNode node) {
      visitControlFlowBranches(node.getChildren());
    }

    @Override
    protected void visitSwitchNode(SwitchNode node) {
      visitControlFlowBranches(node.getChildren());
    }

    @Override
    protected void visitMsgFallbackGroupNode(MsgFallbackGroupNode node) {
      visitControlFlowBranches(node.getChildren());
    }

    @Override
    protected void visitMsgPluralNode(MsgPluralNode node) {
      visitControlFlowBranches(node.getChildren());
    }

    @Override
    protected void visitMsgSelectNode(MsgSelectNode node) {
      visitControlFlowBranches(node.getChildren());
    }

    private void visitControlFlowBranches(List<? extends ParentSoyNode<?>> branches) {
      // If any one of the branches sets needsSpaceForAttribute to true, then it should get set to
      // true for the whole control flow block.
      boolean startNeedsSpaceForAttribute = needsSpaceForAttribute;
      boolean endNeedsSpaceForAttribute = needsSpaceForAttribute;
      boolean startNeedsSpaceForSelfClosingTag = needsSpaceSelfClosingTag;
      boolean endNeedsSpaceForSelfClosingTag = needsSpaceSelfClosingTag;
      for (ParentSoyNode<?> branch : branches) {
        visitChildren(branch);
        endNeedsSpaceForAttribute |= needsSpaceForAttribute;
        needsSpaceForAttribute = startNeedsSpaceForAttribute;
        endNeedsSpaceForSelfClosingTag |= needsSpaceSelfClosingTag;
        needsSpaceSelfClosingTag = startNeedsSpaceForSelfClosingTag;
      }
      needsSpaceForAttribute = endNeedsSpaceForAttribute;
      needsSpaceSelfClosingTag = endNeedsSpaceForSelfClosingTag;
    }

    @Override
    protected void visitSoyNode(SoyNode node) {
      if (node instanceof SoyNode.ParentSoyNode) {
        visitChildren((ParentSoyNode<?>) node);
      }
    }

    @Override
    protected void visitChildren(ParentSoyNode<?> node) {
      doVisitChildren(node);
    }

    // extracted as a helper method to capture the type variable
    private <C extends SoyNode> void doVisitChildren(ParentSoyNode<C> parent) {
      for (int i = 0; i < parent.numChildren(); i++) {
        C child = parent.getChild(i);
        visit(child);
        if (replacements.isPresent()) {
          // Note: if {@code replacements} is empty, the child node is simply removed. This happens
          // for example if the child node is synthetic and was auto-injected during
          // StrictHtmlValidationPass.
          parent.removeChild(i);
          // safe because every replacement always replaces a standalone node with other standalone
          // nodes.
          @SuppressWarnings("unchecked")
          List<? extends C> typedReplacements = (List<? extends C>) replacements.get();
          parent.addChildren(i, typedReplacements);
          i += replacements.get().size() - 1;
          replacements = Optional.empty();
        }
      }
      checkState(!replacements.isPresent());
    }
  }
}
