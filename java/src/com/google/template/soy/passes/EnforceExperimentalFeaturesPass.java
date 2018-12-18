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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.VeLiteralNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.KeyNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateElementNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.TemplatePropVar;

/**
 * A pass that ensures that experimental features are only used when enabled.
 *
 * <p>In general access to experimental features should be controlled by the passes that implement
 * their behavior, but if the new behavior is a change to syntax that might not be feasible, or if
 * the new behavior is a change scattered across the compiler then it might be impractical. In those
 * cases this pass is a reasonable place to put the enforcement code.
 */
final class EnforceExperimentalFeaturesPass extends CompilerFilePass {

  private static final SoyErrorKind ELEMENT_NOT_ALLOWED =
      SoyErrorKind.of("'{'element ...'}' declarations are not available for general use.");
  private static final SoyErrorKind PROP_VARS_NOT_ALLOWED =
      SoyErrorKind.of("@prop declarations are not available for general use.");
  private static final SoyErrorKind KEY_COMMAND_NOT_ALLOWED =
      SoyErrorKind.of("The key command is not available for general use.");
  private static final SoyErrorKind KEY_ATTR_NOT_ALLOWED =
      SoyErrorKind.of("Key attributes are not available for general use.");
  private static final SoyErrorKind DYNAMIC_VE_NOT_ALLOWED =
      SoyErrorKind.of("Dynamic VE features are not available for general use.");

  private final ImmutableSet<String> features;
  private final ErrorReporter reporter;

  EnforceExperimentalFeaturesPass(ImmutableSet<String> features, ErrorReporter reporter) {
    this.features = checkNotNull(features);
    this.reporter = checkNotNull(reporter);
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) { // TODO(b/114483225): Remove.
    if (!features.contains("prop_vars")) {
      for (CallNode call : SoyTreeUtils.getAllNodesOfType(file, CallNode.class)) {
        if (call.getKeyExpr() != null) {
          reporter.report(call.getKeyExpr().getSourceLocation(), KEY_ATTR_NOT_ALLOWED);
        }
      }
      for (KeyNode keyNode : SoyTreeUtils.getAllNodesOfType(file, KeyNode.class)) {
        reporter.report(keyNode.getSourceLocation(), KEY_COMMAND_NOT_ALLOWED);
      }
      for (TemplateNode template : file.getChildren()) {
        if (template instanceof TemplateElementNode) {
          reporter.report(template.getSourceLocation(), ELEMENT_NOT_ALLOWED);
          for (TemplatePropVar prop : ((TemplateElementNode) template).getPropVars()) {
            reporter.report(prop.nameLocation(), PROP_VARS_NOT_ALLOWED);
          }
        }
      }
    }
    if (!features.contains("dynamic_ve")) {
      for (VeLiteralNode veLiteral : SoyTreeUtils.getAllNodesOfType(file, VeLiteralNode.class)) {
        reporter.report(veLiteral.getSourceLocation(), DYNAMIC_VE_NOT_ALLOWED);
      }
      for (FunctionNode function : SoyTreeUtils.getAllNodesOfType(file, FunctionNode.class)) {
        if (function.getFunctionName().equals("ve_data")) {
          reporter.report(function.getSourceLocation(), DYNAMIC_VE_NOT_ALLOWED);
        }
      }
    }
  }
}
