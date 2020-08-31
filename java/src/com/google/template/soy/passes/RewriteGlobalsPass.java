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
import com.google.template.soy.base.internal.Identifier;
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
import com.google.template.soy.types.TypeRegistries;
import com.google.template.soy.types.UnknownType;

/**
 * A {@link CompilerFilePass} that searches for globals and substitutes values.
 *
 * <p>TODO(lukes): consider introducing a SoyEnumNode and replacing globals that reference enums
 * with that node type here.
 */
@RunAfter({
  VeRewritePass.class, // rewrites some VE references that are parsed as globals in a different way
})
@RunBefore({CheckGlobalsPass.class})
final class RewriteGlobalsPass implements CompilerFilePass {
  private static final SoyErrorKind ENUM_MEMBERSHIP_ERROR =
      SoyErrorKind.of("''{0}'' is not a member of enum ''{1}''.");

  private static final SoyErrorKind PROTO_GLOBAL_OVERLAP_ERROR =
      SoyErrorKind.of(
          "''{0}'' corresponds to a proto enum and is registered as a global with the value"
              + " ''{1}''.  Remove the global definition.");

  private final ImmutableMap<String, PrimitiveData> compileTimeGlobals;
  private final ErrorReporter errorReporter;

  RewriteGlobalsPass(
      ImmutableMap<String, PrimitiveData> compileTimeGlobals, ErrorReporter errorReporter) {
    this.compileTimeGlobals = compileTimeGlobals;
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    SoyTypeRegistry typeRegistry = file.getSoyTypeRegistry();
    for (GlobalNode global : SoyTreeUtils.getAllNodesOfType(file, GlobalNode.class)) {
      resolveGlobal(file, typeRegistry, global);
    }
  }

  private void resolveGlobal(SoyFileNode file, SoyTypeRegistry typeRegistry, GlobalNode global) {
    // First check to see if this global matches a proto enum.  We do this because the enums from
    // the type registry have better type information and for applications with legacy globals
    // configs there is often overlap, so the order in which we check is actually important.
    // proto enums are dotted identifiers
    String name = global.getName();
    int lastDot = name.lastIndexOf('.');
    if (lastDot > 0) {
      String enumTypeName = name.substring(0, lastDot);
      SoyType type =
          TypeRegistries.getTypeOrProtoFqn(
              typeRegistry, errorReporter, global.getIdentifier(), enumTypeName);
      if (type != null && type.getKind() == SoyType.Kind.PROTO_ENUM) {
        SoyProtoEnumType enumType = (SoyProtoEnumType) type;
        String enumMemberName = name.substring(lastDot + 1);
        Integer enumValue = enumType.getValue(enumMemberName);
        if (enumValue != null) {
          // TODO(lukes): consider introducing a new PrimitiveNode for enums
          global.resolve(enumType, new IntegerNode(enumValue, global.getSourceLocation()));
          String fullyQualifiedName = enumType.getName() + "." + enumMemberName;
          PrimitiveData value = compileTimeGlobals.get(fullyQualifiedName);
          if (value != null) {
            errorReporter.report(
                global.getSourceLocation(), PROTO_GLOBAL_OVERLAP_ERROR, fullyQualifiedName, value);
          }
        } else {
          // If we found the type definition but not the value, then that's an error
          // regardless of whether we're allowing unbound globals or not.
          errorReporter.report(
              global.getSourceLocation(), ENUM_MEMBERSHIP_ERROR, enumMemberName, enumTypeName);
        }
        return;
      }
    }

    Identifier alias = file.resolveAlias(global.getIdentifier());
    if (alias != null) {
      global.setName(alias.identifier());
    }
    name = global.getName();
    // if that doesn't work, see if it was registered in the globals file.
    PrimitiveData value = compileTimeGlobals.get(name);

    if (value != null) {
      PrimitiveNode expr =
          InternalValueUtils.convertPrimitiveDataToExpr(value, global.getSourceLocation());
      global.resolve(expr.getType(), expr);
      // check if the fully qualified name matches a known proto, even if it isn't imported.
      lastDot = name.lastIndexOf('.');
      if (lastDot > 0) {
        SoyType type = typeRegistry.getProtoRegistry().getProtoType(name.substring(0, lastDot));
        // TODO(b/167269736): After gen_soy_xmb is gone, we should either delete
        // SoyTypeRegistry.DEFAULT_UNKNOWN, or change it to return null for getProtoType.
        if (type != null && !type.equals(UnknownType.getInstance())) {
          errorReporter.report(global.getSourceLocation(), PROTO_GLOBAL_OVERLAP_ERROR, name, value);
        }
      }
    }
  }
}
