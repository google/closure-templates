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
import com.google.common.base.MoreObjects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.parsepasses.contextautoesc.Context.HtmlHtmlAttributePosition;
import com.google.template.soy.parsepasses.contextautoesc.Context.UriPart;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.AutoescapeMode;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallDelegateNode;
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
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.CommandNode;
import com.google.template.soy.soytree.SoyNode.Kind;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.RenderUnitNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TemplateMetadata;
import com.google.template.soy.soytree.TemplateNode;
import java.util.Iterator;
import java.util.List;

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
    AutoescapeMode autoescapeMode = templateNode.getAutoescapeMode();
    InferenceEngine inferenceEngine =
        new InferenceEngine(autoescapeMode, autoescapeMode, inferences, errorReporter);
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
    if (!endContext.isValidEndContextForContentKind(
        MoreObjects.firstNonNull(node.getContentKind(), SanitizedContentKind.HTML))) {
      String msg;
      if (node.getContentKind() == null) {
        msg =
            String.format(
                "A deprecated-contextual block cannot end in context %s. Likely cause is %s.",
                endContext, endContext.getLikelyEndContextMismatchCause(SanitizedContentKind.HTML));
      } else {
        msg =
            String.format(
                "A strict block of kind=\"%s\" cannot end in context %s. Likely cause is %s.",
                node.getContentKind().asAttributeValue(),
                endContext,
                endContext.getLikelyEndContextMismatchCause(node.getContentKind()));
      }
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
      AutoescapeMode templateAutoescapeMode,
      RenderUnitNode node,
      Inferences inferences,
      ErrorReporter errorReporter) {
    InferenceEngine inferenceEngine =
        new InferenceEngine(
            AutoescapeMode.STRICT,
            templateAutoescapeMode,
            inferences,
            errorReporter);
    // Context started off as startContext and we have propagated context through all of
    // node's children, so now context is the node's end context.
    Context endContext =
        inferenceEngine.inferChildren(
            node, Context.getStartContextForContentKind(node.getContentKind()));
    // Checking that start and end context is same.
    checkBlockEndContext(node, endContext);
  }

  /** The autoescaping mode in this current context. */
  private final AutoescapeMode autoescapeMode;

  /** The autoescape mode of the surrounding {template}. */
  private final AutoescapeMode templateAutoescapeMode;

  /** Receives modifications and typing inferences. */
  private final Inferences inferences;

  /** For reporting errors. */
  private final ErrorReporter errorReporter;

  private InferenceEngine(
      AutoescapeMode autoescapeMode,
      AutoescapeMode templateAutoescapeMode,
      Inferences inferences,
      ErrorReporter errorReporter) {
    this.autoescapeMode = autoescapeMode;
    this.templateAutoescapeMode = templateAutoescapeMode;
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
          templateNode.getAutoescapeMode() == autoescapeMode,
          "Same ContextPropagatingVisitor cannot be reused for multiple escaping modes.");
      if (autoescapeMode == AutoescapeMode.STRICT) {
        Preconditions.checkState(
            context.isValidStartContextForContentKind(templateNode.getContentKind()),
            "Strict templates may only be visited in the context for their declared content kind.");
        // Normalize to the canonical context, even if we started in a similar but allowable
        // context (e.g.  single versus double quotes).
        context = Context.getStartContextForContentKind(templateNode.getContentKind());
      }
      visitChildren(templateNode);
      checkBlockEndContext(templateNode, context);
    }

    /** Propagates context across raw chunks of HTML text. */
    @Override
    protected void visitRawTextNode(RawTextNode rawTextNode) {
      context = RawTextContextUpdater.processRawText(rawTextNode, context);
      if (context.uriPart == UriPart.TRUSTED_RESOURCE_URI_END) {
        uriStart = rawTextNode;
      }
    }

    @Override
    protected void visitMsgFallbackGroupNode(MsgFallbackGroupNode node) {
      checkUriEnd();

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
      }
      Context.MsgEscapingStrategy strategy = maybeStrategy.get();
      inferences.setEscapingDirectives(node, context, strategy.escapingModesForFullMessage);

      // (2) Run the inference engine on the parts of the message in that context.
      Context msgEndContext =
          new InferenceEngine(autoescapeMode, templateAutoescapeMode, inferences, errorReporter)
              .inferChildren(node, strategy.childContext);

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
     * {@link DerivedTemplateUtils Derive} a template from the given call's target if necessary, and
     * figure out the template's end context.
     */
    @Override
    protected void visitCallNode(CallNode callNode) {
      checkUriEnd();
      checkHtmlHtmlAttributePosition(callNode);

      callNode.setHtmlContext(context.state);

      String calleeName;
      if (callNode instanceof CallBasicNode) {
        calleeName = ((CallBasicNode) callNode).getCalleeName();
      } else {
        calleeName = ((CallDelegateNode) callNode).getDelCalleeName();
      }

      context = inferCallSite(callNode, context, calleeName, inferences);

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
      switch (autoescapeMode) {
        case CONTEXTUAL:
          // if there is a kind and we are contextual, respect it, otherwise, html it is!
          if (node.getContentKind() == null) {
            inferInContextualModeForHtml(node);
          } else {
            inferInStrictMode(node);
          }
          break;
        case STRICT:
          // The CheckEscapingSanityVisitor ensures that node.getContentKind is non-null
          inferInStrictMode(node);
          break;
      }
    }

    @Override
    protected void visitIfNode(IfNode ifNode) {
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
      printNode.setHtmlContext(context.state);
      // It is an error to use autoescape-canceling print directives in strict mode unless in a
      // block of kind text.
      if (autoescapeMode == AutoescapeMode.STRICT && context.state != HtmlContext.TEXT) {
        for (PrintDirectiveNode printDirective : printNode.getChildren()) {
          if (printDirective.getName().equals("|noAutoescape")) {
            // Treat noAutoescape specially:
            // - It is allowed in strict sub-contexts if the surrounding template is non-strict,
            // to help with migration. This does not apply to other escaping directives since
            // they are just as dangerous, but less obvious to auditors.
            if (templateAutoescapeMode == AutoescapeMode.STRICT) {
              // Help the user figure out the best content kind to use, using existing heuristics.
              SanitizedContentKind recommendedKind = context.getMostAppropriateContentKind();
              String recommendedKindStr =
                  (recommendedKind == SanitizedContentKind.TEXT)
                      ? "appropriate kind=\"...\""
                      : ("kind=\"" + recommendedKind.asAttributeValue() + "\"");
              throw SoyAutoescapeException.createWithNode(
                  "noAutoescape is not allowed in strict autoescaping mode. Instead, pass in a "
                      + "{param} with "
                      + recommendedKindStr
                      + " or SanitizedContent.",
                  printNode);
            }
          }
        }
      }

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
      if (context.uriPart == UriPart.TRUSTED_RESOURCE_URI_END) {
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
      if (context.htmlHtmlAttributePosition == HtmlHtmlAttributePosition.NOT_START) {
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
      visitHtmlTagNode(node);
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
      } else {
        // dynamic tag name
        visit(tag.getChild(0));
      }
      // Make sure the element type was pre-determined when setting the tag name.
      Preconditions.checkArgument(context.elType != Context.ElementType.NONE);
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
    private SanitizedContentKind getCommonContentKindIfStrict(List<TemplateMetadata> templates) {
      if (templates.isEmpty()) {
        return null;
      }
      SanitizedContentKind contentKind = templates.get(0).getContentKind();
      for (TemplateMetadata template : templates) {
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
     * @param templateName The name of the template being called.
     * @param inferences Contains a mapping of templates visible to the call site, prior typing
     *     decisions, and derived templates. Will receive any templates successfully derived as a
     *     side-effect of this call.
     */
    private Context inferCallSite(
        CallNode callNode, Context startContext, String templateName, Inferences inferences) {
      List<TemplateMetadata> targets = inferences.lookupTemplates(callNode);
      SanitizedContentKind calleeStrictContentKind = getCommonContentKindIfStrict(targets);
      if (autoescapeMode == AutoescapeMode.STRICT) {
        // We're currently in a strict mode template. Check what kind of template is being called.
        if (calleeStrictContentKind != null
            && startContext.isValidStartContextForContentKind(calleeStrictContentKind)) {
          // As an optimization, don't escape the call site if the callee has the right content
          // kind. Since all deltemplates with the same name must be of the same kind (checked
          // elsewhere), we can make this optimization even if we can't see all the deltemplates.
          return startContext.getContextAfterDynamicValue();
        } else if (calleeStrictContentKind != null || targets.isEmpty()) {
          // If a strict template calls another strict template (or an unknown extern), the result
          // will be escaped, so the call statement behaves effectively like a print statement.
          // No re-contextualization of the callee is done.
          // TODO(gboyer): Throw an exception if the list of escaping modes is empty, which
          // indicates that there's no valid escaper for this context. My plan is to actually have
          // getEscapingModes() itself throw the exception, but this requires some weeding out of
          // bad existing templates.
          inferences.setEscapingDirectives(
              callNode,
              startContext,
              startContext.getEscapingModes(callNode, ImmutableList.<PrintDirectiveNode>of()));
          return startContext.getContextAfterDynamicValue();
        } else {
          throw SoyAutoescapeException.createWithNode(
              "Soy strict autoescaping currently forbids calls to non-strict templates. "
                  + "Please migrate the callee to strict.",
              callNode);
        }

      } else {
        // In a non-strict mode template.
        if (targets.isEmpty()) {
          // External template not visible to compiler -- let's pray for the best! We might end up
          // calling a Javascript-escaping template from HTML or vice versa.
          // TODO(lukes): this should be getContextAfterDynamicValue
          return startContext;
        } else if (calleeStrictContentKind != null) {
          // Non-strict templates may call strict templates, but only if the context is a match.
          // NOTE: While contextual templates *might* do escaping like strict in this context, it
          // would silently break if the template is compiled as an extern. By having this check,
          // teams can do a single monolithic compilation for error checking to prevent this.
          // We're a little loose in this check to allow calling URI templates within URI
          // attributes, even though it's not technically valid HTML, in order to help migration.
          if (!startContext.isValidStartContextForContentKindLoose(calleeStrictContentKind)) {
            String msg =
                String.format(
                    "Cannot call strictly autoescaped template %s of kind=\"%s\" from "
                        + "incompatible context %s. Strict templates generate extra code to safely "
                        + "call templates of other content kinds, but non-strict templates do not.",
                    templateName, calleeStrictContentKind.asAttributeValue(), startContext);
            throw SoyAutoescapeException.createWithNode(msg, callNode);
          }
          // TODO(lukes): this should be getContextAfterDynamicValue
          return startContext;
        } else {
          if (!startContext.equals(Context.HTML_PCDATA)) {
            throw SoyAutoescapeException.createWithNode(
                "Attempting to call non-strict template '"
                    + templateName
                    + "' from a non-strict template in context '"
                    + startContext.state
                    + "'. This is no longer supported."
                    + " Please migrate to strict autoescaping.",
                callNode);
          }
          // This is the correct answer because
          // 1. we are starting in HTML_PCDATA, per the previous check
          // 2. the only valid end-context for HTML_PCDATA is HTML_PCDATA
          // 3. All deprecated-contextual templates are required to start and end in this context
          //    due to the checks in visitTemplateNode above.
          // So we don't actually have to look at the callees
          return Context.HTML_PCDATA;
        }
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
      inferStrictRenderUnitNode(
          templateAutoescapeMode,
          node,
          inferences,
          errorReporter);
    }

    /** Applies HTML contextual autoescaping on a legacy contextual parameter block. */
    private void inferInContextualModeForHtml(CommandNode node) {
      // NOTE: Previously this wouldn't do any contextual analysis, which resulted in subtle bugs
      // such as the contextual autoescaper not seeing typed parameters in nested calls.
      final Context paramContentNodeEndContext =
          new InferenceEngine(
                  AutoescapeMode.CONTEXTUAL,
                  templateAutoescapeMode,
                  inferences,
                  errorReporter)
              .inferChildren(node, Context.HTML_PCDATA);
      if (!paramContentNodeEndContext.equals(Context.HTML_PCDATA)) {
        throw SoyAutoescapeException.createWithNode(
            "Blocks should start and end in HTML context.", node);
      }
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
