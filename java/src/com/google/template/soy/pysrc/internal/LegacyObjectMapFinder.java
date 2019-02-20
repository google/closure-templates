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

import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.types.ast.GenericTypeNode;
import com.google.template.soy.types.ast.NamedTypeNode;
import com.google.template.soy.types.ast.RecordTypeNode;
import com.google.template.soy.types.ast.RecordTypeNode.Property;
import com.google.template.soy.types.ast.TypeNode;
import com.google.template.soy.types.ast.TypeNodeVisitor;
import com.google.template.soy.types.ast.UnionTypeNode;

/**
 * It is a compilation error to declare a {@code legacy_object_map} param in a Soy file that is
 * compiled to Python. (Use a {@code map} instead.)
 */
final class LegacyObjectMapFinder implements TypeNodeVisitor<Void> {

  private static final SoyErrorKind LEGACY_OBJECT_MAP_NOT_SUPPORTED =
      SoyErrorKind.of(
          "legacy_object_map is not supported in pysrc. Use maps or records instead."
          ,
          StyleAllowance.NO_CAPS);

  private final ErrorReporter errorReporter;

  LegacyObjectMapFinder(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public Void visit(GenericTypeNode node) {
    switch (node.getResolvedType().getKind()) {
      case LEGACY_OBJECT_MAP:
        errorReporter.report(node.sourceLocation(), LEGACY_OBJECT_MAP_NOT_SUPPORTED);
        // fallthrough
      case LIST:
      case MAP:
      case VE:
        for (TypeNode child : node.arguments()) {
          child.accept(this);
        }
        break;
      default:
        throw new AssertionError("unexpected generic type: " + node.getResolvedType().getKind());
    }
    return null;
  }

  @Override
  public Void visit(UnionTypeNode node) {
    for (TypeNode child : node.candidates()) {
      child.accept(this);
    }
    return null;
  }

  @Override
  public Void visit(RecordTypeNode node) {
    for (Property property : node.properties()) {
      property.type().accept(this);
    }
    return null;
  }

  @Override
  public Void visit(NamedTypeNode node) {
    return null;
  }
}
