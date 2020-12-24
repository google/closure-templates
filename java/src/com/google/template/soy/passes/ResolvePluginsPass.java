/*
 * Copyright 2018 Google Inc.
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

import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.exprtree.CallableExprBuilder;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.passes.LocalVariablesNodeVisitor.ExprVisitor;
import com.google.template.soy.plugin.restricted.SoySourceFunction;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.types.SoyType;
import java.util.Optional;

/**
 * Populates the {@link FunctionNode} and {@link PrintDirectiveNode} with their plugin instances
 * based on a registry of such names. Also resolves functions that are imported symbols (e.g. for
 * proto init).
 */
@RunBefore(SoyConformancePass.class)
final class ResolvePluginsPass implements CompilerFilePass {

  private final PluginResolver resolver;

  ResolvePluginsPass(PluginResolver resolver) {
    this.resolver = resolver;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    new LocalVariablesNodeVisitor(new Visitor()).exec(file);
  }

  private class Visitor extends LocalVariablesNodeVisitor.NodeVisitor {

    private final LocalVariablesNodeVisitor.ExprVisitor exprVisitor =
        new LocalVariablesNodeVisitor.ExprVisitor() {
          @Override
          protected void visitFunctionNode(FunctionNode node) {
            visitChildren(node);

            if (node.isResolved()) {
              return;
            }

            // If the function name is an expression then attempt to set the soy function field.
            if (!node.hasStaticName()) {
              setSoyFunctionForNameExpr(node);
              return;
            }

            // Built-in and plug-in functions take precedence over var refs.
            Object impl =
                resolver.lookupSoyFunction(
                    node.getStaticFunctionName(), node.numChildren(), node.getSourceLocation());
            if (impl != null) {
              node.setSoyFunction(impl);
              return;
            }

            // If the name of the function is resolvable to a var def then replace the function
            // identifier with a function name expression. Currently only proto message types are
            // represented as VarDefn and all other symbols have been turned back into GlobalNodes
            // by RestoreGlobalsPass. But eventually this could be modified to have built-in/plug-in
            // functions imported as well.
            // Note that this only handles non-nested proto types. Nested proto types appear at this
            // stage as METHOD_CALL(VARREF) and await handling in ResolveDottedImportsPass.
            VarDefn varDefn = getLocalVariables().lookup(node.getStaticFunctionName());
            if (varDefn != null) {
              VarRefNode functionRef =
                  new VarRefNode(
                      node.getStaticFunctionName(), node.getIdentifier().location(), varDefn);
              Object soyFunction = getSoyFunctionForExpr(functionRef);
              if (soyFunction != null) {
                FunctionNode newFunct =
                    CallableExprBuilder.builder(node)
                        .setIdentifier(null)
                        .setFunctionExpr(functionRef)
                        .buildFunction();
                // Set the soy function field to "resolve" the function.
                newFunct.setSoyFunction(soyFunction);
                node.getParent().replaceChild(node, newFunct);
              }
            }
          }
        };

    @Override
    protected ExprVisitor getExprVisitor() {
      return exprVisitor;
    }

    @Override
    protected void visitPrintDirectiveNode(PrintDirectiveNode directiveNode) {
      super.visitPrintDirectiveNode(directiveNode);
      String name = directiveNode.getName();

      // If a template uses a print directive that doesn't exist, check if a function with the same
      // name does exist. This is likely a print directive being migrated with
      // SoyFunctionSignature#callableAsDeprecatedPrintDirective.
      Optional<SoySourceFunction> aliasedFunction =
          resolver.getFunctionCallableAsPrintDirective(name, directiveNode.getSourceLocation());
      if (aliasedFunction.isPresent()) {
        directiveNode.setPrintDirectiveFunction(aliasedFunction.get());
      } else {
        directiveNode.setPrintDirective(
            resolver.lookupPrintDirective(
                name, directiveNode.getExprList().size(), directiveNode.getSourceLocation()));
      }
    }
  }

  /**
   * For a function without a static name, calls {@link FunctionNode#setSoyFunction} with an
   * appropriate value based on the function's name expression. Setting the soy function makes
   * various code constructs more convenient (switch statements, visitors, etc).
   */
  static void setSoyFunctionForNameExpr(FunctionNode function) {
    Object fct = getSoyFunctionForExpr(function.getNameExpr());
    if (fct != null) {
      function.setSoyFunction(BuiltinFunction.PROTO_INIT);
    }
  }

  static Object getSoyFunctionForExpr(ExprNode expr) {
    if (expr.getType().getKind() == SoyType.Kind.PROTO_TYPE) {
      return BuiltinFunction.PROTO_INIT;
    }
    return null;
  }
}
