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
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.TemplateHeaderVarDefn;
import com.google.template.soy.types.MapType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.ast.GenericTypeNode;
import com.google.template.soy.types.ast.TypeNode;

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
              + "bool, int, float, number, string, proto enum.");
  private static final SoyErrorKind VE_BAD_DATA_TYPE =
      SoyErrorKind.of("Illegal VE metadata type ''{0}''. The metadata must be a proto.");

  private final ErrorReporter errorReporter;

  CheckDeclaredTypesPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (TemplateNode templateNode : file.getTemplates()) {
      for (TemplateHeaderVarDefn param : templateNode.getHeaderParams()) {
        TypeNode type = param.getTypeNode();
        // Skip this if it's a param with a default value and an inferred type. In the case of an
        // illegal map key type, the error will be reported on the map literal by
        // ResolveExpressionTypesPass.
        if (type != null) {
          SoyTreeUtils.allTypeNodes(type)
              .filter(GenericTypeNode.class::isInstance)
              .map(GenericTypeNode.class::cast)
              .forEach(
                  node -> {
                    if (!node.isTypeResolved()) {
                      // this means an error was already reported
                      return;
                    }
                    switch (node.getResolvedType().getKind()) {
                      case MAP:
                        checkArgument(node.arguments().size() == 2);
                        TypeNode key = node.arguments().get(0);
                        if (!MapType.isAllowedKeyType(key.getResolvedType())) {
                          errorReporter.report(
                              key.sourceLocation(), BAD_MAP_OR_SET_KEY_TYPE, key.getResolvedType());
                        }
                        break;
                      case ITERABLE:
                      case LIST:
                      case SET:
                        checkArgument(node.arguments().size() == 1);
                        break;
                      case LEGACY_OBJECT_MAP:
                        checkArgument(node.arguments().size() == 2);
                        break;
                      case VE:
                        checkArgument(node.arguments().size() == 1);
                        TypeNode dataType = node.arguments().get(0);
                        if (dataType.getResolvedType().getKind() != Kind.PROTO
                            && !dataType.getResolvedType().isNullOrUndefined()) {
                          errorReporter.report(
                              dataType.sourceLocation(),
                              VE_BAD_DATA_TYPE,
                              dataType.getResolvedType());
                        }
                        break;
                      case ELEMENT:
                        break;
                      default:
                        throw new AssertionError(
                            "unexpected generic type: " + node.getResolvedType().getKind());
                    }
                  });
        }
      }
    }
  }
}
