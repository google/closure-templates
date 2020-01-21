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
import com.google.template.soy.base.internal.QuoteStyle;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
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
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SkipNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.RenderUnitNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.types.StringType;
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
  public Result run(
      ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator, TemplateRegistry registry) {
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
     * necessary if there is an unquoted attribute as the final attribute.
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
    protected void visitSkipNode(SkipNode skipNode) {
      HtmlOpenTagNode openTag = (HtmlOpenTagNode) skipNode.getParent();
      ImmutableList.Builder<StandaloneNode> builder = ImmutableList.builder();
      // {skip} + {key} nodes are turned into soy-server-key="{$key}". For more information why,
      // see go/typed-html-templates. For Incremental DOM, these are handled in
      // GenIncrementalDomCodeVisitor.
      // Note: when users do not use their own key, the soy-server-key looks like
      // "soy-server-key="{soyServerKey(xid('template'-0))}. When users use their own key, we just
      // use their key verbatim.
      FunctionNode wrappedFn =
          new FunctionNode(
              Identifier.create(
                  BuiltinFunction.SOY_SERVER_KEY.getName(), openTag.getSourceLocation()),
              BuiltinFunction.SOY_SERVER_KEY,
              openTag.getSourceLocation());
      wrappedFn.setType(StringType.getInstance());

      if (openTag.getKeyNode() == null) {
        FunctionNode funcNode =
            new FunctionNode(
                Identifier.create(BuiltinFunction.XID.getName(), openTag.getSourceLocation()),
                BuiltinFunction.XID,
                openTag.getSourceLocation());
        funcNode.addChild(
            new StringNode(skipNode.getSkipId(), QuoteStyle.SINGLE, openTag.getSourceLocation()));
        funcNode.setType(StringType.getInstance());
        wrappedFn.addChild(funcNode);
      } else {
        wrappedFn.addChild(openTag.getKeyNode().getExpr().getRoot().copy(new CopyState()));
      }

      builder
          .add(
              new RawTextNode(idGenerator.genId(), " soy-server-key=", openTag.getSourceLocation()))
          .add(createPrefix("'", skipNode))
          .add(
              new PrintNode(
                  idGenerator.genId(),
                  openTag.getSourceLocation(),
                  /* isImplicit= */ true,
                  /* expr= */ wrappedFn,
                  /* attributes= */ ImmutableList.of(),
                  ErrorReporter.exploding()))
          .add(createSuffix("'", skipNode));
      replacements = Optional.of(builder.build());
    }

    @Override
    protected void visitHtmlOpenTagNode(HtmlOpenTagNode node) {
      visitHtmlTagNode(node);
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

      // prefix the value with a single whitespace character.  This makes it unambiguous with the
      // preceding attribute/tag name.
      // There are some cases where we don't need this:
      // 1. if the attribute children don't render anything, e.g. {$foo ?: ''}
      //    -This would only be fixable by modifying the code generators to dynamically insert the
      //     space character
      // 2. if the preceding node is a quoted attribute value
      //    -This would always work, but is technically out of spec so we should probably avoid it.
      ImmutableList.Builder<StandaloneNode> builder = ImmutableList.builder();
      if (needsSpaceForAttribute) {
        // TODO(lukes): in this case, if the attribute is dynamic and ultimately renders the
        // empty string, we will render an extra space.
        builder.add(
            new RawTextNode(idGenerator.genId(), " ", node.getSourceLocation().getBeginLocation()));
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
