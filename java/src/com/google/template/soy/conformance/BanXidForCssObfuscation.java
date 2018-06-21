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

package com.google.template.soy.conformance;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.template.soy.basetree.Node;
import com.google.template.soy.basetree.ParentNode;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.OperatorNodes.ConditionalOpNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.LetNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.TemplateNode;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Conformance check that prevents the usage of {@code xid} for obfuscating CSS classes. */
final class BanXidForCssObfuscation extends Rule<TemplateNode> {
  private static final Pattern CLASS_PARAM_PATTERN =
      Pattern.compile("\\A(class|classes|\\w+Class|\\w+Classes)\\Z");

  BanXidForCssObfuscation(SoyErrorKind error) {
    super(error);
  }

  @Override
  protected void doCheckConformance(TemplateNode node, ErrorReporter errorReporter) {
    new TemplateVerifier(errorReporter, node).verify();
  }

  private class TemplateVerifier {
    private final ErrorReporter errorReporter;
    private final TemplateNode template;
    private final Map<String, LetNode> localVars = Maps.newHashMap();
    private final Set<LetNode> verifiedLocalVars = Sets.newHashSet();

    TemplateVerifier(ErrorReporter errorReporter, TemplateNode template) {
      this.errorReporter = errorReporter;
      this.template = template;
    }

    void verify() {
      visit(template);
    }

    private void visit(SoyNode node) {
      if (node instanceof LetNode) {
        localVars.put(((LetNode) node).getVarName(), (LetNode) node);
      }

      if (shouldBanXid(node)) {
        visitAndBanXid(node);
      } else if (node instanceof ParentNode) {
        for (Node child : ((ParentNode<?>) node).getChildren()) {
          if (child instanceof SoyNode) {
            visit((SoyNode) child);
          }
        }
      }
    }

    private boolean shouldBanXid(SoyNode node) {
      if (node instanceof CallParamNode
          && CLASS_PARAM_PATTERN.matcher(((CallParamNode) node).getKey().identifier()).matches()) {
        return true;
      }
      if (node instanceof HtmlAttributeNode
          && ((HtmlAttributeNode) node).definitelyMatchesAttributeName("class")) {
        return true;
      }
      return false;
    }

    private void visitAndBanXid(SoyNode node) {
      if (node instanceof ExprHolderNode) {
        for (ExprRootNode expr : ((ExprHolderNode) node).getExprList()) {
          visitAndBanXidInExpressionValue(expr);
        }
      } else if (node instanceof ParentNode) {
        for (Node child : ((ParentNode<?>) node).getChildren()) {
          if (child instanceof SoyNode) {
            visitAndBanXidInPrintedValue((SoyNode) child);
          }
        }
      } else {
        visitAndBanXidInPrintedValue(node);
      }
    }

    private void visitAndBanXidInPrintedValue(SoyNode node) {
      if (node instanceof LetNode) {
        // We will come back to these if they are actually printed.
        localVars.put(((LetNode) node).getVarName(), (LetNode) node);
      } else if (node instanceof PrintNode) {
        visitAndBanXidInExpressionValue(((PrintNode) node).getExpr());
      } else if (node instanceof ParentNode) {
        for (Node child : ((ParentNode<?>) node).getChildren()) {
          if (child instanceof SoyNode) {
            visitAndBanXidInPrintedValue((SoyNode) child);
          }
        }
      }
    }

    private void visitAndBanXidInExpressionValue(ExprNode node) {
      if (node instanceof FunctionNode) {
        FunctionNode fn = (FunctionNode) node;
        if (fn.getFunctionName().equals("xid")) {
          errorReporter.report(fn.getSourceLocation(), error);
        }
      } else if (node instanceof VarRefNode) {
        LetNode varNode = localVars.get(((VarRefNode) node).getName());
        if (varNode != null && verifiedLocalVars.add(varNode)) {
          visitAndBanXid(varNode);
        }
      } else if (node instanceof ConditionalOpNode) {
        ConditionalOpNode condNode = (ConditionalOpNode) node;
        // We skip visiting the conditional expression as it will not be part of the outputed
        // value.
        visitAndBanXidInExpressionValue(condNode.getChild(1));
        visitAndBanXidInExpressionValue(condNode.getChild(2));
      } else if (node instanceof ParentExprNode) {
        for (ExprNode child : ((ParentExprNode) node).getChildren()) {
          visitAndBanXidInExpressionValue(child);
        }
      }
    }
  }
}
