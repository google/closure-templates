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
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.error.SoyErrors;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.logging.LoggingFunction;
import com.google.template.soy.logging.ValidatedLoggingConfig;
import com.google.template.soy.logging.ValidatedLoggingConfig.ValidatedLoggableElement;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.VeLogNode;
import com.google.template.soy.types.BoolType;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;

/**
 * Validates uses of the {@code velog} command.
 *
 * <p>Must run after:
 *
 * <ul>
 *   <li>ResolveTypesPass since we rely on type resolution data
 *   <li>ResolveFunctions pass since we need to validate the use of {@link LoggingFunction}
 *       invocations
 * </ul>
 */
final class VeLogValidationPass extends CompilerFilePass {
  private static final SoyErrorKind NO_CONFIG_FOR_ELEMENT =
      SoyErrorKind.of(
          "Could not find logging configuration for this element.{0}",
          StyleAllowance.NO_PUNCTUATION);
  private static final SoyErrorKind UNEXPECTED_CONFIG =
      SoyErrorKind.of(
          "Unexpected ''data'' attribute for logging element ''{0}'', there is no configured "
              + "''proto_extension_type'' in the logging configuration for this element. "
              + "Did you forget to configure it?");
  private static final SoyErrorKind WRONG_TYPE =
      SoyErrorKind.of("Expected an expression of type ''{0}'', instead got ''{1}''.");
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

  private final ErrorReporter reporter;
  private final ValidatedLoggingConfig loggingConfig;

  VeLogValidationPass(
      ErrorReporter reporter,
      ValidatedLoggingConfig loggingConfig) {
    this.reporter = reporter;
    this.loggingConfig = loggingConfig;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    if (file.getSoyFileKind() != SoyFileKind.SRC) {
      // don't run non deps/indirect deps
      // There is no need
      return;
    }
    for (TemplateNode template : file.getChildren()) {
      for (VeLogNode node : SoyTreeUtils.getAllNodesOfType(template, VeLogNode.class)) {
        if (template.isStrictHtml()) {
          validateNodeAgainstConfig(node);
        } else {
          reporter.report(node.getName().location(), REQUIRE_STRICTHTML);
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
  private void validateNodeAgainstConfig(VeLogNode node) {
    ValidatedLoggableElement config = loggingConfig.getElement(node.getName().identifier());

    if (config == null) {
      reporter.report(
          node.getName().location(),
          NO_CONFIG_FOR_ELEMENT,
          SoyErrors.getDidYouMeanMessage(
              loggingConfig.allKnownIdentifiers(), node.getName().identifier()));
    } else {
      node.setLoggingId(config.getId());
      if (node.getConfigExpression() != null) {
        SoyType type = node.getConfigExpression().getType();
        Optional<String> protoName = config.getProtoName();
        if (!protoName.isPresent()) {
          reporter.report(
              node.getConfigExpression().getSourceLocation(),
              UNEXPECTED_CONFIG,
              node.getName().identifier());
        } else if (type.getKind() != Kind.ERROR
            && (type.getKind() != Kind.PROTO
                || !((SoyProtoType) type).getDescriptor().getFullName().equals(protoName.get()))) {
          reporter.report(
              node.getConfigExpression().getSourceLocation(), WRONG_TYPE, protoName.get(), type);
        }
      }

      if (node.getLogonlyExpression() != null) {
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
  }
}
