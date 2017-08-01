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
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.error.SoyErrors;
import com.google.template.soy.logging.ValidatedLoggingConfig;
import com.google.template.soy.logging.ValidatedLoggingConfig.ValidatedLoggableElement;
import com.google.template.soy.soytree.FooLogNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.primitive.BoolType;
import com.google.template.soy.types.proto.SoyProtoType;

/** Validates uses of the {@code foolog} command. */
final class FooLogValidationPass extends CompilerFilePass {
  private static final SoyErrorKind LOGGING_IS_EXPERIMENTAL =
      SoyErrorKind.of("The '{'foolog ...'}' command is disabled in this configuration.");
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

  private final ErrorReporter reporter;
  private final boolean enabled;
  private final ValidatedLoggingConfig loggingConfig;

  FooLogValidationPass(
      ErrorReporter reporter,
      ImmutableSet<String> experimentalFeatures,
      ValidatedLoggingConfig loggingConfig) {
    this.reporter = reporter;
    this.loggingConfig = loggingConfig;
    this.enabled = experimentalFeatures.contains("logging_support");
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    if (file.getSoyFileKind() != SoyFileKind.SRC) {
      // don't run non deps/indirect deps
      // There is no need
      return;
    }
    for (FooLogNode node : SoyTreeUtils.getAllNodesOfType(file, FooLogNode.class)) {
      if (!enabled) {
        reporter.report(node.getSourceLocation(), LOGGING_IS_EXPERIMENTAL);
      } else {
        validateNodeAgainstConfig(node);
      }
    }
  }

  /** Type checks both expressions and assigns the {@link FooLogNode#getLoggingId()} field. */
  private void validateNodeAgainstConfig(FooLogNode node) {
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
