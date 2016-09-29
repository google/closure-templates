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
import com.google.common.collect.Sets;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprparse.SoyParsingContext;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.EscapingMode;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoySyntaxExceptionUtils;
import com.google.template.soy.soytree.TemplateNode;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Applies changes specified in {@link Inferences} to a Soy parse tree.
 *
 */
final class Rewriter {

  /** The changes to make. */
  private final Inferences inferences;

  /**
   * The names of templates visited.  Used to distinguish derived templates from templates in the
   * input Soy files.
   */
  private final Set<String> visitedTemplateNames = Sets.newHashSet();

  /** Maps print directive names to the content kinds they consume and produce. */
  private final Map<String, ContentKind> sanitizedContentOperators;

  /** For reporting errors. */
  private final ErrorReporter errorReporter;

  Rewriter(
      Inferences inferences,
      Map<String, ContentKind> sanitizedContentOperators,
      ErrorReporter errorReporter) {
    this.inferences = inferences;
    this.sanitizedContentOperators = sanitizedContentOperators;
    this.errorReporter = errorReporter;
  }

  /**
   * @return Derived templates that should be added to the parse tree.
   */
  public List<TemplateNode> rewrite(SoyFileSetNode files) {
    RewriterVisitor mutator = new RewriterVisitor();
    // First walk the input files that the caller already knows about.
    for (SoyFileNode file : files.getChildren()) {
      mutator.exec(file);
    }

    // Now walk over anything not reachable from the input files to make sure we get all the derived
    // templates.
    ImmutableList.Builder<TemplateNode> extraTemplates = ImmutableList.builder();
    for (TemplateNode template : inferences.getAllTemplates()) {
      String name = template.getTemplateName();
      if (!visitedTemplateNames.contains(name)) {
        extraTemplates.add(template);
        mutator.exec(template);
      }
    }
    return extraTemplates.build();
  }


  /**
   * A visitor that applies the changes in Inferences to a Soy tree.
   */
  private final class RewriterVisitor extends AbstractSoyNodeVisitor<Void> {
    /**
     * Keep track of template nodes so we know which are derived and which aren't.
     */
    @Override protected void visitTemplateNode(TemplateNode templateNode) {
      if (visitedTemplateNames.contains(templateNode.getTemplateName())) {
        throw SoySyntaxExceptionUtils.createWithNode(
            "was defined previously",
            templateNode);
      }
      visitedTemplateNames.add(templateNode.getTemplateName());
      visitChildrenAllowingConcurrentModification(templateNode);
    }

    /**
     * Add any escaping directives.
     */
    @Override protected void visitPrintNode(PrintNode printNode) {
      int id = printNode.getId();
      ImmutableList<EscapingMode> escapingModes = inferences.getEscapingModesForId(id);
      for (EscapingMode escapingMode : escapingModes) {
        PrintDirectiveNode newPrintDirective = new PrintDirectiveNode.Builder(
            inferences.getIdGenerator().genId(),
            escapingMode.directiveName,
            "",
            printNode.getSourceLocation())
            .build(SoyParsingContext.exploding());

        // Figure out where to put the new directive.
        // Normally they go at the end to ensure that the value printed is of the appropriate type,
        // but if there are SanitizedContentOperators at the end, then make sure that their input
        // is of the appropriate type since we know that they will not change the content type.
        int newPrintDirectiveIndex = printNode.numChildren();
        while (newPrintDirectiveIndex > 0) {
          String printDirectiveName = printNode.getChild(newPrintDirectiveIndex - 1).getName();
          ContentKind contentKind = sanitizedContentOperators.get(printDirectiveName);
          if (contentKind == null || contentKind != escapingMode.contentKind) {
            break;
          }
          --newPrintDirectiveIndex;
        }

        printNode.addChild(newPrintDirectiveIndex, newPrintDirective);
      }
    }

    /**
     * Do nothing.
     */
    @Override protected void visitRawTextNode(RawTextNode rawTextNode) {
      // TODO: Possibly normalize raw text nodes by adding quotes around unquoted attributes with
      // non-noescape dynamic content to avoid the need for space escaping.
    }

    /**
     * Grabs the inferred escaping directives from the node in string form.
     */
    private ImmutableList<String> getDirectiveNamesForNode(SoyNode node) {
      ImmutableList.Builder<String> escapingDirectiveNames = new ImmutableList.Builder<>();
      for (EscapingMode escapingMode : inferences.getEscapingModesForId(node.getId())) {
        escapingDirectiveNames.add(escapingMode.directiveName);
      }
      return escapingDirectiveNames.build();
    }

    /**
     * Sets the escaping directives we inferred on the node.
     */
    @Override protected void visitMsgFallbackGroupNode(MsgFallbackGroupNode node) {
      node.setEscapingDirectiveNames(getDirectiveNamesForNode(node));
      visitChildren(node);
    }

    /**
     * Rewrite call targets.
     *
     * Note that this processing is only applicable for CallBasicNodes. The reason is that
     * CallDelegateNodes are always calling public templates (delegate templates are always public),
     * and public templates never need rewriting.
     *
     * TODO: Modify contextual autoescape to deal with delegates appropriately.
     */
    @Override protected void visitCallNode(CallNode callNode) {
      // We cannot easily access the original context.  However, because everything has already been
      // parsed, that should be fine.  I don't think this can fail at all, but whatever.
      SoyParsingContext context = SoyParsingContext.empty(errorReporter, "fake.namespace");

      String derivedCalleeName = inferences.getDerivedCalleeNameForCallId(callNode.getId());
      if (derivedCalleeName != null) {
        // Creates a new call node, but with a different target name.
        // TODO: Create a CallNode.withNewName() convenience method.
        CallNode newCallNode;
        if (callNode instanceof CallBasicNode) {
          // For simplicity, use the full callee name as the source callee name.
          newCallNode = new CallBasicNode.Builder(callNode.getId(), callNode.getSourceLocation())
              .calleeName(derivedCalleeName)
              .sourceCalleeName(derivedCalleeName)
              .dataAttribute(callNode.dataAttribute())
              .userSuppliedPlaceholderName(callNode.getUserSuppliedPhName())
              .syntaxVersionBound(callNode.getSyntaxVersionUpperBound())
              .escapingDirectiveNames(callNode.getEscapingDirectiveNames())
              .build(context);
        } else {
          CallDelegateNode callNodeCast = (CallDelegateNode) callNode;
          newCallNode = new CallDelegateNode.Builder(callNode.getId(), callNode.getSourceLocation())
              .delCalleeName(derivedCalleeName)
              .delCalleeVariantExpr(callNodeCast.getDelCalleeVariantExpr())
              .allowEmptyDefault(callNodeCast.allowsEmptyDefault())
              .dataAttribute(callNode.dataAttribute())
              .userSuppliedPlaceholderName(callNode.getUserSuppliedPhName())
              .escapingDirectiveNames(callNode.getEscapingDirectiveNames())
              .build(context);
        }
        if (!callNode.getCommandText().equals(newCallNode.getCommandText())) {
          moveChildrenTo(callNode, newCallNode);
          replaceChild(callNode, newCallNode);
        }
        // Ensure we visit the new node instead of the old one.
        callNode = newCallNode;
      }

      // For strict templates, set any necessary escaping directives.
      callNode.setEscapingDirectiveNames(getDirectiveNamesForNode(callNode));

      visitChildrenAllowingConcurrentModification(callNode);
    }

    /**
     * Recurses to children.
     */
    @Override protected void visitSoyNode(SoyNode node) {
      if (node instanceof ParentSoyNode<?>) {
        visitChildrenAllowingConcurrentModification((ParentSoyNode<?>) node);
      }
    }

  }


  /**
   * Replaces old child with new child.
   */
  private static void replaceChild(StandaloneNode oldChild, StandaloneNode newChild) {
    oldChild.getParent().replaceChild(oldChild, newChild);
  }


  private static <T extends SoyNode> void moveChildrenTo(
      ParentSoyNode<T> oldParent, ParentSoyNode<T> newParent) {
    List<T> children = ImmutableList.copyOf(oldParent.getChildren());
    oldParent.clearChildren();
    newParent.addChildren(children);
  }

}
