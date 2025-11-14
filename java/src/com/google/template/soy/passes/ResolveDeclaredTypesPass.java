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
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.TypeLiteralNode;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.ConstNode;
import com.google.template.soy.soytree.ExternNode;
import com.google.template.soy.soytree.FileMetadata;
import com.google.template.soy.soytree.FileSetMetadata;
import com.google.template.soy.soytree.ImportNode;
import com.google.template.soy.soytree.ImportNode.ImportType;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateElementNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TypeDefNode;
import com.google.template.soy.soytree.defn.SymbolVar.SymbolKind;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.soytree.defn.TemplateStateVar;
import com.google.template.soy.types.DelegatingSoyTypeRegistry;
import com.google.template.soy.types.NamedType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.UnknownType;
import com.google.template.soy.types.ast.IntersectionTypeNode;
import com.google.template.soy.types.ast.TypeNode;
import com.google.template.soy.types.ast.TypeNodeConverter;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/** Resolves all {@link TypeNode}s in the AST. */
final class ResolveDeclaredTypesPass extends AbstractTopologicallyOrderedPass {
  private final ErrorReporter errorReporter;
  private final boolean disableAllTypeChecking;
  private final boolean allowMissingSoyDeps;

  private AccumulatingTypeRegistry typeRegistry;
  private TypeNodeConverter converter;

  private static final SoyErrorKind TYPE_NAME_BUILTIN_COLLISION =
      SoyErrorKind.of(
          "Type ''{0}'' name is already a built-in or imported type {1}.",
          Impression.ERROR_RESOLVE_DECLARED_TYPES_PASS_TYPE_NAME_BUILTIN_COLLISION);

  private static final SoyErrorKind TYPE_NAME_COLLISION =
      SoyErrorKind.of(
          "Type ''{0}'' is already defined in this file.",
          Impression.ERROR_RESOLVE_DECLARED_TYPES_PASS_TYPE_NAME_COLLISION);

  private static final SoyErrorKind INTERSECTION_NOT_ALLOWED =
      SoyErrorKind.of(
          "An intersection type is not allowed here. Extract into a '{type}' instead.",
          Impression.ERROR_RESOLVE_DECLARED_TYPES_PASS_INTERSECTION_NOT_ALLOWED);

  public ResolveDeclaredTypesPass(
      ErrorReporter errorReporter,
      boolean disableAllTypeChecking,
      boolean allowMissingSoyDeps,
      Supplier<FileSetMetadata> fileSetMetadataFromDeps) {
    super(fileSetMetadataFromDeps);
    this.errorReporter = errorReporter;
    this.disableAllTypeChecking = disableAllTypeChecking;
    this.allowMissingSoyDeps = allowMissingSoyDeps;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    typeRegistry = new AccumulatingTypeRegistry(file.getSoyTypeRegistry());
    converter =
        TypeNodeConverter.builder(errorReporter)
            .setTypeRegistry(typeRegistry)
            .setReportMissingTypes(!disableAllTypeChecking && !allowMissingSoyDeps)
            .build();
    new NodeVisitor().exec(file);
  }

  private class NodeVisitor extends AbstractSoyNodeVisitor<Void> {

    private final ExprVisitor exprVisitor = new ExprVisitor();

    @Override
    protected void visitImportNode(ImportNode node) {
      if (node.getImportType() == ImportType.TEMPLATE) {
        FileMetadata fileMetadata = getFileMetadata(node.getSourceFilePath());

        if (fileMetadata == null) {
          super.visitImportNode(node);
          return;
        }

        if (node.isModuleImport()) {
          fileMetadata
              .getTypeDefs()
              .forEach(
                  typeDef -> {
                    boolean unusedTrue =
                        typeRegistry.addTypeAlias(
                            node.getModuleAlias() + "." + typeDef.getName(), typeDef.getType());
                  });
        } else {
          node.getIdentifiers().stream()
              .filter(v -> v.getSymbolKind() == SymbolKind.TYPEDEF)
              .forEach(
                  var -> {
                    if (!var.hasType()) {
                      FileMetadata.TypeDef typeDef = fileMetadata.getTypeDef(var.getSymbol());
                      var.setType(typeDef.getType());
                    }
                    boolean unusedTrue =
                        typeRegistry.addTypeAlias(var.name(), (NamedType) var.authoredType());
                  });
        }
      }
      super.visitImportNode(node);
    }

    @Override
    protected void visitConstNode(ConstNode node) {
      visitTypeNode(node.getTypeNode());
      super.visitConstNode(node);
    }

    @Override
    protected void visitExternNode(ExternNode node) {
      visitTypeNode(node.getTypeNode());
      for (TemplateParam paramVar : node.getParamVars()) {
        paramVar.setType(paramVar.getTypeNode().getResolvedType());
      }
      super.visitExternNode(node);
    }

    @Override
    protected void visitTypeDefNode(TypeDefNode node) {
      visitTypeNode(node.getTypeNode());

      if (typeRegistry.hasType(node.getName())) {
        errorReporter.report(
            node.getNameLocation(),
            TYPE_NAME_BUILTIN_COLLISION,
            node.getName(),
            typeRegistry.getType(node.getName()).getKind()
                + " "
                + typeRegistry.getType(node.getName()));
      } else {
        NamedType namedType = typeRegistry.intern(node.asNamedType());
        if (!typeRegistry.addTypeAlias(node.getName(), namedType)) {
          errorReporter.report(node.getNameLocation(), TYPE_NAME_COLLISION, node.getName());
        }
      }
      super.visitTypeDefNode(node);
    }

    @Override
    protected void visitTemplateNode(TemplateNode node) {
      for (TemplateParam param : node.getAllParams()) {
        if (param.getTypeNode() != null) {
          // Disallow inline intersection types because there's no way to model them inline in JSC
          // annotations. Params are part of the JS template API so it's important to not type them
          // as `?`. We could allow this if we extract all inline defs into local named types.
          noIntersection(param.getTypeNode());

          SoyType paramType = converter.getOrCreateType(param.getTypeNode());
          if (param.isExplicitlyOptional()) {
            paramType = SoyTypes.unionWithUndefined(paramType);
          } else if (param.hasDefault()
              && param.defaultValue().getRoot().getKind() != ExprNode.Kind.UNDEFINED_NODE) {
            paramType = SoyTypes.excludeUndefined(paramType);
          }
          param.setType(paramType);
        } else if (disableAllTypeChecking) {
          // If there's no type node, this is a default parameter. Normally, we'd set the type on
          // this once we figure out the type of the default expression in
          // ResolveExpressionTypesPass. But if type checking is disabled that pass won't run, so
          // instead we set the type to unknown here, because later parts of the compiler require a
          // (non-null) type.
          param.setType(UnknownType.getInstance());
        }
      }
      super.visitTemplateNode(node);
    }

    @Override
    protected void visitTemplateElementNode(TemplateElementNode node) {
      for (TemplateStateVar state : node.getStateVars()) {
        if (state.getTypeNode() != null) {
          noIntersection(state.getTypeNode());
          state.setType(converter.getOrCreateType(state.getTypeNode()));
        }
      }
      super.visitTemplateElementNode(node);
    }

    @Override
    protected void visitSoyNode(SoyNode node) {
      if (node instanceof ExprHolderNode) {
        ((ExprHolderNode) node).getExprList().forEach(exprVisitor::exec);
      }
      if (node instanceof ParentSoyNode) {
        visitChildren((ParentSoyNode<?>) node);
      }
    }

    private void noIntersection(TypeNode node) {
      SoyTreeUtils.allTypeNodes(node)
          .filter(IntersectionTypeNode.class::isInstance)
          .findFirst()
          .ifPresent(n -> errorReporter.report(n.sourceLocation(), INTERSECTION_NOT_ALLOWED));
    }
  }

  private class ExprVisitor extends AbstractExprNodeVisitor<Void> {

    @Override
    protected void visitTypeLiteralNode(TypeLiteralNode node) {
      visitTypeNode(node.getTypeNode());
    }

    @Override
    protected void visitExprNode(ExprNode node) {
      if (node instanceof ParentExprNode) {
        visitChildren((ParentExprNode) node);
      }
    }
  }

  /** A SoyTypeRegistry that we add all `{type}` named types to as we traverse the AST. */
  private static class AccumulatingTypeRegistry extends DelegatingSoyTypeRegistry {

    private final Map<String, SoyType> localTypes = new HashMap<>();

    public AccumulatingTypeRegistry(SoyTypeRegistry delegate) {
      super(delegate);
    }

    public boolean addTypeAlias(String alias, NamedType type) {
      SoyType previous = localTypes.put(alias, type);
      return previous == null;
    }

    @Nullable
    @Override
    public SoyType getType(String typeName) {
      SoyType local = localTypes.get(typeName);
      if (local != null) {
        return local;
      }
      return super.getType(typeName);
    }
  }

  private void visitTypeNode(@Nullable TypeNode typeNode) {
    if (typeNode != null) {
      SoyType unused = converter.exec(typeNode);
    }
  }
}
