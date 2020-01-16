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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.logging.LoggingFunction;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.HtmlCloseTagNode;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.MsgBlockNode;
import com.google.template.soy.soytree.SoyNode.MsgSubstUnitNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.VeLogNode;
import com.google.template.soy.types.BoolType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.VeType;
import java.util.List;
import java.util.Objects;

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
final class VeLogValidationPass implements CompilerFileSetPass {
  private static final SoyErrorKind UNEXPECTED_DATA =
      SoyErrorKind.of(
          "Unexpected data argument. The VE is type ''{0}'' which means there cannot be any data. "
              + "The data is typed ''{1}'' and must match with the VE.");
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
          "The velog command requires a VE identifier, an expression of the ''ve'' type or an "
              + "expression of the ''ve_data'' type. Found an expression of type ''{0}''.");
  private static final SoyErrorKind VE_UNION_WITH_DATA =
      SoyErrorKind.of(
          "It is illegal to set the data parameter if the ve type is a union (''{0}'').");

  private static final SoyErrorKind LOG_WITHIN_MESSAGE_REQUIRES_ELEMENT =
      SoyErrorKind.of("'{velog'} within '{msg'} must directly wrap an HTML element.");

  private final ErrorReporter reporter;
  private final SoyTypeRegistry typeRegistry;

  VeLogValidationPass(ErrorReporter reporter, SoyTypeRegistry typeRegistry) {
    this.reporter = reporter;
    this.typeRegistry = typeRegistry;
  }

  @Override
  public Result run(
      ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator, TemplateRegistry registry) {
    for (SoyFileNode file : sourceFiles) {
      for (TemplateNode template : file.getChildren()) {
        run(template);
      }
    }
    if (reporter.hasErrors()) {
      return Result.STOP;
    }
    return Result.CONTINUE;
  }

  private void run(TemplateNode template) {
    for (FunctionNode node :
        SoyTreeUtils.getAllFunctionInvocations(template, BuiltinFunction.VE_DATA)) {
      validateVeDataFunctionNode(node);
    }
    for (VeLogNode node : SoyTreeUtils.getAllNodesOfType(template, VeLogNode.class)) {
      if (template.isStrictHtml()) {
        validateVelogElementStructure(node);
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
        for (FunctionNode function : SoyTreeUtils.getAllNodesOfType(rootNode, FunctionNode.class)) {
          if (function.getSoyFunction() instanceof LoggingFunction) {
            validateLoggingFunction(holderNode, function);
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

  private void validateVelogElementStructure(VeLogNode node) {
    List<StandaloneNode> children =
        node.getChildren().stream()
            .filter(child -> !SoyElementPass.ALLOWED_CHILD_NODES.contains(child.getKind()))
            .collect(ImmutableList.toImmutableList());
    // TODO(b/133428199): Support {velog} around calls in messages.
    if (node.getNearestAncestor(MsgFallbackGroupNode.class) == null
        && children.size() == 1
        && Iterables.getLast(children) instanceof CallBasicNode) {
      node.setNeedsSyntheticVelogNode(true);
      return;
    }

    // If {velog} is empty, or does not have a single root, we must output a synthetic VE log node
    // on the client.
    if (node.numChildren() == 0) {
      node.setNeedsSyntheticVelogNode(true);
      return;
    }

    HtmlOpenTagNode firstTag = node.getOpenTagNode();
    // If the first child of {velog} is not an open tag, output a synthetic VE log node.
    if (firstTag == null) {
      node.setNeedsSyntheticVelogNode(true);
      return;
    }

    // If the first child is self-closing or is a void tag, output a synthetic VE log node if we see
    // anything after it. If it is the only thing, we don't need a synthetic VE log node.
    if (firstTag.isSelfClosing() || firstTag.getTagName().isDefinitelyVoid()) {
      if (node.numChildren() > 1) {
        node.setNeedsSyntheticVelogNode(true);
      }
      return;
    }

    HtmlCloseTagNode lastTag = node.getCloseTagNode();
    // If the last child is not a close tag, output a synthetic VE log node.
    if (lastTag == null) {
      node.setNeedsSyntheticVelogNode(true);
      return;
    }
    // This check make sures that there is exactly one top-level element -- the last tag must
    // close the first tag within {velog} command. Otherwise, we need to output a synthetic VE log
    // node.
    if (lastTag.getTaggedPairs().size() != 1
        || !Objects.equals(lastTag.getTaggedPairs().get(0), firstTag)) {
      node.setNeedsSyntheticVelogNode(true);
    }
  }

  /** Type checks the VE and logonly expressions. */
  private void validateVeLogNode(VeLogNode node) {
    if (node.getVeDataExpression().getRoot().getType().getKind() != Kind.VE_DATA) {
      reporter.report(
          node.getVeDataExpression().getSourceLocation(),
          INVALID_VE,
          node.getVeDataExpression().getRoot().getType());
    }
    if (node.needsSyntheticVelogNode() && isInMsgNode(node)) {
      reporter.report(node.getSourceLocation(), LOG_WITHIN_MESSAGE_REQUIRES_ELEMENT);
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

    if (veExpr.getType().getKind() == Kind.ERROR) {
      return;
    }

    if (veExpr.getType().getKind() == Kind.VE) {
      if (dataExpr.getType().getKind() != Kind.NULL) {
        VeType veType = (VeType) veExpr.getType();
        SoyType dataType = dataExpr.getType();
        if (!veType.getDataType().isPresent()) {
          reporter.report(
              dataExpr.getSourceLocation(),
              UNEXPECTED_DATA,
              veType,
              dataType);
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
    } else if (SoyTypes.isKindOrUnionOfKind(veExpr.getType(), Kind.VE)) {
      // This is a union of VE types with different data types, so it's okay to wrap in ve_data as
      // long as ve_data's data parameter is null.
      if (dataExpr.getType().getKind() != Kind.NULL) {
        reporter.report(dataExpr.getSourceLocation(), VE_UNION_WITH_DATA, veExpr.getType());
      }
    } else {
      reporter.report(veExpr.getSourceLocation(), WRONG_TYPE, "ve", veExpr.getType());
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
