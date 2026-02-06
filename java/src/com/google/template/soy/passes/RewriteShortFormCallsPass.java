/*
 * Copyright 2020 Google Inc.
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

import static com.google.common.collect.ImmutableListMultimap.toImmutableListMultimap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.base.internal.QuoteStyle;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.compilermetrics.Impression;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprEquivalence;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.CallableExpr.ParamsStyle;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.CommandTagAttribute;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.types.RecordType;
import com.google.template.soy.types.SanitizedType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.TemplateType;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;

/** Rewrites short form calls to call nodes. */
@RunAfter({ResolveExpressionTypesPass.class, FinalizeTemplateRegistryPass.class})
@RunBefore({CheckTemplateCallsPass.class, MoreCallValidationsPass.class})
final class RewriteShortFormCallsPass implements CompilerFileSetPass {

  private static final SoyErrorKind OVERFLOW =
      SoyErrorKind.of(
          "Overflowed short form call rewriting.",
          Impression.ERROR_REWRITE_SHORT_FORM_CALLS_PASS_OVERFLOW);
  static final SoyErrorKind EXPECTED_NAMED_PARAMETERS =
      SoyErrorKind.of(
          "Expected named parameters or a single record parameter.",
          Impression.ERROR_REWRITE_SHORT_FORM_CALLS_PASS_EXPECTED_NAMED_PARAMETERS);

  private final ErrorReporter errorReporter;
  private final ExprEquivalence exprEquivalence;

  public RewriteShortFormCallsPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
    this.exprEquivalence = new ExprEquivalence();
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    for (SoyFileNode file : sourceFiles) {
      run(file, idGenerator);
    }
    return Result.CONTINUE;
  }

  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    ImmutableListMultimap<VarDefn, VarRefNode> vars =
        SoyTreeUtils.allNodesOfType(file, VarRefNode.class)
            .collect(toImmutableListMultimap(VarRefNode::getDefnDecl, v -> v));

    int maxDepth = 20; // safeguard
    AtomicBoolean mutated = new AtomicBoolean(true);
    // Must check recursively because mutations create new CallParamValueNode, which are also
    // processed.
    while (mutated.get() && --maxDepth > 0) {
      mutated.set(false);
      SoyTreeUtils.allNodes(file)
          .forEach(
              n -> {
                boolean m = false;
                if (n instanceof PrintNode) {
                  m = visitPrintNode((PrintNode) n, nodeIdGen);
                } else if (n instanceof LetValueNode) {
                  m = visitLetValueNode((LetValueNode) n, nodeIdGen, vars);
                } else if (n instanceof CallParamValueNode) {
                  m = visitCallParamValueNode((CallParamValueNode) n, nodeIdGen);
                }
                mutated.set(mutated.get() || m);
              });
    }

    if (maxDepth == 0) {
      errorReporter.report(file.getSourceLocation(), OVERFLOW);
    }
  }

  private boolean visitPrintNode(PrintNode node, IdGenerator nodeIdGen) {
    if (node.getParent() instanceof HtmlOpenTagNode) {
      return false;
    }

    CallBasicNode call = convert(node.getExpr(), nodeIdGen);
    if (call != null) {
      node.getParent().replaceChild(node, call);
      return true;
    }
    return false;
  }

  private boolean visitLetValueNode(
      LetValueNode node, IdGenerator nodeIdGen, ImmutableListMultimap<VarDefn, VarRefNode> vars) {
    CallBasicNode call = convert(node.getExpr(), nodeIdGen);
    if (call != null) {
      TemplateType templateType = call.getCalleeExpr().getType().asType(TemplateType.class);
      SourceLocation loc = node.getSourceLocation();
      SanitizedContentKind kind = templateType.getContentKind().getSanitizedContentKind();
      LetContentNode contentNode =
          new LetContentNode(
              nodeIdGen.genId(),
              loc,
              loc,
              node.getVarName(),
              loc,
              getKindAttr(loc, kind),
              ErrorReporter.exploding());
      contentNode.addChild(call);
      contentNode.getVar().setType(SanitizedType.getTypeForContentKind(kind));
      node.getParent().replaceChild(node, contentNode);
      for (VarRefNode varRefNode : vars.get(node.getVar())) {
        varRefNode.setDefn(contentNode.getVar());
      }
      return true;
    }
    return false;
  }

  private boolean visitCallParamValueNode(CallParamValueNode node, IdGenerator nodeIdGen) {
    CallBasicNode call = convert(node.getExpr(), nodeIdGen);
    if (call != null) {
      TemplateType templateType = call.getCalleeExpr().getType().asType(TemplateType.class);
      SourceLocation loc = node.getSourceLocation();
      SanitizedContentKind kind = templateType.getContentKind().getSanitizedContentKind();
      CallParamContentNode contentNode =
          new CallParamContentNode(
              nodeIdGen.genId(),
              loc,
              loc,
              node.getKey(),
              getKindAttr(loc, kind),
              ErrorReporter.exploding());
      contentNode.addChild(call);
      node.getParent().replaceChild(node, contentNode);
      return true;
    }
    return false;
  }

  private static CommandTagAttribute getKindAttr(SourceLocation loc, SanitizedContentKind kind) {
    return new CommandTagAttribute(
        Identifier.create("kind", loc), QuoteStyle.DOUBLE, kind.asAttributeValue(), loc, loc);
  }

  @Nullable
  private CallBasicNode convert(ExprRootNode expr, IdGenerator nodeIdGen) {
    if (!(expr.getRoot() instanceof FunctionNode)) {
      return null;
    }
    FunctionNode fnNode = (FunctionNode) expr.getRoot();
    if (fnNode.hasStaticName()) {
      return null;
    }
    ExprNode nameExpr = fnNode.getNameExpr();

    // Ignore function/extern pointers.
    if (SoyTypes.isKindOrUnionOfKind(nameExpr.getType(), Kind.FUNCTION)) {
      return null;
    }

    if (!SoyTypes.isKindOrUnionOfKind(nameExpr.getType(), Kind.TEMPLATE)) {
      return null;
    }

    boolean dataParam = isShortFormDataCall(fnNode);
    if (fnNode.getParamsStyle() == ParamsStyle.POSITIONAL && !dataParam) {
      errorReporter.report(fnNode.getSourceLocation(), EXPECTED_NAMED_PARAMETERS);
      // Only report error once.
      expr.replaceChild(0, new StringNode("$error", QuoteStyle.SINGLE, expr.getSourceLocation()));
      return null;
    }

    SourceLocation loc = expr.getSourceLocation();
    ImmutableList<CommandTagAttribute> attrs = ImmutableList.of();
    if (dataParam) {
      CommandTagAttribute attr =
          new CommandTagAttribute(
              Identifier.create("data", loc),
              QuoteStyle.DOUBLE,
              ImmutableList.of(fnNode.getParam(0)),
              loc);
      attr.valueAsExprList().forEach(root -> root.setType(root.getRoot().getType()));
      attrs = ImmutableList.of(attr);
    }

    ExprNode callee = nameExpr.copy(new CopyState());
    SoyType type = callee.getType();
    CallBasicNode call =
        new CallBasicNode(
            nodeIdGen.genId(), loc, loc, callee, attrs, false, ErrorReporter.exploding());
    call.setOriginalShortFormExprEquivalence(exprEquivalence.wrap(expr.copy(new CopyState())));
    call.getCalleeExpr().setType(type);
    if (!dataParam) {
      for (int i = 0; i < fnNode.getParamNames().size(); i++) {
        Identifier id = fnNode.getParamNames().get(i);
        CallParamValueNode valueNode =
            new CallParamValueNode(
                nodeIdGen.genId(), id.location(), id, fnNode.getParam(i).copy(new CopyState()));
        valueNode.getExpr().setType(fnNode.getParam(i).getType());
        call.addChild(valueNode);
      }
    }

    // Allow CheckTemplateCallsPass to find stricthtml violations. This will be more strict than if
    // the HtmlRewriter had been run on the transformed AST because we have to assume PCDATA state.
    call.setIsPcData(true);

    return call;
  }

  private static boolean isShortFormDataCall(FunctionNode fnNode) {
    if (fnNode.getParamsStyle() != ParamsStyle.POSITIONAL) {
      return false;
    }
    if (fnNode.getParams().size() != 1) {
      return false;
    }
    ExprNode param = fnNode.getParam(0);
    return RecordType.EMPTY_RECORD.isAssignableFromLoose(param.getType());
  }
}
