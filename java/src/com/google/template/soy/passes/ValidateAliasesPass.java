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
import com.google.template.soy.compilermetrics.Impression;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.AliasDeclaration;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.types.TypeRegistries;
import com.google.template.soy.types.TypeRegistry;

/**
 * Checks that aliases don't conflict with things that can be aliased (or their namespace prefixes).
 */
final class ValidateAliasesPass implements CompilerFilePass {

  private static final SoyErrorKind ALIAS_CONFLICTS_WITH_TYPE_NAME =
      SoyErrorKind.of(
          "Alias ''{0}'' conflicts with a type of the same name.",
          Impression.ERROR_VALIDATE_ALIASES_PASS_ALIAS_CONFLICTS_WITH_TYPE_NAME);

  private final ErrorReporter errorReporter;

  ValidateAliasesPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    TypeRegistry registry = TypeRegistries.builtinTypeRegistry();
    for (AliasDeclaration alias : file.getAliasDeclarations()) {
      String aliasName = alias.alias().identifier();
      if (registry.hasType(aliasName)) {
        if (ImportsPass.NEW_TYPES.contains(aliasName)) {
          errorReporter.warn(alias.alias().location(), ALIAS_CONFLICTS_WITH_TYPE_NAME, aliasName);
        } else {
          errorReporter.report(alias.alias().location(), ALIAS_CONFLICTS_WITH_TYPE_NAME, aliasName);
        }
      }
    }
  }
}
