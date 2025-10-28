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

import static com.google.template.soy.soytree.SoyTreeUtils.getChildTypeNodes;

import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.soytree.AutoImplNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.SoyTreeUtils.VisitDirective;
import com.google.template.soy.types.MapType;
import com.google.template.soy.types.MutableListType;
import com.google.template.soy.types.MutableMapType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.ast.GenericTypeNode;
import com.google.template.soy.types.ast.LiteralTypeNode;
import com.google.template.soy.types.ast.TypeNode;
import com.google.template.soy.types.ast.TypesHolderNode;

/**
 * Checks type declarations to make sure they're legal. For now, this only checks that legal map
 * keys are used.
 *
 * <p>This class determines if explicit type declarations are legal, whereas {@link
 * ResolveExpressionTypesPass} calculates implicit types and determines if they're legal.
 */
final class CheckDeclaredTypesPass implements CompilerFilePass {

  static final SoyErrorKind BAD_MAP_OR_SET_KEY_TYPE =
      SoyErrorKind.of(
          "''{0}'' is not allowed as a map or set key type. Allowed key types: "
              + "bool, int, float, gbigint, number, string, proto enum.");
  private static final SoyErrorKind VE_BAD_DATA_TYPE =
      SoyErrorKind.of("Illegal VE metadata type ''{0}''. The metadata must be a proto.");
  private static final SoyErrorKind LITERAL_TYPE =
      SoyErrorKind.of("Literal types are not allowed.");
  private static final SoyErrorKind MUTABLE_TYPE =
      SoyErrorKind.of("Mutable types are only allowed inside '{'autoimpl'}'.");

  private final ErrorReporter errorReporter;

  CheckDeclaredTypesPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    SoyTreeUtils.allTypeNodes(file)
        .forEach(
            node -> {
              checkGenericTypes(node);
              checkLiteralTypes(node);
            });

    SoyTreeUtils.allNodesOfType(
            file,
            TypesHolderNode.class,
            node ->
                node instanceof AutoImplNode
                    ? VisitDirective.SKIP_CHILDREN
                    : VisitDirective.CONTINUE)
        .flatMap(TypesHolderNode::getTypeNodes)
        .forEach(this::checkMutableTypes);
  }

  private void checkMutableTypes(TypeNode node) {
    SoyTreeUtils.allTypeNodes(node)
        .forEach(
            n -> {
              if (n.isTypeResolved()) {
                SoyType type = n.getResolvedType();
                if (type instanceof MutableListType || type instanceof MutableMapType) {
                  errorReporter.report(n.sourceLocation(), MUTABLE_TYPE);
                }
              }
            });
  }

  private void checkGenericTypes(TypeNode root) {
    SoyTreeUtils.allTypeNodes(root)
        .forEach(
            node -> {
              if (!node.isTypeResolved()) {
                // this means an error was already reported
                return;
              }
              if (node instanceof GenericTypeNode) {
                GenericTypeNode genericTypeNode = (GenericTypeNode) node;
                SoyType soyType = node.getResolvedType();
                switch (soyType.getKind()) {
                  case MAP:
                    TypeNode key = genericTypeNode.arguments().get(0);
                    if (!MapType.isAllowedKeyType(key.getResolvedType())) {
                      errorReporter.report(
                          key.sourceLocation(), BAD_MAP_OR_SET_KEY_TYPE, key.getResolvedType());
                    }
                    break;
                  case VE:
                    TypeNode dataType = genericTypeNode.arguments().get(0);
                    if (dataType.getResolvedType().getKind() != Kind.PROTO
                        && !SoyTypes.isNullOrUndefined(dataType.getResolvedType())) {
                      errorReporter.report(
                          dataType.sourceLocation(), VE_BAD_DATA_TYPE, dataType.getResolvedType());
                    }
                    break;
                  default:
                    break;
                }
              }
            });
  }

  private void checkLiteralTypes(TypeNode root) {
    if (root instanceof LiteralTypeNode) {
      ExprNode value = ((LiteralTypeNode) root).literal();
      if (value.getKind() != ExprNode.Kind.NULL_NODE
          && value.getKind() != ExprNode.Kind.UNDEFINED_NODE) {
        errorReporter.report(root.sourceLocation(), LITERAL_TYPE);
      }
    } else if (root instanceof GenericTypeNode) {
      // String literal type nodes are only valid in the second argument of Pick<> and Omit<>.
      String type = ((GenericTypeNode) root).name();
      if (type.equals("Pick") || type.equals("Omit")) {
        checkGenericTypes(((GenericTypeNode) root).arguments().get(0));
        return;
      }
    }

    getChildTypeNodes(root).forEach(this::checkLiteralTypes);
  }
}
