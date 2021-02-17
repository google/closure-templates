/*
 * Copyright 2020 Google Inc.
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

import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.error.SoyErrors;
import com.google.template.soy.exprtree.CallableExprBuilder;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.Kind;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.FieldAccessNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.MethodCallNode;
import com.google.template.soy.exprtree.ProtoEnumValueNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.defn.ImportedVar;
import com.google.template.soy.types.ImportType;
import com.google.template.soy.types.ProtoEnumImportType;
import com.google.template.soy.types.ProtoImportType;
import com.google.template.soy.types.ProtoModuleImportType;
import com.google.template.soy.types.SoyProtoEnumType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.TemplateImportType;
import com.google.template.soy.types.TemplateModuleImportType;
import com.google.template.soy.types.TypeInterner;
import com.google.template.soy.types.UnknownType;
import javax.annotation.Nullable;

/**
 * Inline VARREF + FIELD_ACCESS/METHOD_CALL into a simple VARREF for all VARREFs that point to an
 * {@link ImportedVar} VARDEF. Ensures that we don't need to evaluate such accesses at runtime.
 *
 * <p>Imported var defs currently include proto messages (used in proto init), proto enums (used to
 * reference enum values), proto extensions (for getExtension and proto init), and namespace (nested
 * access to the other three). Field access of proto message fields is unrelated.
 */
@RunAfter(ResolveNamesPass.class)
// Run before ResolveExpressionTypesPass to simplify that pass. I won't have to traverse dot
// accesses on these types.
@RunBefore(ResolveExpressionTypesPass.class)
public final class ResolveDottedImportsPass implements CompilerFilePass {

  private static final SoyErrorKind NO_SUCH_NESTED_TYPE =
      SoyErrorKind.of(
          "Nested type ''{0}'' does not exist in {1} {2}.{3}", StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind ENUM_MEMBERSHIP_ERROR =
      SoyErrorKind.of("''{0}'' is not a member of enum ''{1}''.");

  private final ErrorReporter errorReporter;
  private final TypeInterner typeRegistry;

  public ResolveDottedImportsPass(ErrorReporter errorReporter, TypeInterner typeRegistry) {
    this.errorReporter = errorReporter;
    this.typeRegistry = typeRegistry;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    SoyTreeUtils.allNodesOfType(file, VarRefNode.class)
        .filter(
            v -> {
              if (!v.hasType()) {
                return false;
              }
              return v.getDefnDecl().type() instanceof ImportType;
            })
        .forEach(
            v -> {
              while (v != null) {
                v = inlineNode(v);
              }
            });
  }

  /**
   * If the parent of {@code v} is a field access inlines both nodes to a var ref. If the parent is
   * a method call, inlines both nodes into a function call.
   *
   * @return the resulting inlined node if it is a var ref, so that chains of field accesses can be
   *     handled in a while loop.
   */
  private VarRefNode inlineNode(VarRefNode v) {
    ParentExprNode parent = v.getParent();
    ExprNode inlined = null;
    SourceLocation fullLocation;
    if (parent.getKind() == Kind.FIELD_ACCESS_NODE) {
      // Convert:
      // FIELD_ACCESS(VAR_REF("t"), "fieldName") -> VAR_REF("t.fieldName")
      fullLocation = v.getSourceLocation().extend(parent.getSourceLocation());
      inlined =
          resolveField(
              v, parent.getKind(), ((FieldAccessNode) parent).getFieldName(), fullLocation);
    } else if (parent.getKind() == Kind.METHOD_CALL_NODE && parent.getChildIndex(v) == 0) {
      // Convert:
      // METHOD_CALL("methodName", VAR_REF("t"), ...) -> FUNCTION(VAR_REF("t.methodName"), ...)
      fullLocation =
          v.getSourceLocation().extend(((MethodCallNode) parent).getMethodName().location());
      ExprNode target =
          resolveField(
              v,
              parent.getKind(),
              ((MethodCallNode) parent).getMethodName().identifier(),
              fullLocation);
      if (target == null) {
        return null;
      }
      FunctionNode function =
          CallableExprBuilder.builder((MethodCallNode) parent)
              .setSourceLocation(v.getSourceLocation().extend(parent.getSourceLocation()))
              .setTarget(null)
              .setIdentifier(null)
              .setFunctionExpr(target)
              .buildFunction();
      ResolvePluginsPass.setSoyFunctionForNameExpr(function);
      inlined = function;
    }

    if (inlined != null) {
      parent.getParent().replaceChild(parent, inlined);
      if (inlined instanceof VarRefNode) {
        return (VarRefNode) inlined;
      }
    }

    return null;
  }

  /**
   * Returns a new expr node that evaluates the value of the field named {@code fieldName} on the
   * {@code refn} value. Returns either a primitive node or var ref node or null if the field could
   * not be resolved.
   */
  @Nullable
  private ExprNode resolveField(
      VarRefNode refn, Kind kind, String fieldName, SourceLocation fullLocation) {
    VarDefn defn = refn.getDefnDecl();
    SoyType type = defn.type();

    if (type.getKind() == SoyType.Kind.PROTO_ENUM_TYPE) {
      EnumDescriptor enumDescriptor = ((ProtoEnumImportType) type).getDescriptor();
      EnumValueDescriptor val = enumDescriptor.findValueByName(fieldName);
      Identifier id = Identifier.create(refn.getName() + "." + fieldName, fullLocation);
      SoyProtoEnumType soyType = typeRegistry.getOrCreateProtoEnumType(enumDescriptor);
      if (val != null) {
        return new ProtoEnumValueNode(id, soyType, val.getNumber());
      } else {
        errorReporter.report(fullLocation, ENUM_MEMBERSHIP_ERROR, fieldName, refn.getName());
        return new ProtoEnumValueNode(id, soyType, 0);
      }
    }

    SoyType nestedType = UnknownType.getInstance();

    if (type.getKind() == SoyType.Kind.PROTO_MODULE) {
      nestedType =
          typeRegistry.getProtoImportType(
              ((ProtoModuleImportType) type).getDescriptor(), fieldName);
    } else if (type.getKind() == SoyType.Kind.PROTO_TYPE) {
      nestedType =
          typeRegistry.getProtoImportType(((ProtoImportType) type).getDescriptor(), fieldName);
    } else if (type.getKind() == SoyType.Kind.TEMPLATE_MODULE) {
      TemplateModuleImportType moduleType = (TemplateModuleImportType) type;
      if (moduleType.getTemplateNames().contains(fieldName)) {
        nestedType = typeRegistry.intern(TemplateImportType.create(moduleType, fieldName));
      }
    }

    if (nestedType == UnknownType.getInstance()) {
      // Unknown methods will be reported later.
      if (kind != Kind.METHOD_CALL_NODE) {
        String didYouMean = "";
        if (type instanceof ImportType) {
          didYouMean =
              SoyErrors.getDidYouMeanMessage(((ImportType) type).getNestedSymbolNames(), fieldName);
        }

        errorReporter.report(
            fullLocation, NO_SUCH_NESTED_TYPE, fieldName, englishForType(type), type, didYouMean);
      }
      return null;
    }

    VarDefn newDefn = ImportedVar.nested(defn, nestedType);
    return new VarRefNode(refn.getName() + "." + fieldName, fullLocation, newDefn);
  }

  private static String englishForType(SoyType type) {
    switch (type.getKind()) {
      case PROTO_TYPE:
        return "proto message";
      case PROTO_ENUM_TYPE:
        return "proto enum";
      case PROTO_EXTENSION:
        return "proto extension";
      case PROTO_MODULE:
        return "proto module";
      case TEMPLATE_TYPE:
        return "template";
      case TEMPLATE_MODULE:
        return "template module";
      default:
        return "type";
    }
  }
}
