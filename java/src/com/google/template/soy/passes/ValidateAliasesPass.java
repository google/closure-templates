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
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.logging.ValidatedLoggingConfig;
import com.google.template.soy.shared.SoyGeneralOptions;
import com.google.template.soy.soytree.AliasDeclaration;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistry;

/**
 * Checks that aliases don't conflict with things that can be aliased (or their namespace prefixes).
 */
final class ValidateAliasesPass extends CompilerFilePass {
  private static final SoyErrorKind ALIAS_CONFLICTS_WITH_GLOBAL =
      SoyErrorKind.of("Alias ''{0}'' conflicts with a global of the same name.");
  private static final SoyErrorKind ALIAS_CONFLICTS_WITH_GLOBAL_PREFIX =
      SoyErrorKind.of("Alias ''{0}'' conflicts with namespace for global ''{1}''.");

  private static final SoyErrorKind ALIAS_CONFLICTS_WITH_TYPE_NAME =
      SoyErrorKind.of("Alias ''{0}'' conflicts with a type of the same name.");
  private static final SoyErrorKind ALIAS_CONFLICTS_WITH_TYPE_PREFIX =
      SoyErrorKind.of("Alias ''{0}'' conflicts with namespace for type ''{1}''.");

  private static final SoyErrorKind ALIAS_CONFLICTS_WITH_VE =
      SoyErrorKind.of("Alias ''{0}'' conflicts with a VE of the same name.");

  private static final SoyErrorKind ALIAS_NEVER_USED =
      SoyErrorKind.of("Alias ''{0}'' is never referenced in this file. Please remove it.");

  private final SoyTypeRegistry registry;
  private final ErrorReporter errorReporter;
  private final SoyGeneralOptions options;
  private final ValidatedLoggingConfig loggingConfig;

  ValidateAliasesPass(
      SoyTypeRegistry registry,
      ErrorReporter errorReporter,
      SoyGeneralOptions options,
      ValidatedLoggingConfig loggingConfig) {
    this.registry = registry;
    this.errorReporter = errorReporter;
    this.options = options;
    this.loggingConfig = loggingConfig;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (AliasDeclaration alias : file.getAliasDeclarations()) {
      if (!file.aliasUsed(alias.alias().identifier())) {
        errorReporter.report(alias.alias().location(), ALIAS_NEVER_USED, alias.alias());
        // Skip the rest of the checks to prevent multiple errors on a bad alias.
        continue;
      }
      if (options.getCompileTimeGlobals().containsKey(alias.alias().identifier())) {
        errorReporter.report(alias.alias().location(), ALIAS_CONFLICTS_WITH_GLOBAL, alias.alias());
      }
      SoyType type = registry.getType(alias.alias().identifier());
      // When running with a dummy type provider that parses all types as unknown, ignore that.
      if (type != null && type.getKind() != SoyType.Kind.UNKNOWN) {
        errorReporter.report(
            alias.alias().location(), ALIAS_CONFLICTS_WITH_TYPE_NAME, alias.alias());
      }
      String conflictingNamespacedType =
          registry.findTypeWithMatchingNamespace(alias.alias().identifier());
      if (conflictingNamespacedType != null) {
        errorReporter.report(
            alias.alias().location(),
            ALIAS_CONFLICTS_WITH_TYPE_PREFIX,
            alias.alias(),
            conflictingNamespacedType);
      }
      String prefix = alias.alias().identifier() + ".";
      for (String global : options.getCompileTimeGlobals().keySet()) {
        if (global.startsWith(prefix)) {
          errorReporter.report(
              alias.alias().location(), ALIAS_CONFLICTS_WITH_GLOBAL_PREFIX, alias.alias(), global);
        }
      }
      if (loggingConfig.getElement(alias.alias().identifier()) != null) {
        errorReporter.report(alias.alias().location(), ALIAS_CONFLICTS_WITH_VE, alias.alias());
      }
    }
  }
}
