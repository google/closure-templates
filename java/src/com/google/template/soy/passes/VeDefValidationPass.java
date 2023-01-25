/*
 * Copyright 2015 Google Inc.
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
import com.google.template.soy.data.SoyProtoValue;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.AbstractReturningExprNodeVisitor;
import com.google.template.soy.exprtree.BooleanNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FloatNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.NullNode;
import com.google.template.soy.exprtree.ProtoEnumValueNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.logging.ValidatedLoggingConfig;
import com.google.template.soy.logging.ValidatedLoggingConfig.ValidatedLoggableElement;
import com.google.template.soy.passes.CompilerFileSetPass.Result;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.soytree.ConstNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.types.ProtoImportType;
import com.google.template.soy.types.SoyProtoType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;

/** Validates the arguments to ve_def, and validates the VEs. */
@RunAfter({ResolveExpressionTypesPass.class})
final class VeDefValidationPass implements CompilerFileSetPass {

  private static final SoyErrorKind VE_DEF_OUTSIDE_CONST =
      SoyErrorKind.of("Visual elements defined with ve_def() must be assigned to a constant.");

  private static final SoyErrorKind BAD_VE_DEF_NAME =
      SoyErrorKind.of("The first argument to ve_def() must be an string literal.");

  private static final SoyErrorKind BAD_VE_DEF_ID =
      SoyErrorKind.of("The second argument to ve_def() must be an integer literal.");

  private static final SoyErrorKind BAD_VE_DEF_DATA_PROTO_TYPE =
      SoyErrorKind.of("The third argument to ve_def() must be proto type or 'null'.");

  private static final SoyErrorKind BAD_VE_DEF_METADATA =
      SoyErrorKind.of(
          "The fourth argument to ve_def() must be an proto init expression of "
              + "LoggableElementMetadata. All fields must be literals.");

  private final ValidatedLoggingConfig validatedLoggingConfig;
  private final ErrorReporter errorReporter;
  private final ProtoEvalVisitor protoEvalVisitor;

  VeDefValidationPass(ValidatedLoggingConfig validatedLoggingConfig, ErrorReporter errorReporter) {
    this.validatedLoggingConfig = validatedLoggingConfig;
    this.errorReporter = errorReporter;
    this.protoEvalVisitor = new ProtoEvalVisitor(errorReporter);
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    Set<ExprNode> vedefsInConstNodes = new HashSet<>();
    List<ValidatedLoggableElement> vedefs = new ArrayList<>();

    for (SoyFileNode file : sourceFiles) {
      // Find all ve_defs() that are assigned to a {const}, all others are errors.
      SoyTreeUtils.allNodesOfType(file, ConstNode.class)
          .forEach(c -> maybeAddVeDefFromConst(c, vedefsInConstNodes));

      SoyTreeUtils.allNodesOfType(file, FunctionNode.class)
          .filter(VeDefValidationPass::isVeDef)
          .forEach(
              func -> {
                if (!vedefsInConstNodes.contains(func)) {
                  errorReporter.report(func.getSourceLocation(), VE_DEF_OUTSIDE_CONST);
                }
                buildVeDefAndValidate(func, vedefs);
              });
    }
    ValidatedLoggingConfig.validate(validatedLoggingConfig, vedefs, errorReporter);
    return Result.CONTINUE;
  }

  private void maybeAddVeDefFromConst(ConstNode constNode, Set<ExprNode> veDefsInConstNodes) {
    if (isVeDef(constNode.getExpr().getRoot())) {
      veDefsInConstNodes.add(constNode.getExpr().getRoot());
    }
  }

  private void buildVeDefAndValidate(FunctionNode func, List<ValidatedLoggableElement> vedefs) {
    if (func.numChildren() < 2) {
      // Wrong # argument errors are already thrown.
      return;
    }

    if (!(func.getChild(0) instanceof StringNode)) {
      errorReporter.report(func.getChild(0).getSourceLocation(), BAD_VE_DEF_NAME);
      return;
    }
    String veName = ((StringNode) func.getChild(0)).getValue();

    if (!(func.getChild(1) instanceof IntegerNode)) {
      errorReporter.report(func.getChild(1).getSourceLocation(), BAD_VE_DEF_ID);
      return;
    }
    long id = ((IntegerNode) func.getChild(1)).getValue();

    final Optional<String> dataProtoType;
    if (func.getChildren().size() < 3 || func.getChild(2) instanceof NullNode) {
      dataProtoType = Optional.empty();
    } else {
      if (!(func.getChild(2).getType() instanceof ProtoImportType)) {
        errorReporter.report(func.getChild(2).getSourceLocation(), BAD_VE_DEF_DATA_PROTO_TYPE);
        return;
      }
      dataProtoType = Optional.of(func.getChild(2).getType().toString());
    }

    final Optional<Object> staticMetadata;
    if (func.getChildren().size() < 4) {
      staticMetadata = Optional.empty();
    } else {
      if (!func.getChild(3).getType().toString().equals("soy.LoggableElementMetadata")) {
        errorReporter.report(func.getChild(3).getSourceLocation(), BAD_VE_DEF_METADATA);
        return;
      }
      SoyValue protoInstance = protoEvalVisitor.exec(func.getChild(3));
      if (!(protoInstance instanceof SoyProtoValue)) {
        return;
      }
      staticMetadata = Optional.of(((SoyProtoValue) protoInstance).getProto());
    }

    vedefs.add(
        ValidatedLoggingConfig.ValidatedLoggableElement.create(
            veName, id, dataProtoType, staticMetadata, func.getSourceLocation()));
  }

  private static boolean isVeDef(ExprNode node) {
    if (!(node instanceof FunctionNode)) {
      return false;
    }
    FunctionNode functionNode = (FunctionNode) node;
    return functionNode.isResolved() && functionNode.getSoyFunction() == BuiltinFunction.VE_DEF;
  }

  /**
   * If the expression is a proto init with only literal fields, return the equivalent
   * SoyProtoValue, otherwise return null.
   */
  private static class ProtoEvalVisitor extends AbstractReturningExprNodeVisitor<SoyValue> {

    private final ErrorReporter errorReporter;

    ProtoEvalVisitor(ErrorReporter errorReporter) {
      this.errorReporter = errorReporter;
    }

    @Override
    @Nullable
    protected SoyValue visitFunctionNode(FunctionNode node) {
      Object soyFunction = node.getSoyFunction();
      if (!(soyFunction instanceof BuiltinFunction)) {
        return null;
      }
      BuiltinFunction builtin = (BuiltinFunction) soyFunction;
      if (builtin != BuiltinFunction.PROTO_INIT) {
        return null;
      }
      SoyProtoType soyProto = (SoyProtoType) node.getType();
      ImmutableList<Identifier> paramNames = node.getParamNames();
      SoyProtoValue.Builder builder = new SoyProtoValue.Builder(soyProto.getDescriptor());
      for (int i = 0; i < node.numChildren(); i++) {
        SoyValue fieldValue = visit(node.getChild(i));
        if (fieldValue == null) {
          return null;
        }
        String fieldName = paramNames.get(i).identifier();
        if (!builder.hasField(fieldName)) {
          // Ignore fields that don't exist. builder.setField() will throw a NPE on unknown field.
          // A separate compilation error will have already been thrown for the bad proto init.
          continue;
        }
        builder.setField(fieldName, fieldValue);
      }
      return builder.build();
    }

    @Override
    protected SoyValue visitExprRootNode(ExprRootNode node) {
      return visit(node.getRoot());
    }

    @Override
    protected SoyValue visitNullNode(NullNode node) {
      return NullData.INSTANCE;
    }

    @Override
    protected SoyValue visitBooleanNode(BooleanNode node) {
      return BooleanData.forValue(node.getValue());
    }

    @Override
    protected SoyValue visitIntegerNode(IntegerNode node) {
      return IntegerData.forValue(node.getValue());
    }

    @Override
    protected SoyValue visitFloatNode(FloatNode node) {
      return FloatData.forValue(node.getValue());
    }

    @Override
    protected SoyValue visitStringNode(StringNode node) {
      return StringData.forValue(node.getValue());
    }

    @Override
    protected SoyValue visitProtoEnumValueNode(ProtoEnumValueNode node) {
      return IntegerData.forValue(node.getValue());
    }

    @Override
    @Nullable
    protected SoyValue visitExprNode(ExprNode node) {
      errorReporter.report(node.getSourceLocation(), BAD_VE_DEF_METADATA);
      return null;
    }
  }
}
