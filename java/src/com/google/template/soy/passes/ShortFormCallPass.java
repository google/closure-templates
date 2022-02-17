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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.passes.PassManager.AstRewrites;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.TemplateImportType;
import com.google.template.soy.types.TemplateType;

/** Rewrites short form calls to call nodes. */
@RunAfter({ResolveExpressionTypesPass.class, FinalizeTemplateRegistryPass.class})
final class ShortFormCallPass implements CompilerFileSetPass {

  private final ErrorReporter errorReporter;
  private final AstRewrites astRewrites;

  ShortFormCallPass(AstRewrites astRewrites, ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
    this.astRewrites = astRewrites;
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    if (errorReporter.hasErrors()) {
      return Result.CONTINUE;
    }
    for (SoyFileNode file : sourceFiles) {
      run(file, idGenerator);
    }
    return Result.CONTINUE;
  }

  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (TemplateNode template : file.getTemplates()) {
      // It is OK for Kythe to depend on the rewritten call nodes since they have appropriate
      // source locations to map back to the original template. For tricorder fixes, we need
      // to make sure that we are only rewriting human-written call nodes.
      if (astRewrites != AstRewrites.TRICORDER && astRewrites != AstRewrites.NONE) {
        for (PrintNode printNode : SoyTreeUtils.getAllNodesOfType(template, PrintNode.class)) {
          process(printNode, nodeIdGen);
        }
      }
    }
  }

  private void process(PrintNode printNode, IdGenerator nodeIdGen) {
    if (printNode.getExpr().getRoot() instanceof FunctionNode
        && !((FunctionNode) printNode.getExpr().getRoot()).hasStaticName()
        && ((FunctionNode) printNode.getExpr().getRoot()).getNameExpr() != null) {
      FunctionNode fnNode = (FunctionNode) printNode.getExpr().getRoot();
      ExprNode callee;
      SoyType type;
      if (fnNode.getNameExpr() instanceof VarRefNode
          && fnNode.getNameExpr().getType() instanceof TemplateImportType) {
        TemplateLiteralNode templateLiteralNode =
            TemplateLiteralNode.forVarRef((VarRefNode) fnNode.getNameExpr());
        templateLiteralNode.setStaticCall(true);
        callee = templateLiteralNode;
        type = ((TemplateImportType) fnNode.getNameExpr().getType()).getBasicTemplateType();
        templateLiteralNode.setType(type);
      } else if (fnNode.getNameExpr().getType() instanceof TemplateType) {
        callee = fnNode.getNameExpr().copy(new CopyState());
        type = callee.getType();
      } else {
        return;
      }
      CallBasicNode call =
          new CallBasicNode(
              nodeIdGen.genId(),
              printNode.getSourceLocation(),
              printNode.getExpr().getSourceLocation(),
              callee,
              ImmutableList.of(),
              false,
              errorReporter);
      call.getCalleeExpr().setType(type);
      for (int i = 0; i < fnNode.getParamNames().size(); i++) {
        Identifier identifier = fnNode.getParamNames().get(i);
        CallParamValueNode valueNode =
            new CallParamValueNode(
                nodeIdGen.genId(),
                identifier.location(),
                identifier,
                fnNode.getParams().get(i).copy(new CopyState()));
        valueNode.getExpr().setType(fnNode.getParams().get(i).getType());
        call.addChild(valueNode);
      }
      printNode.getParent().replaceChild(printNode, call);
    }
  }
}
