/*
 * Copyright 2010 Google Inc.
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

package com.google.template.soy.parsepasses.contextautoesc;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.parsepasses.contextautoesc.Context.AttributeEndDelimiter;
import com.google.template.soy.parsepasses.contextautoesc.Context.HtmlHtmlAttributePosition;
import com.google.template.soy.parsepasses.contextautoesc.Context.UriPart;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.EscapingMode;
import com.google.template.soy.soytree.ForIfemptyNode;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForNonemptyNode;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.HtmlAttributeValueNode;
import com.google.template.soy.soytree.HtmlCloseTagNode;
import com.google.template.soy.soytree.HtmlCommentNode;
import com.google.template.soy.soytree.HtmlContext;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.HtmlTagNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.Kind;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.RenderUnitNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.types.TemplateType;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * Chooses appropriate escaping modes for <code>{print}</code> commands and derives templates as
 * necessary.
 *
 * <p>For each template with {@code autoescape="contextual"}, assume that the template is used to
 * produce an HTML fragment. Start walking the body with the {@link Context context} provided by the
 * caller (typically {@link HtmlContext#HTML_PCDATA}).
 *
 * <ul>
 *   <li>For RawTextNodes, update the context based on the fragment, so seeing "&lt;script&gt;" will
 *       move us into a JavaScript context while "&lt;!--" would move us into an HTML comment
 *       context.
 *   <li>For {@link PrintNode}s, choose an escaping convention appropriate to the current context.
 *   <li>For {@link IfNode}s, {@link SwitchNode}s, and looping constructs, propagate context
 *       separately along each path, and make sure they converge on a consistent context.
 *   <li>For {@link CallBasicNode}s, maybe derive the target based on current context, recursively
 *       propagate contexts through the derived template to compute an end context for the template.
 *       See fixed-point typing below for a discussion of reentrant templates and templates used in
 *       different contexts.
 * </ul>
 *
 */
final class InferenceEngine {

  /**
   * Infer an end context for the given template and, if requested, choose escaping directives for
   * any <code>{print}</code>.
   *
   * @param templateNode A template that is visited in {@code startContext} and no other. If a
   *     template can be reached from multiple contexts, then it should be cloned. This class
   *     automatically does that for called templates.
   * @param inferences Receives all suggested changes and inferences to tn.
   * @return The end context when the given template is reached from {@code startContext}.
   */
  public static Context inferTemplateEndContext(
      TemplateNode templateNode,
      Context startContext,
      Inferences inferences,
      ErrorReporter errorReporter) {
    InferenceEngine inferenceEngine = new InferenceEngine(inferences, errorReporter);
    // Context started off as startContext and we have propagated context through all of
    // template's children, so now return the template's end context.
    return inferenceEngine.infer(templateNode, startContext);
  }

  /**
   * Checks that the end context of a block is compatible with its start context.
   *
   * @throws SoyAutoescapeException if they mismatch.
   */
  private static void checkBlockEndContext(RenderUnitNode node, Context endContext) {
    if (!endContext.isValidEndContextForContentKind(node.getContentKind())) {
      String msg =
          String.format(
              "A block of kind=\"%s\" cannot end in context %s. Likely cause is %s.",
              node.getContentKind().asAttributeValue(),
              endContext,
              endContext.getLikelyEndContextMismatchCause(node.getContentKind()));
      throw SoyAutoescapeException.createWithNode(msg, node);
    }
  }

  /**
   * Applies strict contextual autoescaping to the given node's children.
   *
   * <p>The start context is the given node's declared {@link ContentKind}, and it is enforced that
   * the block's inferred end context matches the start context.
   *
   * <p>This method is used to visit the content of {let} and {param} nodes with a {@code kind}
   * attribute.
   */
  static void inferStrictRenderUnitNode(
      RenderUnitNode node, Inferences inferences, ErrorReporter errorReporter) {
    InferenceEngine inferenceEngine = new InferenceEngine(inferences, errorReporter);
    // Context started off as startContext and we have propagated context through all of
    // node's children, so now context is the node's end context.
    Context endContext =
        inferenceEngine.inferChildren(
            node, Context.getStartContextForContentKind(node.getContentKind()));
    // Checking that start and end context is same.
    checkBlockEndContext(node, endContext);
  }

  /** Receives modifications and typing inferences. */
  private final Inferences inferences;

  /** For reporting errors. */
  private final ErrorReporter errorReporter;

  private InferenceEngine(Inferences inferences, ErrorReporter errorReporter) {
    this.inferences = inferences;
    this.errorReporter = errorReporter;
  }

  private Context infer(SoyNode node, Context context) {
    return new ContextPropagatingVisitor(context).exec(node);
  }

  private Context inferChildren(SoyNode node, Context context) {
    ContextPropagatingVisitor contextPropagatingVisitor = new ContextPropagatingVisitor(context);
    return contextPropagatingVisitor.execChildren(node);
  }

  /**
   * A visitor that propagates context across a Soy AST to determine its end context. The end
   * context of an AST is the one that would be reached by applying the {@link
   * RawTextContextUpdater}'s HTML/CSS/JS grammar to any output of the template (where print
   * commands produce innocuous strings). An innocuous string is one that is non-empty and that
   * contains no special characters in HTML/CSS/JS. The string 'z' is a good example of an innocuous
   * string.
   */
  private final class ContextPropagatingVisitor extends AbstractSoyNodeVisitor<Context> {

    private Context context;

    private RawTextNode uriStart = null;

    public ContextPropagatingVisitor(Context context) {
      this.context = context;
    }

    @Override
    public Context exec(SoyNode node) {
      visit(node);
      return context;
    }

    /** Like {@link #exec(SoyNode)}, but only visits the current node's children, if any. */
    public Context execChildren(SoyNode node) {
      if (node instanceof ParentSoyNode<?>) {
        visitChildren((ParentSoyNode<?>) node);
      }
      return context;
    }

    @Override
    protected void visitTemplateNode(TemplateNode templateNode) {
      Preconditions.checkState(
          context.isValidStartContextForContentKind(templateNode.getContentKind()),
          "Templates may only be visited in the context for their declared content kind.");
      // Normalize to the canonical context, even if we started in a similar but allowable
      // context (e.g.  single versus double quotes).
      context = Context.getStartContextForContentKind(templateNode.getContentKind());
      visitChildren(templateNode);
      checkBlockEndContext(templateNode, context);
    }

    /** Propagates context across raw chunks of HTML text. */
    @Override
    protected void visitRawTextNode(RawTextNode rawTextNode) {
      context = RawTextContextUpdater.processRawText(rawTextNode, context);
      if (context.uriPart() == UriPart.TRUSTED_RESOURCE_URI_END) {
        uriStart = rawTextNode;
      }
      rawTextNode.setHtmlContext(context.state());
    }

    @Override
    protected void visitMsgNode(MsgNode node) {
      node.setEscapingMode(context.state().getEscapingMode());
      super.visitMsgNode(node);
    }

    @Override
    protected void visitMsgFallbackGroupNode(MsgFallbackGroupNode node) {
      checkUriEnd();
      node.setHtmlContext(context.state());

      // (1) Determine the escaping we should do on the node itself, and the context we should
      // parse the children in.
      Optional<Context.MsgEscapingStrategy> maybeStrategy = context.getMsgEscapingStrategy(node);
      if (!maybeStrategy.isPresent()) {
        throw SoyAutoescapeException.createWithNode(
            "Messages are not supported in this context, because it would mean asking "
                + "translators to write source code; if this is desired, try factoring the "
                + "message into a {let} block: "
                + context,
            node);
      } else if (context.delimType() == AttributeEndDelimiter.SPACE_OR_TAG_END) {
        throw SoyAutoescapeException.createWithNode(
            "Messages are not supported in this context because a space in the translation would "
                + "end the attribute value. Wrap the attribute value into quotes.",
            node);
      }
      Context.MsgEscapingStrategy strategy = maybeStrategy.get();
      inferences.setEscapingDirectives(node, context, strategy.escapingModesForFullMessage);

      // (2) Run the inference engine on the parts of the message in that context.
      Context msgEndContext =
          new InferenceEngine(inferences, errorReporter).inferChildren(node, strategy.childContext);

      // (3) Make sure the message didn't itself change context.
      if (!msgEndContext.equals(strategy.childContext)) {
        throw SoyAutoescapeException.createWithNode(
            "Message text should not alter the escaping context. "
                + context
                + " != "
                + strategy.childContext,
            node);
      }
    }

    /**
     * Derive a template from the given call's target if necessary, and figure out the template's
     * end context.
     */
    @Override
    protected void visitCallNode(CallNode callNode) {
      checkUriEnd();
      checkHtmlHtmlAttributePosition(callNode);

      callNode.setHtmlContext(context.state());

      context = inferCallSite(callNode, context, inferences);

      visitChildren(callNode);
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
      inferInStrictMode(node);
    }

    @Override
    protected void visitIfNode(IfNode ifNode) {
      ifNode.setHtmlContext(context.state());
      propagateAcrossDisjunction(ifNode);
    }

    @Override
    protected void visitSwitchNode(SwitchNode switchNode) {
      propagateAcrossDisjunction(switchNode);
    }

    /**
     * Do multiple inferences so we can make sure we get to a consistent context regardless of how
     * many times the loop is entered.
     */
    @Override
    protected void visitForNode(ForNode forNode) {
      List<BlockNode> foreachChildren = forNode.getChildren();
      ForNonemptyNode neNode = (ForNonemptyNode) foreachChildren.get(0);
      ForIfemptyNode ieNode;
      if (foreachChildren.size() == 2) {
        ieNode = (ForIfemptyNode) foreachChildren.get(1);
      } else if (foreachChildren.size() == 1) {
        ieNode = null;
      } else {
        throw new AssertionError();
      }
      Context afterBody = context;
      if (neNode != null) {
        afterBody = infer(neNode, context);
        // Make sure that repeated invocations of the body end up in the same state.
        Context elseContext = infer(neNode, afterBody);
        Optional<Context> combined = Context.union(elseContext, afterBody);
        if (!combined.isPresent()) {
          throw SoyAutoescapeException.createWithNode(
              "{"
                  + forNode.getCommandName()
                  + "} body does not end in the same context after repeated entries.",
              forNode);
        }
        afterBody = combined.get();
      }
      Context ifemptyContext;
      if (ieNode != null) {
        ifemptyContext = infer(ieNode, context);
      } else {
        ifemptyContext = context;
      }
      Optional<Context> combined = Context.union(ifemptyContext, afterBody);
      if (!combined.isPresent()) {
        throw SoyAutoescapeException.createWithNode(
            "{"
                + forNode.getCommandName()
                + "} body "
                + (ieNode == null
                    ? "changes context."
                    : "does not end in the same context as {ifempty}."),
            ieNode == null ? forNode : ieNode);
      }
      context = combined.get();
    }

    /**
     * Pick an escaping mode for the print node if this is in an {@code autoescape="contextual"}
     * template.
     */
    @Override
    protected void visitPrintNode(PrintNode printNode) {
      printNode.setHtmlContext(context.state());
      checkUriEnd();
      checkHtmlHtmlAttributePosition(printNode);

      List<EscapingMode> escapingModes = inferences.getEscapingMode(printNode);
      Context prev = context;
      if (escapingModes.isEmpty()) { // None specified.
        // The inferences set below specify which nodes to change. In the non-contextual modes,
        // we leave escapingModesToSet null since no changes are to be made to this print node.
        List<EscapingMode> escapingModesToSet = null;
        // Infer one.
        escapingModes =
            escapingModesToSet = context.getEscapingModes(printNode, printNode.getChildren());
        inferences.setEscapingDirectives(printNode, prev, escapingModesToSet);
      } else if (!context.isCompatibleWith(escapingModes.get(0))) {
        String msg =
            String.format("Escaping modes %s not compatible with %s.", escapingModes, context);
        throw SoyAutoescapeException.createWithNode(msg, printNode);
      }

      // Figure out the context at the end.
      context = context.getContextAfterDynamicValue();
    }

    private void checkUriEnd() {
      if (context.uriPart() == UriPart.TRUSTED_RESOURCE_URI_END) {
        throw SoyAutoescapeException.createWithNode(
            "TrustedResourceUris containing dynamic content must have a fixed scheme (https) and "
                + "host using one of the following formats:\n"
                + "  * https://foo/\n" // NOTYPO
                + "  * //foo/\n"
                + "  * /foo\n"
                + "or move the calculation of this URL outside of the template and use an "
                + "ordaining API.",
            // We switch to UriPart.TRUSTED_RESOURCE_URI_END in RawTextNode where we also store
            // uriStart.
            uriStart);
      }
    }

    private void checkHtmlHtmlAttributePosition(SoyNode node) {
      if (context.htmlHtmlAttributePosition() == HtmlHtmlAttributePosition.NOT_START) {
        throw SoyAutoescapeException.createWithNode(
            "HTML attribute values containing HTML can use dynamic expressions only at the start "
                + "of the value.",
            node);
      }
    }

    @Override
    protected void visitHtmlOpenTagNode(HtmlOpenTagNode node) {
      visitHtmlTagNode(node);
    }

    @Override
    protected void visitHtmlCloseTagNode(HtmlCloseTagNode node) {
      if (!node.getTagName().isWildCard()) {
        visitHtmlTagNode(node);
      }
    }

    @Override
    protected void visitHtmlCommentNode(HtmlCommentNode node) {
      context = context.transitionToState(HtmlContext.HTML_COMMENT);
      visitChildren(node);
      context = context.transitionToState(HtmlContext.HTML_PCDATA);
    }

    private void visitHtmlTagNode(HtmlTagNode tag) {
      context =
          context.transitionToState(
              tag.getKind() == Kind.HTML_OPEN_TAG_NODE
                  ? HtmlContext.HTML_BEFORE_OPEN_TAG_NAME
                  : HtmlContext.HTML_BEFORE_CLOSE_TAG_NAME);
      // if the tag name is a constant, transition to an appropriate tag state
      if (tag.getTagName().isStatic()) {
        context = context.transitionToTagName(tag);
        ((RawTextNode) tag.getChild(0)).setHtmlContext(HtmlContext.HTML_TAG_NAME);
      } else {
        // dynamic tag name
        visit(tag.getChild(0));
      }
      // Make sure the element type was pre-determined when setting the tag name.
      Preconditions.checkArgument(context.elType() != Context.ElementType.NONE);
      context = context.transitionToTagBody();
      // 0 is the tag name
      for (int i = 1; i < tag.numChildren(); i++) {
        visit(tag.getChild(i));
      }
      context = context.transitionToAfterTag();
    }

    @Override
    protected void visitHtmlAttributeNode(HtmlAttributeNode node) {
      SoyNode first = node.getChild(0);
      if (first.getKind() == SoyNode.Kind.RAW_TEXT_NODE) {
        context = context.transitionToAttrName(((RawTextNode) first).getRawText());
        ((RawTextNode) first).setHtmlContext(HtmlContext.HTML_ATTRIBUTE_NAME);
      } else {
        visit(first);
      }
      if (node.hasValue()) {
        visit(node.getChild(1));
      }
      context = context.transitionToTagBody();
    }

    @Override
    protected void visitHtmlAttributeValueNode(HtmlAttributeValueNode node) {
      Context.AttributeEndDelimiter delim;
      switch (node.getQuotes()) {
        case DOUBLE:
          delim = Context.AttributeEndDelimiter.DOUBLE_QUOTE;
          break;
        case NONE:
          delim = Context.AttributeEndDelimiter.SPACE_OR_TAG_END;
          break;
        case SINGLE:
          delim = Context.AttributeEndDelimiter.SINGLE_QUOTE;
          break;
        default:
          throw new AssertionError();
      }
      context = context.transitionToAttrValue(delim);
      visitChildren(node);
      context = context.transitionToTagBody();
    }

    /** Handle conjunction nodes. */
    @Override
    protected void visitSoyNode(SoyNode node) {
      if (node instanceof ParentSoyNode<?>) {
        visitChildren((ParentSoyNode<?>) node);
      }
    }

    //
    // Helper methods.

    /**
     * Determines the content kind of the templates.
     *
     * <p>This relies on CheckDelegatesPass to print friendly messages if the deltemplates differ in
     * content kind.
     */
    private SanitizedContentKind getCommonContentKindIfStrict(List<TemplateType> templates) {
      if (templates.isEmpty()) {
        return null;
      }
      SanitizedContentKind contentKind = templates.get(0).getContentKind();
      for (TemplateType template : templates) {
        Preconditions.checkArgument(template.getContentKind() == contentKind);
      }
      return contentKind;
    }

    /**
     * Derives a template if necessary to compute a consistent end context for a call to the named
     * template.
     *
     * @param callNode The call node.
     * @param startContext The context before the call.
     * @param inferences Contains a mapping of templates visible to the call site, prior typing
     *     decisions, and derived templates. Will receive any templates successfully derived as a
     *     side-effect of this call.
     */
    private Context inferCallSite(CallNode callNode, Context startContext, Inferences inferences) {
      List<TemplateType> targets = inferences.lookupTemplates(callNode);
      SanitizedContentKind calleeStrictContentKind = getCommonContentKindIfStrict(targets);
      // Check what kind of template is being called.
      if (calleeStrictContentKind != null
          && startContext.isValidStartContextForContentKind(calleeStrictContentKind)) {
        // As an optimization, don't escape the call site if the callee has the right content
        // kind. Since all deltemplates with the same name must be of the same kind (checked
        // elsewhere), we can make this optimization even if we can't see all the deltemplates.
        return startContext.getContextAfterDynamicValue();
      } else {
        // If a strict template calls another strict template (or an unknown extern), the result
        // will be escaped, so the call statement behaves effectively like a print statement.
        // No re-contextualization of the callee is done.
        // TODO(gboyer): Throw an exception if the list of escaping modes is empty, which
        // indicates that there's no valid escaper for this context. My plan is to actually have
        // getEscapingModes() itself throw the exception, but this requires some weeding out of
        // bad existing templates.
        inferences.setEscapingDirectives(
            callNode, startContext, startContext.getEscapingModes(callNode, ImmutableList.of()));
        return startContext.getContextAfterDynamicValue();
      }
    }

    /** Consider the various branches separately and compute a union context for each branch. */
    private void propagateAcrossDisjunction(ParentSoyNode<?> node) {
      // All the branches of an {if} or {switch} should return compatible contexts, so that we can
      // figure out the end context of the branch as a whole.
      Iterator<? extends SoyNode> childIt = node.getChildren().iterator();
      SoyNode firstBranch = childIt.next();
      Context out = infer(firstBranch, context);
      boolean sawElseOrDefault = false;
      while (childIt.hasNext()) {
        SoyNode branch = childIt.next();
        Context brOut = infer(branch, context);
        Optional<Context> combined = Context.union(out, brOut);
        if (!combined.isPresent()) {
          throw SoyAutoescapeException.createWithNode(
              (node instanceof IfNode
                      ? "{if} command branch ends in a different context than preceding branches:"
                      : "{switch} command case ends in a different context than preceding cases:")
                  + " "
                  + branch.toSourceString(),
              branch);
        }
        out = combined.get();
        if (branch instanceof IfElseNode || branch instanceof SwitchDefaultNode) {
          sawElseOrDefault = true;
        }
      }

      // If there is no else or default, then the end context has to be the compatible with the
      // start context.
      if (!sawElseOrDefault) {
        Optional<Context> combined = Context.union(context, out);
        if (!combined.isPresent()) {
          throw SoyAutoescapeException.createWithNode(
              (node instanceof IfNode
                  ? "{if} command without {else} changes context."
                  : "{switch} command without {default} changes context."),
              node);
        }
        out = combined.get();
      }

      context = out;
    }

    private void inferInStrictMode(RenderUnitNode node) {
      inferStrictRenderUnitNode(node, inferences, errorReporter);
    }
  }

  //
  // Static helper methods (cannot be part of inner class).

  @AutoValue
  abstract static class DerivedNameAndContext {
    static DerivedNameAndContext create(String derivedName, Context context) {
      return new AutoValue_InferenceEngine_DerivedNameAndContext(derivedName, context);
    }

    abstract String derivedName();

    abstract Context context();
  }

  @AutoValue
  abstract static class InferencesAndContext {
    static InferencesAndContext create(Inferences inferences, Context context) {
      return new AutoValue_InferenceEngine_InferencesAndContext(inferences, context);
    }

    abstract Inferences inferences();

    abstract Context context();
  }
}
