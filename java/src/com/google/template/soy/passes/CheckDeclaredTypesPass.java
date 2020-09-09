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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.MapType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.ast.GenericTypeNode;
import com.google.template.soy.types.ast.NamedTypeNode;
import com.google.template.soy.types.ast.RecordTypeNode;
import com.google.template.soy.types.ast.RecordTypeNode.Property;
import com.google.template.soy.types.ast.TemplateTypeNode;
import com.google.template.soy.types.ast.TypeNode;
import com.google.template.soy.types.ast.TypeNodeVisitor;
import com.google.template.soy.types.ast.UnionTypeNode;

/**
 * Checks type declarations to make sure they're legal. For now, this only checks that legal map
 * keys are used.
 *
 * <p>This class determines if explicit type declarations are legal, whereas {@link
 * ResolveExpressionTypesPass} calculates implicit types and determines if they're legal.
 */
final class CheckDeclaredTypesPass implements CompilerFilePass {

  private static final SoyErrorKind VE_BAD_DATA_TYPE =
      SoyErrorKind.of("Illegal VE metadata type ''{0}''. The metadata must be a proto.");

  private final ErrorReporter errorReporter;

  CheckDeclaredTypesPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (TemplateNode templateNode : file.getTemplates()) {
      for (TemplateParam param : templateNode.getAllParams()) {
        TypeNode type = param.getTypeNode();
        // Skip this if it's a param with a default value and an inferred type. In the case of an
        // illegal map key type, the error will be reported on the map literal by
        // ResolveExpressionTypesPass.
        if (type != null) {
          type.accept(new MapKeyTypeChecker());
        }
      }
    }
  }

  private final class MapKeyTypeChecker implements TypeNodeVisitor<Void> {

    @Override
    public Void visit(NamedTypeNode node) {
      return null; // Not a map. Nothing to do.
    }

    @Override
    public Void visit(GenericTypeNode node) {
      if (!node.isTypeResolved()) {
        // this means an error was already reported
        return null;
      }
      switch (node.getResolvedType().getKind()) {
        case MAP:
          checkArgument(node.arguments().size() == 2);
          TypeNode key = node.arguments().get(0);
          if (!MapType.isAllowedKeyType(key.getResolvedType())) {
            errorReporter.report(
                key.sourceLocation(), MapType.BAD_MAP_KEY_TYPE, key.getResolvedType());
          }
          node.arguments().get(1).accept(this);
          break;
        case LIST:
          checkArgument(node.arguments().size() == 1);
          node.arguments().get(0).accept(this);
          break;
        case LEGACY_OBJECT_MAP:
          checkArgument(node.arguments().size() == 2);
          for (TypeNode child : node.arguments()) {
            child.accept(this);
          }
          break;
        case VE:
          checkArgument(node.arguments().size() == 1);
          TypeNode dataType = node.arguments().get(0);
          if (dataType.getResolvedType().getKind() != Kind.PROTO
              && dataType.getResolvedType().getKind() != Kind.NULL) {
            errorReporter.report(
                dataType.sourceLocation(), VE_BAD_DATA_TYPE, dataType.getResolvedType());
          }
          node.arguments().get(0).accept(this);
          break;
        case ELEMENT:
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
    public Void visit(TemplateTypeNode node) {
      for (TemplateTypeNode.Parameter parameter : node.parameters()) {
        parameter.type().accept(this);
      }
      node.returnType().accept(this);
      return null;
    }
  }
}
