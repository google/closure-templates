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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.internal.base.Pair;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.AutoescapeMode;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CssNode;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForeachIfemptyNode;
import com.google.template.soy.soytree.ForeachNode;
import com.google.template.soy.soytree.ForeachNonemptyNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TemplateNode;

import java.util.Iterator;
import java.util.List;

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
   *    If a template can be reached from multiple contexts, then it should be cloned.
   *    This class automatically does that for called templates.
   * @param inferences Receives all suggested changes and inferences to tn.
   * @return The end context when the given template is reached from {@code startContext}.
   */
  public static Context inferTemplateEndContext(
      TemplateNode templateNode, Context startContext, Inferences inferences)
      throws SoyAutoescapeException {
    Context endContext;
    try {
      Context context = startContext;
      AutoescapeMode autoescapeMode = templateNode.getAutoescapeMode();
      for (SoyNode child : templateNode.getChildren()) {
        context = new InferenceEngine(autoescapeMode, inferences).infer(child, context);
      }
      // Context started off as startContext and we have propagated context through all of
      // template's children, so now context is the template's end context.
      endContext = context;
      inferences.recordTemplateEndContext(templateNode.getTemplateName(), endContext);
    } catch (SoyAutoescapeExceptionWrapper e) {
      SoyAutoescapeException ex = e.getSoyAutoescapeException();
      if (ex.getTemplateName() == null) {
        ex.setTemplateName(templateNode.getTemplateNameForUserMsgs());
      }
      if (!ex.getSourceLocation().isKnown()) {
        SoyFileNode containingFile = templateNode.getNearestAncestor(SoyFileNode.class);
        if (containingFile != null) {
          ex.setFilePath(containingFile.getFilePath());
        }
      }
      throw ex;
    }
    return endContext;
  }

  /** True if the inference engine is allowed to add escaping directives for this template. */
  private final AutoescapeMode autoescapeMode;

  /** Receives modifications and typing inferences. */
  private final Inferences inferences;

  /** The escaping mode to assume when none is specified. */
  private final @Nullable EscapingMode defaultEscapingMode;

  private InferenceEngine(AutoescapeMode autoescapeMode, Inferences inferences) {
    this.autoescapeMode = autoescapeMode;
    this.inferences = inferences;
    this.defaultEscapingMode = (autoescapeMode != AutoescapeMode.FALSE) ?
        EscapingMode.ESCAPE_HTML : null;
  }

  private Context infer(SoyNode node, Context context) {
    return new ContextPropagatingVisitor(context).exec(node);
  }


  /**
   * A visitor that propagates context across a Soy AST to determine its end context.
   * The end context of an AST is the one that would be reached by applying the
   * {@link RawTextContextUpdater}'s HTML/CSS/JS grammar to any output of the template
   * (where print commands produce innocuous strings).
   * An innocuous string is one that is non-empty and that contains no special characters
   * in HTML/CSS/JS.  The string 'z' is a good example of an innocuous string.
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
     * Propagates context across raw chunks of HTML text.
     */
    @Override protected void visitRawTextNode(RawTextNode rawTextNode) {
      String rawText = rawTextNode.getRawText();
      Context newContext;
      try {
        newContext = RawTextContextUpdater.processRawText(rawText, context);
      } catch (SoyAutoescapeException ex) {
        ex.maybeSetContextNode(rawTextNode);
        throw new SoyAutoescapeExceptionWrapper(ex);
      }
      if (newContext.isErrorContext()) {
        throw new SoyAutoescapeExceptionWrapper(new SoyAutoescapeException(
            rawTextNode, "Failed to compute an output context for raw text `" + rawText +
            "` starting in context " + context));
      }
      context = newContext;
    }

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
            inferCallSite(context, calleeName, inferences);
        String derivedCalleeName = derivedNameAndContext.first;
        if (!calleeName.equals(derivedCalleeName)) {
          inferences.retargetCall(callNode, derivedCalleeName);
        }
        context = derivedNameAndContext.second;
      } catch (SoyAutoescapeException ex) {
        ex.maybeSetContextNode(callNode);
        throw new SoyAutoescapeExceptionWrapper(ex);
      }

      // TODO: If CallParamNode is changed to allow content inside it, then we need to not treat its
      // children as contextual, and make sure that its context does not change the context
      // of the template.
    }

    /**
     * Derives a template if necessary to compute a consistent end context for a call to the named
     * template.
     *
     * @param startContext The context before the call.
     * @param templateName The name of the template being called.
     * @param inferences Contains a mapping of templates visible to the call site, prior typing
     *     decisions, and derived templates.  Will receive any templates successfully derived as a
     *     side-effect of this call.
     * @return The name of the template to call (possibly derived from templateName) and the context
     *     after the call ends.
     */
    private Pair<String, Context> inferCallSite(
        Context startContext, String templateName, Inferences inferences)
        throws SoyAutoescapeException {
      inferences.recordTemplateChecked(templateName);
      List<TemplateNode> targets = inferences.lookupTemplates(templateName);
      // Best guess for externs
      if (targets == null || targets.isEmpty()) {
        return Pair.of(templateName, startContext);
      }

      String suffix = DerivedTemplateUtils.getSuffix(startContext);
      String baseName = DerivedTemplateUtils.getBaseName(templateName);
      // The derived template name.
      String newCalleeName = baseName + suffix;

      Context end = inferences.getTemplateEndContext(newCalleeName);
      if (end != null) {
        return Pair.of(newCalleeName, end);
      }

      List<TemplateNode> templateNodes = inferences.lookupTemplates(newCalleeName);
      if (templateNodes == null) {
        templateNodes = inferences.cloneTemplates(baseName, newCalleeName);
      }
      Inferences inferences2 = new Inferences(inferences);
      inferences2.recordTemplateEndContext(newCalleeName, startContext);
      for (TemplateNode templateNode : templateNodes) {
        Context c = inferTemplateEndContext(templateNode, startContext, inferences2);
        end = end != null ? Context.union(end, c) : c;
      }
      if (!end.equals(startContext)) {
        if (inferences2.wasTemplateChecked(newCalleeName)) {
          // Try assuming end as the endContext and see if that is a fixed point.
          // If so, it is a valid end context since its output is the same regardless of whether
          // recursive calls are properly typed.
          // This allows us to gloss over minor differences in startContexts, e.g. JsFollowingSlash.
          Inferences inferences3 = new Inferences(inferences);
          inferences3.recordTemplateEndContext(newCalleeName, end);
          Context possibleFixedPoint = null;
          for (TemplateNode templateNode : templateNodes) {
            Context c = inferTemplateEndContext(templateNode, startContext, inferences3);
            possibleFixedPoint = possibleFixedPoint != null
                ? Context.union(c, possibleFixedPoint) : c;
          }
          end = Context.union(possibleFixedPoint, end);
          if (end.isErrorContext()) {
            // Cannot identify an end context.  Bail.
            throw new SoyAutoescapeException(
                templateNodes.get(0),
                "Cannot determine end context for recursive template " + templateName);
          }
        }
      }
      inferences2.recordTemplateEndContext(newCalleeName, end);
      inferences2.foldIntoParent();
      return Pair.of(newCalleeName, end);
    }

    @Override protected void visitIfNode(IfNode ifNode) {
      propagateAcrossDisjunction(ifNode);
    }

    @Override protected void visitSwitchNode(SwitchNode switchNode) {
      propagateAcrossDisjunction(switchNode);
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
            throw new SoyAutoescapeException(
                branch,
                (node instanceof IfNode ?
                 "{if} command branch ends in a different context than preceding branches: " :
                 "{switch} command case ends in a different context than preceding cases: ") +
                branch.toSourceString());
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
            throw new SoyAutoescapeException(
                node,
                (node instanceof IfNode ?
                 "{if} command without {else} changes context : " :
                 "{switch} command without {default} changes context : ") +
                node.toSourceString());
          }
          out = combined;
        }

        context = out;
      } catch (SoyAutoescapeException ex) {
        ex.maybeSetContextNode(node);
        throw new SoyAutoescapeExceptionWrapper(ex);
      }
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
          throw new SoyAutoescapeException(
              forNode,
              "{for} command changes context so it cannot be reentered : " +
              forNode.toSourceString());
        }
        context = combined;
      } catch (SoyAutoescapeException ex) {
        ex.maybeSetContextNode(forNode);
        throw new SoyAutoescapeExceptionWrapper(ex);
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
            throw new SoyAutoescapeException(
                neNode,
                "{foreach} body does not end in the same context after repeated entries : " +
                neNode.toSourceString());
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
          throw new SoyAutoescapeException(
              ieNode == null ? foreachNode : ieNode,
              (ieNode == null ?
               "{foreach} body changes context : " :
               "{foreach} body does not end in the same context as {ifempty} : ") +
              foreachNode.toSourceString());
        }
        context = combined;
      } catch (SoyAutoescapeException ex) {
        ex.maybeSetContextNode(foreachNode);
        throw new SoyAutoescapeExceptionWrapper(ex);
      }
    }

    /**
     * Pick an escaping mode for the print node if this is in an
     * {@code autoescape="contextual"} template.
     */
    @Override protected void visitPrintNode(PrintNode printNode) {
      try {
        List<EscapingMode> escapingModes = inferences.getEscapingMode(printNode);

        context = context.getContextBeforeDynamicValue();
        if (escapingModes.isEmpty()) {  // None specified.
          switch (autoescapeMode) {
            case CONTEXTUAL:
              // Infer one.
              escapingModes = context.getEscapingModes();
              inferences.setEscapingDirectives(printNode, escapingModes);
              break;
            case FALSE:
              // Nothing to do.  Just assume that the end context is the same as the start context.
              break;
            case TRUE:
              escapingModes = ImmutableList.of(defaultEscapingMode);
              break;
          }
        } else if (!context.isCompatibleWith(escapingModes.get(0))) {
          throw new SoyAutoescapeException(
              printNode,
              "Escaping modes " + escapingModes + " not compatible with " + context + " : " +
              printNode.toSourceString());
        }

        // Figure out the context at the end.
        if (!escapingModes.isEmpty() || autoescapeMode == AutoescapeMode.CONTEXTUAL) {
          // If we know the escaping mode or we're supposed to choose one, then use that.
          Context newContext = context.getContextAfterEscaping(
              escapingModes.isEmpty() ? null : escapingModes.get(0));
          if (newContext.isErrorContext()) {
            if (context.uriPart == Context.UriPart.UNKNOWN ||
                context.uriPart == Context.UriPart.UNKNOWN_PRE_FRAGMENT) {
              throw new SoyAutoescapeException(
                  printNode,
                  "Cannot determine which part of the URL " + printNode.toSourceString() +
                  " is in.");
            } else {
              throw new SoyAutoescapeException(
                  printNode, "Don't put {print} inside comments : " + printNode.toSourceString());
            }
          }
          context = newContext;
        } else {
          // If we are not in an autoescaping template, assume that the author knows what they're
          // doing and simulate an innocuous value.
          context = RawTextContextUpdater.processRawText("z", context);
        }
      } catch (SoyAutoescapeException ex) {
        ex.maybeSetContextNode(printNode);
        throw new SoyAutoescapeExceptionWrapper(ex);
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
     * Handle conjunction nodes.
     */
    @Override protected void visitSoyNode(SoyNode node) {
      if (node instanceof ParentSoyNode<?>) {
        visitChildren((ParentSoyNode<?>) node);
      }
    }
  }

}
