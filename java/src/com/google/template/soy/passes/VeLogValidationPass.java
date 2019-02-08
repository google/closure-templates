/*
 * Copyright 2017 Google Inc.
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

import com.google.common.base.Optional;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.VeLiteralNode;
import com.google.template.soy.logging.LoggingFunction;
import com.google.template.soy.logging.ValidatedLoggingConfig;
import com.google.template.soy.logging.ValidatedLoggingConfig.ValidatedLoggableElement;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.MsgBlockNode;
import com.google.template.soy.soytree.SoyNode.MsgSubstUnitNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.VeLogNode;
import com.google.template.soy.types.BoolType;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.VeType;

/**
 * Validates uses of the {@code velog} command and {@code ve_data} expression.
 *
 * <p>Must run after:
 *
 * <ul>
 *   <li>VeRewritePass since that rewrites VE syntactic sugar
 *   <li>ResolveTypesPass since we rely on type resolution data
 *   <li>ResolveFunctions pass since we need to validate the use of {@link LoggingFunction}
 *       invocations
 *   <li>VeLogRewritePass since that rewrites more VE syntactic sugar
 * </ul>
 */
final class VeLogValidationPass extends CompilerFilePass {
  private static final SoyErrorKind UNEXPECTED_CONFIG =
      SoyErrorKind.of(
          "Unexpected ''data'' attribute for logging element ''{0}'', there is no configured "
              + "''proto_extension_type'' in the logging configuration for this element. "
              + "Did you forget to configure it?");
  private static final SoyErrorKind UNEXPECTED_DATA =
      SoyErrorKind.of(
          "Unexpected data argument. The data (''{0}'') must match with the VE passed as the "
              + "first argument (''{1}''). The VE is type ''{2}'' which means there cannot be any "
              + "data.");
  private static final SoyErrorKind WRONG_TYPE =
      SoyErrorKind.of("Expected an expression of type ''{0}'', instead got ''{1}''.");
  private static final SoyErrorKind LOGONLY_DISALLOWED_IN_MSG =
      SoyErrorKind.of(
          "The logonly attribute may not be set on '''{velog}''' nodes in '''{msg}''' context. "
              + "Consider moving the logonly content into another template and calling it, or "
              + "refactoring your '''{msg}''' into multiple distinct messages.");
  private static final SoyErrorKind REQUIRE_STRICTHTML =
      SoyErrorKind.of(
          "The '{'velog ...'}' command can only be used in templates with stricthtml=\"true\".");

  private static final SoyErrorKind INVALID_LOGGING_FUNCTION_LOCATION =
      SoyErrorKind.of(
          "The logging function ''{0}'' can only be evaluated in a print command that is the "
              + "only direct child of an html attribute value.{1}",
          SoyErrorKind.StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind NO_PRINT_DIRECTIVES =
      SoyErrorKind.of(
          "The logging function ''{0}'' can only be evaluated in a print command with no print "
              + "directives.");

  private static final SoyErrorKind UNKNOWN_PROTO =
      SoyErrorKind.of("Unknown proto type ''{0}'' configured for use with this VE.");
  private static final SoyErrorKind BAD_DATA_TYPE =
      SoyErrorKind.of(
          "Illegal VE metadata type ''{0}'' for this VE. The metadata must be a proto.");
  private static final SoyErrorKind INVALID_VE =
      SoyErrorKind.of(
          "The velog command requires a VE identifier, a call to ''ve(..)'' or a call to "
              + "''ve_data(...)'', found an expression of type ''{0}''.");

  private final ErrorReporter reporter;
  private final ValidatedLoggingConfig loggingConfig;
  private final SoyTypeRegistry typeRegistry;

  VeLogValidationPass(
      ErrorReporter reporter, ValidatedLoggingConfig loggingConfig, SoyTypeRegistry typeRegistry) {
    this.reporter = reporter;
    this.loggingConfig = loggingConfig;
    this.typeRegistry = typeRegistry;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (TemplateNode template : file.getChildren()) {
      for (FunctionNode node :
          SoyTreeUtils.getAllFunctionInvocations(template, BuiltinFunction.VE_DATA)) {
        validateVeDataFunctionNode(node);
      }
      for (VeLogNode node : SoyTreeUtils.getAllNodesOfType(template, VeLogNode.class)) {
        if (template.isStrictHtml()) {
          validateVeLogNode(node);
        } else {
          reporter.report(node.getVeDataExpression().getSourceLocation(), REQUIRE_STRICTHTML);
        }
      }
      // We need to validate logging functions.  The rules are
      // 1. logging functions can only be the direct children of PrintNodes
      // 2. the print nodes must be direct children of HtmlAttributeValueNodes
      //
      // However, because there is no way (currently) to navigate from an ExprNode to the SoyNode
      // which owns it, we need to do this multi-phase traversal to ensure the correct parenting
      // hierarchy.
      for (ExprHolderNode holderNode :
          SoyTreeUtils.getAllNodesOfType(template, SoyNode.ExprHolderNode.class)) {
        for (ExprRootNode rootNode : holderNode.getExprList()) {
          for (FunctionNode function :
              SoyTreeUtils.getAllNodesOfType(rootNode, FunctionNode.class)) {
            if (function.getSoyFunction() instanceof LoggingFunction) {
              validateLoggingFunction(holderNode, function);
            }
          }
        }
      }
    }
  }

  private void validateLoggingFunction(ExprHolderNode holderNode, FunctionNode function) {
    if (function.getParent().getKind() != ExprNode.Kind.EXPR_ROOT_NODE) {
      reporter.report(
          function.getSourceLocation(),
          INVALID_LOGGING_FUNCTION_LOCATION,
          function.getFunctionName(),
          " It is part of complex expression.");
      return;
    }
    if (holderNode.getKind() != SoyNode.Kind.PRINT_NODE) {
      reporter.report(
          function.getSourceLocation(),
          INVALID_LOGGING_FUNCTION_LOCATION,
          function.getFunctionName(),
          " It isn't in a print node.");
      return;
    }
    PrintNode printNode = (PrintNode) holderNode;
    if (printNode.numChildren() != 0) {
      reporter.report(
          printNode.getChild(0).getSourceLocation(),
          NO_PRINT_DIRECTIVES,
          function.getFunctionName());
    }
    if (holderNode.getParent().getKind() != SoyNode.Kind.HTML_ATTRIBUTE_VALUE_NODE) {
      reporter.report(
          function.getSourceLocation(),
          INVALID_LOGGING_FUNCTION_LOCATION,
          function.getFunctionName(),
          " It isn't the direct child of an attribute value.");
      return;
    }
    if (holderNode.getParent().numChildren() > 1) {
      reporter.report(
          function.getSourceLocation(),
          INVALID_LOGGING_FUNCTION_LOCATION,
          function.getFunctionName(),
          " It has sibling nodes in the attribute value.");
      return;
    }
  }

  /** Type checks both expressions and assigns the {@link VeLogNode#getLoggingId()} field. */
  private void validateVeLogNode(VeLogNode node) {
    Identifier veName = null;
    if (node.getVeDataExpression().getRoot().getKind() == ExprNode.Kind.FUNCTION_NODE) {
      FunctionNode fn = (FunctionNode) node.getVeDataExpression().getRoot();
      Object soyFunction = fn.getSoyFunction();
      if (soyFunction.equals(BuiltinFunction.VE_DATA)) {
        if (fn.getChild(0).getKind() == ExprNode.Kind.VE_LITERAL_NODE) {
          // TODO(b/71641483): remove this once we have runtime support for dynamic VEs.
          VeLiteralNode ve = (VeLiteralNode) fn.getChild(0);
          veName = ve.getName();
        }
      }
    }

    if (veName == null) {
      reporter.report(
          node.getVeDataExpression().getSourceLocation(),
          INVALID_VE,
          node.getVeDataExpression().getRoot().getType());
      return;
    }

    ValidatedLoggableElement config = loggingConfig.getElement(veName.identifier());

    if (config != null) {
      // A null config means this velog statement has an unknown VE name. This error will have
      // already been reported by validateVeDataFunctionNode, so continue on the best we can.
      node.setLoggingId(config.getId());
      if (node.getConfigExpression() != null) {
        SoyType type = node.getConfigExpression().getType();
        Optional<String> protoName = config.getProtoName();
        if (!protoName.isPresent()) {
          reporter.report(
              node.getConfigExpression().getSourceLocation(),
              UNEXPECTED_CONFIG,
              veName.identifier());
        } else if (type.getKind() != Kind.ERROR
            && (type.getKind() != Kind.PROTO
                || !((SoyProtoType) type).getDescriptor().getFullName().equals(protoName.get()))) {
          reporter.report(
              node.getConfigExpression().getSourceLocation(), WRONG_TYPE, protoName.get(), type);
        }
      }
    }

    if (node.getLogonlyExpression() != null) {
      // check to see if it is in a msg node.  logonly is disallowed in msg nodes because we don't
      // have an implementation strategy.
      if (isInMsgNode(node)) {
        reporter.report(node.getLogonlyExpression().getSourceLocation(), LOGONLY_DISALLOWED_IN_MSG);
      }
      SoyType type = node.getLogonlyExpression().getType();
      if (type.getKind() != Kind.BOOL) {
        reporter.report(
            node.getLogonlyExpression().getSourceLocation(),
            WRONG_TYPE,
            BoolType.getInstance(),
            type);
      }
    }
  }

  private void validateVeDataFunctionNode(FunctionNode node) {
    if (node.numChildren() < 1 || node.numChildren() > 2) {
      return; // an error has already been reported
    }
    ExprNode veExpr = node.getChild(0);
    ExprNode dataExpr = node.getChild(1);

    if (veExpr.getType().getKind() != Kind.ERROR) {
      if (veExpr.getType().getKind() != Kind.VE) {
        reporter.report(veExpr.getSourceLocation(), WRONG_TYPE, "ve", veExpr.getType());
      } else if (dataExpr.getType().getKind() != Kind.NULL) {
        VeType veType = (VeType) veExpr.getType();
        SoyType dataType = dataExpr.getType();
        if (!veType.getDataType().isPresent()) {
          reporter.report(
              dataExpr.getSourceLocation(),
              UNEXPECTED_DATA,
              dataExpr.toSourceString(),
              veExpr.toSourceString(),
              veType);
        } else {
          SoyType veDataType = typeRegistry.getType(veType.getDataType().get());
          if (veDataType == null) {
            reporter.report(veExpr.getSourceLocation(), UNKNOWN_PROTO, veType.getDataType().get());
          } else if (veDataType.getKind() != Kind.PROTO) {
            reporter.report(veExpr.getSourceLocation(), BAD_DATA_TYPE, veDataType);
          } else if (!dataType.equals(veDataType)) {
            reporter.report(
                dataExpr.getSourceLocation(), WRONG_TYPE, veType.getDataType().get(), dataType);
          }
        }
      }
    }
  }

  private static boolean isInMsgNode(SoyNode node) {
    if (node instanceof MsgNode) {
      return true;
    }
    ParentSoyNode<?> parent = node.getParent();
    if (parent instanceof MsgBlockNode || parent instanceof MsgSubstUnitNode) {
      return isInMsgNode(parent);
    }
    return false;
  }
}
