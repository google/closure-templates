/*
 * Copyright 2021 Google Inc.
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

package com.google.template.soy.jbcsrc;

import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.isDefinitelyAssignableFrom;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.exprtree.AbstractLocalVarDefn;
import com.google.template.soy.exprtree.DataAccessNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.jbcsrc.ExpressionCompiler.BasicExpressionCompiler;
import com.google.template.soy.jbcsrc.internal.SoyClassWriter;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.CodeBuilder;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyRuntimeType;
import com.google.template.soy.jbcsrc.restricted.Statement;
import com.google.template.soy.jbcsrc.restricted.TypeInfo;
import com.google.template.soy.jbcsrc.shared.Names;
import com.google.template.soy.soytree.ConstNode;
import com.google.template.soy.soytree.PartialFileSetMetadata;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.SoyType;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

/** Compiles byte code for {@link ConstNode}s. */
public final class ConstantsCompiler {

  static final TemplateAnalysis ALL_RESOLVED =
      new TemplateAnalysis() {
        @Override
        public boolean isResolved(VarRefNode ref) {
          // Only locals in list comprehension and other globals may possibly be referenced.
          return true;
        }

        @Override
        public boolean isResolved(DataAccessNode ref) {
          // Data access is not allowed in const context.
          throw new UnsupportedOperationException();
        }
      };

  private final ConstNode constant;
  private final SoyClassWriter writer;
  private final JavaSourceFunctionCompiler javaSourceFunctionCompiler;
  private final PartialFileSetMetadata fileSetMetadata;

  ConstantsCompiler(
      ConstNode constant,
      SoyClassWriter writer,
      JavaSourceFunctionCompiler javaSourceFunctionCompiler,
      PartialFileSetMetadata fileSetMetadata) {
    this.constant = constant;
    this.writer = writer;
    this.javaSourceFunctionCompiler = javaSourceFunctionCompiler;
    this.fileSetMetadata = fileSetMetadata;
  }

  static SoyRuntimeType getConstantRuntimeType(SoyType type) {
    return SoyRuntimeType.getUnboxedType(type).orElseGet(() -> SoyRuntimeType.getBoxedType(type));
  }

  static Method getConstantMethod(String symbol, SoyType type) {
    return new Method(
        symbol,
        getConstantRuntimeType(type).runtimeType(),
        new Type[] {BytecodeUtils.RENDER_CONTEXT_TYPE});
  }

  public void compile() {
    Method method = getConstantMethod(constant.getVar().name(), constant.getVar().type());

    Label start = new Label();
    Label end = new Label();
    TemplateVariableManager variableSet =
        new TemplateVariableManager(
            TypeInfo.createClass(
                    Names.javaClassNameFromSoyNamespace(
                        constant.getNearestAncestor(SoyFileNode.class).getNamespace()))
                .type(),
            method,
            ImmutableList.of(StandardNames.RENDER_CONTEXT),
            start,
            end,
            /*isStatic=*/ true);
    Expression renderContext = variableSet.getVariable(StandardNames.RENDER_CONTEXT);
    TemplateParameterLookup variables =
        new ConstantVariables(variableSet, new RenderContextExpression(renderContext));

    BasicExpressionCompiler expressionCompiler =
        ExpressionCompiler.createBasicCompiler(
            constant,
            ALL_RESOLVED,
            variables,
            variableSet,
            javaSourceFunctionCompiler,
            fileSetMetadata);

    SoyExpression body = expressionCompiler.compile(constant.getExpr());
    Preconditions.checkArgument(
        isDefinitelyAssignableFrom(body.soyRuntimeType().runtimeType(), method.getReturnType()));

    new Statement() {
      @Override
      protected void doGen(CodeBuilder adapter) {
        adapter.mark(start);
        body.gen(adapter);
        adapter.mark(end);
        adapter.returnValue();
        variableSet.generateTableEntries(adapter);
      }
    }.writeMethod(methodAccess(), method, writer);
  }

  private int methodAccess() {
    // Same issue as TemplateCompiler#methodAccess
    return (constant.isExported() ? Opcodes.ACC_PUBLIC : 0) | Opcodes.ACC_STATIC;
  }

  static final class ConstantVariables implements TemplateParameterLookup {
    private final TemplateVariableManager variableSet;
    private final RenderContextExpression renderContext;

    ConstantVariables(TemplateVariableManager variableSet, RenderContextExpression renderContext) {
      this.renderContext = renderContext;
      this.variableSet = variableSet;
    }

    UnsupportedOperationException unsupported() {
      return new UnsupportedOperationException(
          "This method isn't supported in constants compilation context");
    }

    @Override
    public Expression getParam(TemplateParam param) {
      throw unsupported();
    }

    @Override
    public Expression getParamsRecord() {
      throw unsupported();
    }

    @Override
    public Expression getIjRecord() {
      throw unsupported();
    }

    @Override
    public Expression getLocal(AbstractLocalVarDefn<?> local) {
      return variableSet.getVariable(local.name());
    }

    @Override
    public Expression getLocal(SyntheticVarName varName) {
      throw unsupported();
    }

    @Override
    public RenderContextExpression getRenderContext() {
      return renderContext;
    }

    @Override
    public JbcSrcPluginContext getPluginContext() {
      throw unsupported();
    }
  }
}
