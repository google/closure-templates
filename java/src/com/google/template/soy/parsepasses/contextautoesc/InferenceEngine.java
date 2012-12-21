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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.internalutils.NodeContentKinds;
import com.google.template.soy.internal.base.Pair;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.AutoescapeMode;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CssNode;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForeachIfemptyNode;
import com.google.template.soy.soytree.ForeachNode;
import com.google.template.soy.soytree.ForeachNonemptyNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.CommandNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.RenderUnitNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TemplateNode;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;


/**
 * Chooses appropriate escaping modes for <code>{print}</code> commands and derives templates as
 * necessary.
 * <p>
 * For each template with {@code autoescape="contextual"}, assume that the template is used
 * to produce an HTML fragment.
 * Start walking the body with the {@link Context context} provided by the caller (typically
 * {@link Context.State#HTML_PCDATA}).
 * <ul>
 *   <li>For RawTextNodes, update the context based on the fragment, so seeing "&lt;script&gt;" will
 *   move us into a JavaScript context while "&lt;!--" would move us into an HTML comment context.
 *   <li>For {@link PrintNode}s, choose an escaping convention appropriate to the current context.
 *   <li>For {@link IfNode}s, {@link SwitchNode}s, and looping constructs, propagate context
 *   separately along each path, and make sure they converge on a consistent context.
 *   <li>For {@link CallBasicNode}s, maybe derive the target based on current context, recursively
 *   propagate contexts through the derived template to compute an end context for the template.
 *   See fixed-point typing below for a discussion of reentrant templates and templates used in
 *   different contexts.
 * </ul>
 *
 * @author Mike Samuel
 */
final class InferenceEngine {

  /**
   * Infer an end context for the given template and, if requested, choose escaping directives for
   * any <code>{print}</code>.
   *
   * @param templateNode A template that is visited in {@code startContext} and no other.
   *     If a template can be reached from multiple contexts, then it should be cloned.
   *     This class automatically does that for called templates.
   * @param inferences Receives all suggested changes and inferences to tn.
   * @param autoescapeCancellingDirectives Soy directives that cancel autoescaping (see
   *     {@link com.google.template.soy.shared.restricted.SoyPrintDirective#shouldCancelAutoescape()}).
   * @return The end context when the given template is reached from {@code startContext}.
   */
  public static Context inferTemplateEndContext(
      TemplateNode templateNode, Context startContext, Inferences inferences,
      Set<String> autoescapeCancellingDirectives)
      throws SoyAutoescapeException {
    Context endContext;
    try {
      Context context = startContext;
      AutoescapeMode autoescapeMode = templateNode.getAutoescapeMode();
      context = new InferenceEngine(autoescapeMode, inferences, autoescapeCancellingDirectives)
          .infer(templateNode, context);
      // Context started off as startContext and we have propagated context through all of
      // template's children, so now context is the template's end context.
      endContext = context;
      inferences.recordTemplateEndContext(templateNode.getTemplateName(), endContext);
    } catch (SoyAutoescapeException e) {
      throw e.maybeAssociateNode(templateNode);
    }
    return endContext;
  }

  /** True if the inference engine is allowed to add escaping directives for this template. */
  private final AutoescapeMode autoescapeMode;

  /** Receives modifications and typing inferences. */
  private final Inferences inferences;

  /** The escaping mode to assume when none is specified. */
  private final @Nullable EscapingMode defaultEscapingMode;

  /**
   * Soy directives that cancel autoescaping (see
   * {@link com.google.template.soy.shared.restricted.SoyPrintDirective#shouldCancelAutoescape()}).
   */
  private final Set<String> autoescapeCancellingDirectives;

  private InferenceEngine(
      AutoescapeMode autoescapeMode, Inferences inferences,
      Set<String> autoescapeCancellingDirectives) {
    this.autoescapeMode = autoescapeMode;
    this.inferences = inferences;
    this.autoescapeCancellingDirectives = autoescapeCancellingDirectives;
    this.defaultEscapingMode = (autoescapeMode != AutoescapeMode.FALSE) ?
        EscapingMode.ESCAPE_HTML : null;
  }

  private Context infer(SoyNode node, Context context) {
    return new ContextPropagatingVisitor(context).exec(node);
  }

  private Context inferChildren(SoyNode node, Context context) {
    ContextPropagatingVisitor contextPropagatingVisitor = new ContextPropagatingVisitor(context);
    return contextPropagatingVisitor.execChildren(node);
  }

  /**
   * A visitor that propagates context across a Soy AST to determine its end context.
   * The end context of an AST is the one that would be reached by applying the
   * {@link RawTextContextUpdater}'s HTML/CSS/JS grammar to any output of the template
   * (where print commands produce innocuous strings).
   * An innocuous string is one that is non-empty and that contains no special characters
   * in HTML/CSS/JS. The string 'z' is a good example of an innocuous string.
   */
  private final class ContextPropagatingVisitor extends AbstractSoyNodeVisitor<Context> {

    private Context context;

    public ContextPropagatingVisitor(Context context) {
      this.context = context;
    }

    @Override public Context exec(SoyNode node) {
      visit(node);
      return context;
    }


    /**
     * Like {@link #exec(SoyNode)}, but only visits the current node's children, if any.
     */
    public Context execChildren(SoyNode node) {
      if (node instanceof ParentSoyNode<?>) {
        visitChildren((ParentSoyNode<?>) node);
      }
      return context;
    }


    @Override protected void visitTemplateNode(TemplateNode templateNode) {
      Preconditions.checkState(templateNode.getAutoescapeMode() == autoescapeMode,
          "Same ContextPropagatingVisitor cannot be reused for multiple escaping modes.");
      if (autoescapeMode == AutoescapeMode.STRICT) {
        Preconditions.checkState(
            Context.isValidStartContextForContentKind(templateNode.getContentKind(), context),
            "Strict templates may only be visited in the context for their declared content kind.");
        // Normalize to the canonical context, even if we started in a similar but allowable
        // context (e.g.  single versus double quotes).
        context = Context.getStartContextForContentKind(templateNode.getContentKind());
      }
      visitChildren(templateNode);
      if (autoescapeMode == AutoescapeMode.STRICT) {
        checkStrictBlockEndContext(templateNode, context);
      }
    }


    /**
     * Propagates context across raw chunks of HTML text.
     */
    @Override protected void visitRawTextNode(RawTextNode rawTextNode) {
      String rawText = rawTextNode.getRawText();
      Context newContext;
      try {
        newContext = RawTextContextUpdater.processRawText(rawText, context);
      } catch (SoyAutoescapeException ex) {
        throw ex.maybeAssociateNode(rawTextNode);
      }
      if (newContext.isErrorContext()) {
        throw SoyAutoescapeException.createWithNode(
            "Failed to compute an output context for raw text `" + rawText +
                "` starting in context " + context,
            rawTextNode);
      }
      context = newContext;
    }


    // TODO: Reorder visitCall* methods in AbstractSoyNodeVisitor order.
    /**
     * {@link DerivedTemplateUtils Derive} a template from the given call's target if necessary, and
     * figure out the template's end context.
     */
    @Override protected void visitCallNode(CallNode callNode) {
      try {
        String calleeName;
        if (callNode instanceof CallBasicNode) {
          calleeName = ((CallBasicNode) callNode).getCalleeName();
        } else {
          calleeName = ((CallDelegateNode) callNode).getDelCalleeName();
        }

        Pair<String, Context> derivedNameAndContext =
            inferCallSite(callNode, context, calleeName, inferences);
        String derivedCalleeName = derivedNameAndContext.first;
        if (!calleeName.equals(derivedCalleeName)) {
          inferences.retargetCall(callNode, derivedCalleeName);
        }
        context = derivedNameAndContext.second;
      } catch (SoyAutoescapeException ex) {
        throw ex.maybeAssociateNode(callNode);
      }

      visitChildren(callNode);
    }


    /**
     * For param content nodes with a {@code kind} attribute, visit the node's content with the
     * strict contextual escaper in the start context indicated by the {@code kind} attribute.
     */
    @Override protected void visitCallParamContentNode(CallParamContentNode node) {
      if (node.getContentKind() != null) {
        inferInStrictMode(node);
      } else if (autoescapeMode == AutoescapeMode.CONTEXTUAL) {
        inferInContextualModeForHtml(node);
      }
    }


    /**
     * Pass over CSS nodes.
     */
    @Override protected void visitCssNode(CssNode node) {
      context = context.getContextBeforeDynamicValue();

      // TODO: Maybe check that we're in a non-string CSS context, a JS string or value context, or
      // an attribute value context like a class, id, or for.
    }


    /**
     * For let content nodes with a {@code kind} attribute, visit the node's content with the strict
     * contextual escaper in the start context indicated by the {@code kind} attribute.
     */
    @Override protected void visitLetContentNode(LetContentNode node) {
      if (node.getContentKind() == null) {
        // Nodes without kind attribute are treated by the contextual autoescaper as before (i.e.
        // visted in whatever context the let node appears.
        // TODO: Consider unconditionally visiting as HTML_PCDATA to be consistent with {param}.
        super.visitLetContentNode(node);
      } else {
        inferInStrictMode(node);
      }
    }


    @Override protected void visitIfNode(IfNode ifNode) {
      propagateAcrossDisjunction(ifNode);
    }


    @Override protected void visitSwitchNode(SwitchNode switchNode) {
      propagateAcrossDisjunction(switchNode);
    }


    /**
     * Do multiple inferences so we can make sure we get to a consistent context regardless of
     * how many times the loop is entered.
     */
    @Override protected void visitForNode(ForNode forNode) {
      // Strictly speaking, if a for loop is guaranteed to execute once, then the result of
      // rewrite(loopBody, context) must be the same as rewrite(loopBody, result).
      // But where we cannot prove that the loop is executed at least once, the result must be the
      // same as context.
      // Even more strictly speaking, if there exists an arbitrary positive integer P such that the
      // loop is guaranteed to execute N*P times for some arbitrary non-negative integer N then
      // we can follow the loop body P times to compute the end context, and where N is positive,
      // we can ignore the context before the loop.
      // For simplicity, we just enforce the property that the loop body cannot change context.
      try {
        Context afterBody = context;
        for (SoyNode child : forNode.getChildren()) {
          afterBody = infer(child, afterBody);
        }
        Context combined = Context.union(context, afterBody);
        if (combined.isErrorContext()) {
          throw SoyAutoescapeException.createWithNode(
              "{for} command changes context so it cannot be reentered : " +
                  forNode.toSourceString(),
              forNode);
        }
        context = combined;
      } catch (SoyAutoescapeException ex) {
        throw ex.maybeAssociateNode(forNode);
      }
    }

    /**
     * Do multiple inferences so we can make sure we get to a consistent context regardless of
     * how many times the loop is entered.
     */
    @Override protected void visitForeachNode(ForeachNode foreachNode) {
      List<SoyNode> foreachChildren = foreachNode.getChildren();
      ForeachNonemptyNode neNode = (ForeachNonemptyNode) foreachChildren.get(0);
      ForeachIfemptyNode ieNode;
      if (foreachChildren.size() == 2) {
        ieNode = (ForeachIfemptyNode) foreachChildren.get(1);
      } else if (foreachChildren.size() == 1) {
        ieNode = null;
      } else {
        throw new AssertionError();
      }
      try {
        Context afterBody = context;
        if (neNode != null) {
          afterBody = infer(neNode, context);
          // Make sure that repeated invocations of the body end up in the same state.
          Context elseContext = infer(neNode, afterBody);
          Context combined = Context.union(elseContext, afterBody);
          if (combined.isErrorContext()) {
            throw SoyAutoescapeException.createWithNode(
                "{foreach} body does not end in the same context after repeated entries : " +
                    neNode.toSourceString(),
                neNode);
          }
          afterBody = combined;
        }
        Context ifemptyContext;
        if (ieNode != null) {
          ifemptyContext = infer(ieNode, context);
        } else {
          ifemptyContext = context;
        }
        Context combined = Context.union(ifemptyContext, afterBody);
        if (combined.isErrorContext()) {
          throw SoyAutoescapeException.createWithNode(
              (ieNode == null ?
                  "{foreach} body changes context : " :
                  "{foreach} body does not end in the same context as {ifempty} : ") +
                  foreachNode.toSourceString(),
              ieNode == null ? foreachNode : ieNode);
        }
        context = combined;
      } catch (SoyAutoescapeException ex) {
        throw ex.maybeAssociateNode(foreachNode);
      }
    }


    /**
     * Pick an escaping mode for the print node if this is in an
     * {@code autoescape="contextual"} template.
     */
    @Override protected void visitPrintNode(PrintNode printNode) {
      try {
        // It is an error to use autoescape-canceling print directives in strict mode unless in a
        // block of kind text.
        if (autoescapeMode == AutoescapeMode.STRICT && !context.equals(Context.TEXT)) {
          for (PrintDirectiveNode printDirective : printNode.getChildren()) {
            if (autoescapeCancellingDirectives.contains(printDirective.getName())) {
              throw SoyAutoescapeException.createWithNode(
                  // TODO: When strict mode is made user visible, adjust error message to mention
                  // strict mode.
                  "Autoescape-cancelling print directive " + printDirective.getName() +
                      " not allowed in strict blocks of non-text kind: " +
                      printNode.toSourceString(),
                  printNode);
            }
          }
        }

        List<EscapingMode> escapingModes = inferences.getEscapingMode(printNode);

        context = context.getContextBeforeDynamicValue();
        if (escapingModes.isEmpty()) {  // None specified.
          // The inferences set below specify which nodes to change. In the non-contextual modes,
          // we leave escapingModesToSet null since no changes are to be made to this print node.
          List<EscapingMode> escapingModesToSet = null;
          switch (autoescapeMode) {
            case STRICT:
            case CONTEXTUAL:
              // Infer one.
              escapingModes = escapingModesToSet = context.getEscapingModes();
              break;
            case FALSE:
              // Nothing to do. Just assume that the end context is the same as the start context.
              break;
            case TRUE:
              escapingModes = ImmutableList.of(defaultEscapingMode);
              break;
          }
          inferences.setEscapingDirectives(printNode, context, escapingModesToSet);
        } else if (!context.isCompatibleWith(escapingModes.get(0))) {
          throw SoyAutoescapeException.createWithNode(
              "Escaping modes " + escapingModes + " not compatible with " + context + " : " +
                  printNode.toSourceString(),
              printNode);
        }

        // Figure out the context at the end.
        if (!escapingModes.isEmpty() || autoescapeMode == AutoescapeMode.CONTEXTUAL ||
            autoescapeMode == AutoescapeMode.STRICT) {
          // If we know the escaping mode or we're supposed to choose one, then use that.
          context = getContextAfterEscaping(printNode, context, escapingModes);
        } else {
          // If we are not in an autoescaping template, assume that the author knows what they're
          // doing and simulate an innocuous value.
          context = RawTextContextUpdater.processRawText("z", context);
        }
      } catch (SoyAutoescapeException ex) {
        throw ex.maybeAssociateNode(printNode);
      }
    }


    /**
     * Handle conjunction nodes.
     */
    @Override protected void visitSoyNode(SoyNode node) {
      if (node instanceof ParentSoyNode<?>) {
        visitChildren((ParentSoyNode<?>) node);
      }
    }


    //
    // Helper methods.


    /**
     * Determines the content kind of the templates.
     *
     * This relies on CheckDelegatesVisitor to print friendly messages if the deltemplates differ
     * in content kind.
     */
    private ContentKind getCommonContentKindIfStrict(List<TemplateNode> templates) {
      if (templates == null || templates.isEmpty()) {
        return null;
      }
      ContentKind contentKind = templates.get(0).getContentKind();
      for (TemplateNode template : templates) {
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
     * @return The name of the template to call (possibly derived from templateName) and the context
     *     after the call ends.
     */
    private Pair<String, Context> inferCallSite(
        CallNode callNode, Context startContext, String templateName, Inferences inferences)
        throws SoyAutoescapeException {
      inferences.recordTemplateChecked(templateName);
      List<TemplateNode> targets = inferences.lookupTemplates(templateName);
      ContentKind calleeStrictContentKind = getCommonContentKindIfStrict(targets);

      if (autoescapeMode == AutoescapeMode.STRICT) {
        // We're currently in a strict mode template. Check what kind of template is being called.
        if (calleeStrictContentKind != null || targets == null || targets.isEmpty()) {
          // If a strict template calls another strict template (or an unknown extern), the result
          // will be escaped, so the call statement behaves effectively like a print statement.
          // No re-contextualization of the callee is done. As an optimization, don't escape the
          // call site if we know all possible targets return the correct sanitized content kind.
          if (inferences.mightHaveExternalDefs(templateName) ||
              !Context.isValidStartContextForContentKind(calleeStrictContentKind, startContext)) {
            inferences.setEscapingDirectives(callNode, context, context.getEscapingModes());
          }
          return Pair.of(templateName, getContextAfterDynamicValue(callNode, startContext));
        } else if (startContext.equals(Context.TEXT)) {
          // Contextualize the callee in TEXT mode. It's okay to call any template from TEXT mode
          // since TEXT doesn't make any safety guarantees.
          return contextualizeCallee(Context.TEXT, templateName, inferences);
        } else {
          // TODO: We could easily allow this in a future release. We can contextualize the callee
          // and re-escape its output. There are two options. TEXT is nicer because there's no
          // re-escaping in most cases. Markup won't be preserved, but at least there will be zero
          // double-escaping. HTML is more consistent because externs behave the same as interns.
          throw SoyAutoescapeException.createWithNode(
              "Soy strict autoescaping currently forbids calls to non-strict templates, unless " +
                  "the context is kind=\"text\", since there's no guarantee the callee is safe: " +
                  callNode.getTagString(),
              callNode);
        }

      } else {
        // In a non-strict mode template.
        if (targets == null || targets.isEmpty()) {
          // External template not visible to compiler -- let's pray for the best! We might end up
          // calling a Javascript-escaping template from HTML or vice versa.
          return Pair.of(templateName, startContext);
        } else if (calleeStrictContentKind != null) {
          // Non-strict templates may call strict templates, but only if the context is a match.
          // NOTE: While contextual templates *might* do escaping like strict in this context, it
          // would silently break if the template is compiled as an extern. By having this check,
          // teams can do a single monolithic compilation for error checking to prevent this.
          if (!Context.isValidStartContextForContentKind(calleeStrictContentKind, startContext)) {
            throw SoyAutoescapeException.createWithNode(
                "Cannot call strictly autoescaped template " + templateName + " of kind=\"" +
                    NodeContentKinds.toAttributeValue(calleeStrictContentKind) +
                    "\" from incompatible context " + startContext + ". Strict templates " +
                    "generate extra code to safely call templates of other content kinds, but " +
                    "non-strict templates do not: " + callNode.getTagString(),
                callNode);
          }
          Preconditions.checkState(
              Context.isValidEndContextForContentKind(calleeStrictContentKind,
                  determineContextualization(startContext, templateName, inferences)),
              "This assertion should be redundant with strict mode's end-context check.");
          return Pair.of(templateName, startContext);
        } else {
          // Normal contextual-to-contextual propagation.
          return contextualizeCallee(startContext, templateName, inferences);
        }
      }
    }


    /**
     * Creates a contextual derivative of the specified template and infers the end context.
     *
     * @param startContext The known context to start at.
     * @param calleeName The non-contextualized callee name.
     * @param inferences The inferences to write to.
     * @return A pairing of the new derived name and the end context.
     */
    private Pair<String, Context> contextualizeCallee(
        Context startContext, String calleeName, Inferences inferences) {
      // Propgate the context into the callee contextual template.
      String suffix = DerivedTemplateUtils.getSuffix(startContext);
      String baseName = DerivedTemplateUtils.getBaseName(calleeName);
      // The derived template name.
      String newCalleeName = baseName + suffix;

      // Clone the templates for this new context if needed.
      if (inferences.lookupTemplates(newCalleeName) == null) {
        inferences.cloneTemplates(baseName, newCalleeName);
      }

      Context endContext = determineContextualization(startContext, newCalleeName, inferences);
      return Pair.of(newCalleeName, endContext);
    }


    /**
     * Determines the end context and a set of inferences for a template in a particular context.
     *
     * This does not create new cloned templates, but just computes contextualization on existing
     * ones.
     *
     * @param startContext The start context we're calling these templates in.
     * @param calleeName The callee's name, already modified for context.
     * @param inferences The inferences to modify.
     */
    private Context determineContextualization(
        Context startContext, String calleeName, Inferences inferences) {
      Context endContext = inferences.getTemplateEndContext(calleeName);
      if (endContext != null) {
        // We've already computed this; return early.
        return endContext;
      }

      List<TemplateNode> templateNodes = inferences.lookupTemplates(calleeName);
      // Optimistically assume the new callee ends with the same context as it starts, and then
      // verify that's the case.
      Pair<Inferences, Context> hypothesis = hypothesizeContextualization(
          startContext, startContext, calleeName, templateNodes, inferences);
      endContext = hypothesis.second;
      Inferences subInferences = hypothesis.first;
      if (!endContext.equals(startContext) && subInferences.wasTemplateChecked(calleeName)) {
        // Try assuming endContext as the endContext and see if that is a fixed point. If so, it
        // is a valid endContext context since its output is the same regardless of whether
        // recursive calls are properly typed. This allows us to gloss over minor differences in
        // startContexts, e.g. JsFollowingSlash.
        Pair<Inferences, Context> secondHypothesis = hypothesizeContextualization(
            startContext, endContext, calleeName, templateNodes, inferences);
        endContext = Context.union(secondHypothesis.second, endContext);
        // See if the first and second hypothesis result in a compatible end context.
        if (endContext.isErrorContext()) {
          // Cannot identify an end context. Bail.
          throw SoyAutoescapeException.createWithNode(
              "Cannot determine end context for recursive template " + calleeName,
              templateNodes.get(0));
        }
      }
      subInferences.recordTemplateEndContext(calleeName, endContext);
      subInferences.foldIntoParent();
      return endContext;
    }


    /**
     * Hypothesizes a particular end context and determines a potential end context, if any.
     *
     * This returns the *actual* end context determined from this hypothesis. Hypotheses are
     * needed to handle recursive templates, where the output context is needed to compute the
     * context within the template.
     *
     * @param startContext The known context to start at.
     * @param hypotheticalEndContext The end context to test.
     * @param calleeName Name of the callee.
     * @param templateNodes The templates and deltemplates of the same name.
     * @param parentInferences The inferences to work from.
     * @return A combination of the end context determined and the inferences that go along with
     *     them.
     */
    private Pair<Inferences, Context> hypothesizeContextualization(
        Context startContext, Context hypotheticalEndContext, String calleeName,
        List<TemplateNode> templateNodes, Inferences parentInferences) {
      // Create a hypothetical world of inferences based on this hypothesis. It is up to the caller
      // to fold these into the parent inferences if it chooses to use these.
      Inferences inferences = new Inferences(parentInferences);
      Context endContext = null;
      inferences.recordTemplateEndContext(calleeName, hypotheticalEndContext);
      for (TemplateNode templateNode : templateNodes) {
        Context c = inferTemplateEndContext(
            templateNode, startContext, inferences, autoescapeCancellingDirectives);
        endContext = (endContext != null) ? Context.union(endContext, c) : c;
      }
      return Pair.of(inferences, endContext);
    }


    /**
     * Consider the various branches separately and compute a union context for each branch.
     */
    private void propagateAcrossDisjunction(ParentSoyNode<?> node) {
      try {
        // All the branches of an {if} or {switch} should return compatible contexts, so that we can
        // figure out the end context of the branch as a whole.
        Iterator<? extends SoyNode> childIt = node.getChildren().iterator();
        SoyNode firstBranch = childIt.next();
        Context out = infer(firstBranch, context);
        boolean sawElseOrDefault = false;
        while (childIt.hasNext()) {
          SoyNode branch = childIt.next();
          Context brOut = infer(branch, context);
          Context combined = Context.union(out, brOut);
          if (combined.isErrorContext()) {
            throw SoyAutoescapeException.createWithNode(
                (node instanceof IfNode ?
                    "{if} command branch ends in a different context than preceding branches: " :
                    "{switch} command case ends in a different context than preceding cases: ") +
                    branch.toSourceString(),
                branch);
          }
          out = combined;
          if (branch instanceof IfElseNode || branch instanceof SwitchDefaultNode) {
            sawElseOrDefault = true;
          }
        }

        // If there is no else or default, then the end context has to be the compatible with the
        // start context.
        if (!sawElseOrDefault) {
          Context combined = Context.union(context, out);
          if (combined.isErrorContext()) {
            throw SoyAutoescapeException.createWithNode(
                (node instanceof IfNode ?
                    "{if} command without {else} changes context : " :
                    "{switch} command without {default} changes context : ") +
                    node.toSourceString(),
                node);
          }
          out = combined;
        }

        context = out;
      } catch (SoyAutoescapeException ex) {
        throw ex.maybeAssociateNode(node);
      }
    }


   /**
     * Apply strict contextual autoescaping to the given node's children.
     *
     * <p>The start context is the given node's declared {@link ContentKind}, and it is enforced
     * that the block's inferred end context matches the start context.
     *
     * <p>This method is used to visit the content of {let} and {param} nodes with a {@code kind}
     * attribute.
     */
    private void inferInStrictMode(RenderUnitNode node) {
      // Note: CheckEscapingSanityVisitor ensures that {param} and {let} nodes with kind
      // attribute only occur in contextually autoescaped templates. We can't ensure this here,
      // because this visitor does not visit non-contextual templates.
      final Context endContext = new InferenceEngine(
          AutoescapeMode.STRICT, inferences, autoescapeCancellingDirectives)
              .inferChildren(node, Context.getStartContextForContentKind(node.getContentKind()));
      checkStrictBlockEndContext(node, endContext);
    }


    /**
     * Checks that the end context of a strict block is compatible with its start context.
     *
     * Throws if they mismatch.
     */
    private void checkStrictBlockEndContext(RenderUnitNode node, Context endContext) {
      if (!Context.isValidEndContextForContentKind(node.getContentKind(), endContext)) {
        throw SoyAutoescapeException.createWithNode(
            "A strict block of kind=\"" + NodeContentKinds.toAttributeValue(node.getContentKind()) +
                "\" cannot end in context " + endContext + ". Likely cause is " +
                Context.getLikelyEndContextMismatchCause(node.getContentKind(), endContext) + ": " +
                node.getTagString(),
            node);
      }
    }


    /**
     * Applies HTML contextual autoescaping on a legacy contextual parameter block.
     */
    private void inferInContextualModeForHtml(CommandNode node) {
      // NOTE: Previously this wouldn't do any contextual analysis, which resulted in subtle bugs
      // such as the contextual autoescaper not seeing typed parameters in nested calls.
      final Context paramContentNodeEndContext = new InferenceEngine(
          AutoescapeMode.CONTEXTUAL, inferences, autoescapeCancellingDirectives)
              .inferChildren(node, Context.HTML_PCDATA);
      if (!paramContentNodeEndContext.equals(Context.HTML_PCDATA)) {
        throw SoyAutoescapeException.createWithNode(
            "Blocks should start and end in HTML context: " + node.getTagString(), node);
      }
    }
  }


  //
  // Static helper methods (cannot be part of inner class).

  /**
   * Returns the end context after a properly escaped dynamic value was inserted.
   * @param node Node to print out in case of an error.
   * @param startContext The context after which a dynamic value is inserted.
   */
  private static Context getContextAfterDynamicValue(SoyNode node, Context startContext) {
    // TODO: If the context is JS, perhaps this should return JsFollowingSlash.UNKNOWN. Right now
    // we assume that the dynamic value is also an expression, but JsFollowingSlash.UNKNOWN would
    // account for things that end in semicolons (since the next slash could be either a regex OR a
    // division op).
    return getContextAfterEscaping(node, startContext,
        startContext.getContextBeforeDynamicValue().getEscapingModes());
  }


  /**
   * Returns the end context after a dynamic value was inserted with specific escaping modes.
   *
   * @param node The node to print in case of an error.
   * @param startContext The start context -- must be a "context before dynamic value".
   * @param escapingModes The escaping sequence being used.
   */
  private static Context getContextAfterEscaping(
       SoyNode node, Context startContext, List<EscapingMode> escapingModes) {
    // TODO: Shouldn't this use the last escaping mode, since the order is from earliest to the
    // latest escaping?
    Context endContext = startContext.getContextAfterEscaping(
        escapingModes.isEmpty() ? null : escapingModes.get(0));
    if (endContext.isErrorContext()) {
      if (startContext.uriPart == Context.UriPart.UNKNOWN ||
          startContext.uriPart == Context.UriPart.UNKNOWN_PRE_FRAGMENT) {
        throw SoyAutoescapeException.createWithNode(
            "Cannot determine which part of the URL " + node.toSourceString() + " is in.", node);
      } else {
        throw SoyAutoescapeException.createWithNode(
            "Don't put {print} or {call} inside comments : " + node.toSourceString(), node);
      }
    }
    return endContext;
  }
}
