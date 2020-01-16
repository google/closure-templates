/*
 * Copyright 2016 Google Inc.
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

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.data.internalutils.InternalValueUtils;
import com.google.template.soy.data.restricted.PrimitiveData;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprNode.PrimitiveNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.types.SoyProtoEnumType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistry;

/**
 * A {@link CompilerFilePass} that searches for globals and substitutes values.
 *
 * <p>TODO(lukes): consider introducing a SoyEnumNode and replacing globals that reference enums
 * with that node type here.
 */
final class RewriteGlobalsPass implements CompilerFilePass {
  private static final SoyErrorKind ENUM_MEMBERSHIP_ERROR =
      SoyErrorKind.of("''{0}'' is not a member of enum ''{1}''.");

  private final SoyTypeRegistry typeRegistry;
  private final ImmutableMap<String, PrimitiveData> compileTimeGlobals;
  private final ErrorReporter errorReporter;

  RewriteGlobalsPass(
      SoyTypeRegistry typeRegistry,
      ImmutableMap<String, PrimitiveData> compileTimeGlobals,
      ErrorReporter errorReporter) {
    this.typeRegistry = typeRegistry;
    this.compileTimeGlobals = compileTimeGlobals;
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (GlobalNode global : SoyTreeUtils.getAllNodesOfType(file, GlobalNode.class)) {
      resolveGlobal(global);
    }
  }

  private void resolveGlobal(GlobalNode global) {
    // First check to see if this global matches a proto enum.  We do this because the enums from
    // the type registry have better type information and for applications with legacy globals
    // configs there is often overlap, so the order in which we check is actually important.
    // proto enums are dotted identifiers
    String name = global.getName();
    int lastDot = name.lastIndexOf('.');
    if (lastDot > 0) {
      String enumTypeName = name.substring(0, lastDot);
      SoyType type = typeRegistry.getType(enumTypeName);
      if (type != null && type.getKind() == SoyType.Kind.PROTO_ENUM) {
        SoyProtoEnumType enumType = (SoyProtoEnumType) type;
        String enumMemberName = name.substring(lastDot + 1);
        Integer enumValue = enumType.getValue(enumMemberName);
        if (enumValue != null) {
          // TODO(lukes): consider introducing a new PrimitiveNode for enums
          global.resolve(enumType, new IntegerNode(enumValue, global.getSourceLocation()));
        } else {
          // If we found the type definition but not the value, then that's an error
          // regardless of whether we're allowing unbound globals or not.
          errorReporter.report(
              global.getSourceLocation(), ENUM_MEMBERSHIP_ERROR, enumMemberName, enumTypeName);
        }
        // TODO(lukes): issue a warning if a registered global also matches
        return;
      }
    }
    // if that doesn't work, see if it was registered in the globals file.
    PrimitiveData value = compileTimeGlobals.get(global.getName());
    if (value != null) {
      PrimitiveNode expr =
          InternalValueUtils.convertPrimitiveDataToExpr(value, global.getSourceLocation());
      global.resolve(expr.getType(), expr);
    }
  }
}
