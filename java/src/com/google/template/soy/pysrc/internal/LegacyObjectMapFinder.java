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

package com.google.template.soy.pysrc.internal;

import com.google.template.soy.compilermetrics.Impression;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.ast.GenericTypeNode;
import com.google.template.soy.types.ast.TypeNode;

/**
 * It is a compilation error to declare a {@code legacy_object_map} param in a Soy file that is
 * compiled to Python. (Use a {@code map} instead.)
 */
final class LegacyObjectMapFinder {

  private static final SoyErrorKind LEGACY_OBJECT_MAP_NOT_SUPPORTED =
      SoyErrorKind.of(
          "legacy_object_map is not supported in pysrc. Use maps or records instead."
          ,
          Impression.ERROR_LEGACY_OBJECT_MAP_FINDER_LEGACY_OBJECT_MAP_NOT_SUPPORTED,
          StyleAllowance.NO_CAPS);

  private final ErrorReporter errorReporter;

  LegacyObjectMapFinder(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  public void exec(TypeNode type) {
    SoyTreeUtils.allTypeNodes(type)
        .filter(GenericTypeNode.class::isInstance)
        .map(GenericTypeNode.class::cast)
        .forEach(
            node -> {
              if (node.getResolvedType().getKind() == Kind.LEGACY_OBJECT_MAP) {
                errorReporter.report(node.sourceLocation(), LEGACY_OBJECT_MAP_NOT_SUPPORTED);
              }
            });
  }
}
