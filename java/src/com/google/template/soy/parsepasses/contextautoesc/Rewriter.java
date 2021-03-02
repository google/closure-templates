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

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SanitizedContentOperator;
import com.google.template.soy.data.internal.Converters;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.shared.internal.ShortCircuitable;
import com.google.template.soy.shared.internal.ShortCircuitables;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.EscapingMode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.Visibility;
import com.google.template.soy.soytree.defn.LocalVar;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.SanitizedType;
import com.google.template.soy.types.TemplateType;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * Applies changes specified in {@link Inferences} to a Soy parse tree.
 *
 */
final class Rewriter {

  /** The changes to make. */
  private final Inferences inferences;

  private final IdGenerator idGen;

  private final ImmutableMap<String, ? extends SoyPrintDirective> printDirectives;
  private final RewriterVisitor mutator = new RewriterVisitor();

  Rewriter(
      Inferences inferences,
      IdGenerator idGen,
      ImmutableList<? extends SoyPrintDirective> printDirectives) {
    this.inferences = inferences;
    this.idGen = idGen;
    this.printDirectives =
        printDirectives.stream()
            .filter(d -> EscapingMode.fromDirective(d.getName()) != null)
            .collect(toImmutableMap(SoyPrintDirective::getName, Function.identity()));
  }

  /** @return Derived templates that should be added to the parse tree. */
  public void rewrite(SoyNode node) {
    mutator.exec(node);
  }

  /** A visitor that applies the changes in Inferences to a Soy tree. */
  private final class RewriterVisitor extends AbstractSoyNodeVisitor<Void> {

    /** Add any escaping directives. */
    @Override
    protected void visitPrintNode(PrintNode printNode) {
      ImmutableList<EscapingMode> escapingModes = inferences.getEscapingModesForNode(printNode);
      for (EscapingMode escapingMode : escapingModes) {
        SoyPrintDirective directive = printDirectives.get(escapingMode.directiveName);
        if (directive == null) {
          throw new IllegalStateException(
              "Couldn't find directive for EscapingMode " + escapingMode);
        }
        PrintDirectiveNode newPrintDirective =
            PrintDirectiveNode.createSyntheticNode(
                idGen.genId(),
                Identifier.create(escapingMode.directiveName, printNode.getSourceLocation()),
                printNode.getSourceLocation(),
                directive);
        // Figure out where to put the new directive.
        // Normally they go at the end to ensure that the value printed is of the appropriate type,
        // but if there are SanitizedContentOperators at the end, then make sure that their input
        // is of the appropriate type since we know that they will not change the content type.
        int newPrintDirectiveIndex = printNode.numChildren();
        while (newPrintDirectiveIndex > 0) {
          SoyPrintDirective printDirective =
              printNode.getChild(newPrintDirectiveIndex - 1).getPrintDirective();
          SanitizedContentKind contentKind =
              printDirective instanceof SanitizedContentOperator
                  ? SanitizedContentKind.valueOf(
                      ((SanitizedContentOperator) printDirective).getContentKind().name())
                  : null;
          if (contentKind == null || contentKind != escapingMode.contentKind) {
            break;
          }
          --newPrintDirectiveIndex;
        }

        printNode.addChild(newPrintDirectiveIndex, newPrintDirective);
      }
      // Be conservative.
      // IncrementalDom currently depends on the existence of escapeHtml to force logging to occur.
      // It might be better to use a different strategy, or just to replace directives that operate
      // on HTML.  print directives are slowly going away.
      if (!printNode.hasUserSpecifiedPrintDirectives()) {
        SanitizedContentKind trustedKind = getTrustedContentKindForNode(printNode);
        if (trustedKind != null) {
          ContentKind trustedContentKind = ContentKind.valueOf(trustedKind.name());
          // Remove the initial directive if it would short circuit for the given kind.
          while (printNode.numChildren() > 0) {
            PrintDirectiveNode directive = printNode.getChild(0);
            if (directive.getPrintDirective() instanceof ShortCircuitable
                && ((ShortCircuitable) directive.getPrintDirective())
                    .isNoopForKind(trustedContentKind)) {
              printNode.removeChild(0);
              continue;
            }
            break;
          }
        }
      }
    }

    /**
     * Returns the content kind for the value if it exists and can be trusted.
     *
     * <p>We only trust sanitized content objects where we know it was produced by a {@code let} or
     * {@code param} variable (for private templates where we can find the callsites). This is
     * because the type checking we do at runtime is insufficiently strict in all backends to fully
     * trust just the type system.
     *
     * <p>This is a conservative set of conditions and leaves some opportunities on the table.
     */
    @Nullable
    private SanitizedContentKind getTrustedContentKindForNode(ExprHolderNode exprHolder) {
      return getTrustedContentKindForNode(exprHolder, /*followCalls=*/ true);
    }

    @Nullable
    private SanitizedContentKind getTrustedContentKindForNode(
        ExprHolderNode exprHolder, boolean followCalls) {
      ExprNode expr = Iterables.getOnlyElement(exprHolder.getExprList()).getRoot();
      if (!(expr instanceof VarRefNode)) {
        return null; // we only support trivial var refs
      }
      VarRefNode varRef = (VarRefNode) expr;
      if (varRef.getDefnDecl() instanceof LocalVar) {
        LocalVar var = (LocalVar) varRef.getDefnDecl();
        if (var.declaringNode() instanceof LetContentNode) {
          return ((LetContentNode) var.declaringNode()).getContentKind();
        }
      } else if (varRef.getDefnDecl() instanceof TemplateParam && followCalls) {
        TemplateParam param = (TemplateParam) varRef.getDefnDecl();
        return getTrustedContentKindForParameter(
            exprHolder.getNearestAncestor(TemplateNode.class), param);
      }
      return null;
    }

    private SanitizedContentKind getTrustedContentKindForParameter(
        TemplateNode template, TemplateParam param) {
      if (param.isInjected()) {
        return null; // can't validate injected parameters
      }
      if (!param.type().getKind().isKnownSanitizedContent()) {
        return null; // only care about sanitized types
      }
      if (SoyTreeUtils.allNodesOfType(template.getParent(), TemplateLiteralNode.class)
          .anyMatch(
              (templateLiteral) ->
                  !templateLiteral.isStaticCall()
                      && templateLiteral.getResolvedName().equals(template.getTemplateName()))) {
        // If a template is passed around into other templates, we cannot be sure of the trusted
        // content kind.
        return null;
      }
      SanitizedContentKind expectedKind = ((SanitizedType) param.type()).getContentKind();

      // if it is private we know that all callers are in this file.  Find them and check
      // if they are all passing the parameter with a consistent kind
      if (template.getVisibility() != Visibility.PRIVATE) {
        return null; // can only find all callers for private templates
      }
      for (CallBasicNode callNode :
          SoyTreeUtils.getAllNodesOfType(template.getParent(), CallBasicNode.class)) {
        if (callNode.isStaticCall()
            && callNode.getCalleeName().equals(template.getTemplateName())
            && !doesCallPassCompatibleContentForParameter(callNode, expectedKind, param.name())) {
          return null;
        }
      }
      return expectedKind;
    }

    /** Checks if the given call passes the parameter with a known safe content kind. */
    private boolean doesCallPassCompatibleContentForParameter(
        CallNode callNode, SanitizedContentKind expectedKind, String parameter) {
      for (CallParamNode callParam : callNode.getChildren()) {
        if (callParam.getKey().identifier().equals(parameter)) {
          if (callParam instanceof CallParamContentNode
              && ((CallParamContentNode) callParam).getContentKind() == expectedKind) {
            return true;
          } else if (callParam instanceof CallParamValueNode
              // We don't follow calls so we don't get lost in recursive templates
              && getTrustedContentKindForNode(
                      (CallParamValueNode) callParam, /* followCalls=*/ false)
                  == expectedKind) {
            return true;
          }
        }
      }
      return false;
    }

    /** Do nothing. */
    @Override
    protected void visitRawTextNode(RawTextNode rawTextNode) {
      // TODO: Possibly normalize raw text nodes by adding quotes around unquoted attributes with
      // non-noescape dynamic content to avoid the need for space escaping.
    }

    /** Grabs the inferred escaping directives from the node in string form. */
    private ImmutableList<SoyPrintDirective> getDirectivesForNode(SoyNode node) {
      ImmutableList.Builder<SoyPrintDirective> escapingDirectiveNames =
          new ImmutableList.Builder<>();
      for (EscapingMode escapingMode : inferences.getEscapingModesForNode(node)) {
        escapingDirectiveNames.add(printDirectives.get(escapingMode.directiveName));
      }
      return escapingDirectiveNames.build();
    }

    /** Sets the escaping directives we inferred on the node. */
    @Override
    protected void visitMsgFallbackGroupNode(MsgFallbackGroupNode node) {
      node.setEscapingDirectives(getDirectivesForNode(node));
      visitChildren(node);
    }

    /** Rewrite call targets. */
    @Override
    protected void visitCallNode(CallNode node) {
      ImmutableList<SoyPrintDirective> directives = getDirectivesForNode(node);
      // Only handle CallBasicNode.  The compiler attempts to enforce consistency in the type of
      // deltemplates but there is currently no strong guarantee that they are compatible.  So be
      // conservative here.
      if (node instanceof CallBasicNode) {
        ImmutableList<TemplateType> targets = inferences.lookupTemplates(node);
        if (!targets.isEmpty()) {
          directives =
              ShortCircuitables.filterDirectivesForKind(
                  Converters.contentKindfromSanitizedContentKind(
                      targets.get(0).getContentKind().getSanitizedContentKind()),
                  directives);
        }
      }
      node.setEscapingDirectives(directives);

      visitChildren(node);
    }

    /** Recurses to children. */
    @Override
    protected void visitSoyNode(SoyNode node) {
      if (node instanceof ParentSoyNode<?>) {
        visitChildren((ParentSoyNode<?>) node);
      }
    }
  }
}
