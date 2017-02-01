/*
 * Copyright 2009 Google Inc.
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

import static com.google.template.soy.parsepasses.contextautoesc.ContentSecurityPolicyPass.CSP_NONCE_VARIABLE_NAME;

import com.google.template.soy.basetree.Node;
import com.google.template.soy.basetree.NodeVisitor;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarDefn.Kind;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.TemplateParam;

/**
 * A visitor that checks for certain misuses of template parameters:
 *
 * <ul>
 *   <li>There should be no explicit use of injected parameters called {@code csp_nonce}. These
 *       should all be inserted by the soy compiler as part of the content security pass.
 * </ul>
 *
 * <p>TODO(lukes): add a check for unresolved globals that match params.
 */
final class CheckInvalidParamsVisitor {
  private static final SoyErrorKind IJ_CSP_NONCE_REFERENCE =
      SoyErrorKind.of(
          "Found a use of the injected parameter ''csp_nonce''. This parameter is reserved "
              + "by the Soy compiler for Content Security Policy support.");

  private final ErrorReporter errorReporter;

  CheckInvalidParamsVisitor(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  void exec(SoyNode node) {
    // Search for injected params named 'csp_nonce'.  This includes:
    // * @inject params
    // * $ij references
    SoyTreeUtils.visitAllNodes(
        node,
        new NodeVisitor<Node, Boolean>() {
          @Override
          public Boolean exec(Node node) {
            if (node instanceof TemplateNode) {
              TemplateNode template = (TemplateNode) node;
              for (TemplateParam param : template.getAllParams()) {
                if (param.isInjected() && param.name().equals(CSP_NONCE_VARIABLE_NAME)) {
                  errorReporter.report(node.getSourceLocation(), IJ_CSP_NONCE_REFERENCE);
                }
              }
            }
            if (node instanceof VarRefNode) {
              VarDefn defn = ((VarRefNode) node).getDefnDecl();
              if (defn.kind() == Kind.IJ_PARAM && defn.name().equals(CSP_NONCE_VARIABLE_NAME)) {
                errorReporter.report(node.getSourceLocation(), IJ_CSP_NONCE_REFERENCE);
              }
            }
            return true; // keep going
          }
        });
  }
}
