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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.data.internalutils.InternalValueUtils;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.PrimitiveData;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoytreeUtils;
import com.google.template.soy.types.SoyEnumType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistry;

import java.util.Map;

/**
 * Visitor for substituting values of compile-time globals and/or for checking that all globals are
 * defined by the compile-time globals map.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> To do substitution only, set {@code shouldAssertNoUnboundGlobals} to false in the
 * constructor. To do substitution and checking, set  {@code shouldAssertNoUnboundGlobals} to true.
 *
 */
final class SubstituteGlobalsVisitor {

  private static final SoyErrorKind UNBOUND_GLOBAL = SoyErrorKind.of("Unbound global ''{0}''.");
  private static final SoyErrorKind ENUM_MEMBERSHIP_ERROR =
      SoyErrorKind.of("''{0}'' is not a member of enum ''{1}''.");

  /** Map from compile-time global name to value. */
  private final ImmutableMap<String, PrimitiveData> compileTimeGlobals;

  /** Whether to throw an exception if we encounter an unbound global. */
  private final boolean shouldAssertNoUnboundGlobals;

  /** Type registry used to look up enum values. */
  private final SoyTypeRegistry typeRegistry;

  private final SubstituteGlobalsInExprVisitor exprVisitor = new SubstituteGlobalsInExprVisitor();

  private final ErrorReporter errorReporter;

  /**
   * @param compileTimeGlobals Map from compile-time global name to value.
   * @param shouldAssertNoUnboundGlobals Whether to throw an exception if we encounter an unbound
   *     global.
   */
  SubstituteGlobalsVisitor(
      Map<String, PrimitiveData> compileTimeGlobals,
      SoyTypeRegistry typeRegistry,
      boolean shouldAssertNoUnboundGlobals,
      ErrorReporter errorReporter) {
    this.compileTimeGlobals = ImmutableMap.copyOf(compileTimeGlobals);
    this.typeRegistry = checkNotNull(typeRegistry);
    this.shouldAssertNoUnboundGlobals = shouldAssertNoUnboundGlobals;
    this.errorReporter = errorReporter;
  }

  /** Runs this pass on the given Soy tree. */
  public void exec(SoyNode soyTree) {
    SoytreeUtils.execOnAllV2Exprs(soyTree, exprVisitor);
  }

  /**
   * Private helper class for SubstituteGlobalsVisitor to visit expressions.
   * This class does the real work.
   */
  @VisibleForTesting
  final class SubstituteGlobalsInExprVisitor extends AbstractExprNodeVisitor<Void> {

    @Override protected void visitGlobalNode(GlobalNode node) {

      PrimitiveData value =
          (compileTimeGlobals != null) ? compileTimeGlobals.get(node.getName()) : null;

      if (value == null && typeRegistry != null) {
        value = getEnumValue(node);
      }

      if (value == null) {
        if (shouldAssertNoUnboundGlobals && !node.shouldSuppressUnknownGlobalErrors()) {
          errorReporter.report(node.getSourceLocation(), UNBOUND_GLOBAL, node.getName());
        }
        return;
      }

      // Replace this node with a primitive literal.
      node.getParent().replaceChild(node, InternalValueUtils.convertPrimitiveDataToExpr(value));
    }

    @Override protected void visitExprNode(ExprNode node) {
      if (node instanceof ParentExprNode) {
        visitChildrenAllowingConcurrentModification((ParentExprNode) node);
      }
    }

    private PrimitiveData getEnumValue(GlobalNode node) {
      String name = node.getName();
      int lastDot = name.lastIndexOf('.');
      if (lastDot < 0) {
        return null;
      }
      String enumTypeName = name.substring(0, lastDot);
      SoyType type = typeRegistry.getType(enumTypeName);
      if (type instanceof SoyEnumType) {
        SoyEnumType enumType = (SoyEnumType) type;
        String enumValueName = name.substring(lastDot + 1);
        Integer enumValue = enumType.getValue(enumValueName);
        if (enumValue != null) {
          return IntegerData.forValue(enumValue);
        } else {
          // If we found the type definition but not the value, then that's an error
          // regardless of whether we're allowing unbound globals or not.
          errorReporter.report(
              node.getSourceLocation(), ENUM_MEMBERSHIP_ERROR, enumValueName, enumTypeName);
        }
      }
      return null;
    }
  }
}
