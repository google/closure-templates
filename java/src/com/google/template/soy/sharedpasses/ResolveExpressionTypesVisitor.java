/*
 * Copyright 2013 Google Inc.
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

package com.google.template.soy.sharedpasses;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.basetree.SyntaxVersionBound;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.AbstractOperatorNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.ExprNode.PrimitiveNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FieldAccessNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.ItemAccessNode;
import com.google.template.soy.exprtree.ListLiteralNode;
import com.google.template.soy.exprtree.MapLiteralNode;
import com.google.template.soy.exprtree.OperatorNodes.AndOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ConditionalOpNode;
import com.google.template.soy.exprtree.OperatorNodes.DivideByOpNode;
import com.google.template.soy.exprtree.OperatorNodes.EqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.GreaterThanOpNode;
import com.google.template.soy.exprtree.OperatorNodes.GreaterThanOrEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.LessThanOpNode;
import com.google.template.soy.exprtree.OperatorNodes.LessThanOrEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.MinusOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ModOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NegativeOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NullCoalescingOpNode;
import com.google.template.soy.exprtree.OperatorNodes.OrOpNode;
import com.google.template.soy.exprtree.OperatorNodes.PlusOpNode;
import com.google.template.soy.exprtree.OperatorNodes.TimesOpNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.ExprUnion;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForeachNonemptyNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoySyntaxExceptionUtils;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.types.SoyObjectType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeOps;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.aggregate.ListType;
import com.google.template.soy.types.aggregate.MapType;
import com.google.template.soy.types.aggregate.UnionType;
import com.google.template.soy.types.primitive.BoolType;
import com.google.template.soy.types.primitive.IntType;
import com.google.template.soy.types.primitive.NullType;
import com.google.template.soy.types.primitive.SanitizedType;
import com.google.template.soy.types.primitive.StringType;
import com.google.template.soy.types.primitive.UnknownType;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Visitor which resolves all expression types.
 *
 */
public final class ResolveExpressionTypesVisitor extends AbstractSoyNodeVisitor<Void> {

  /** User-declared syntax version. */
  private final SyntaxVersion declaredSyntaxVersion;

  /** Common operations on Soy types. */
  private final SoyTypeOps typeOps;

  /** Current set of type substitutions. */
  private TypeSubstitution substitutions;

  public ResolveExpressionTypesVisitor(
      SoyTypeRegistry typeRegistry, SyntaxVersion declaredSyntaxVersion) {
    this.typeOps = new SoyTypeOps(typeRegistry);
    this.declaredSyntaxVersion = declaredSyntaxVersion;
  }

  @Override protected void visitTemplateNode(TemplateNode node) {
    visitSoyNode(node);
  }

  @Override protected void visitPrintNode(PrintNode node) {
    visitSoyNode(node);

    ExprRootNode<?> expr = node.getExprUnion().getExpr();
    if (expr != null && expr.getType().equals(BoolType.getInstance())) {
      String errorMsg = "Bool values can no longer be printed";
      // Append some possibly helpful info in the case that the expr's top level is the 'or'
      // operator and the declaredSyntaxVersion is 2.3+ (meaning we resolved the 'or' output to be
      // type bool).
      if (declaredSyntaxVersion.num >= SyntaxVersion.V2_3.num &&
          expr.getChild(0) instanceof OrOpNode) {
        errorMsg += " (if you're intending the 'or' operator to return one of the operands" +
            " instead of bool, please use the binary null-coalescing operator '?:' instead)";
      }
      errorMsg += ".";
      node.maybeSetSyntaxVersionBound(new SyntaxVersionBound(SyntaxVersion.V2_3, errorMsg));
    }
  }

  @Override protected void visitLetValueNode(LetValueNode node) {
    visitSoyNode(node);
    node.getVar().setType(node.getValueExpr().getType());
  }

  @Override protected void visitLetContentNode(LetContentNode node) {
    visitSoyNode(node);
    SoyType varType = StringType.getInstance();
    if (node.getContentKind() != null) {
      switch (node.getContentKind()) {
        case ATTRIBUTES:
          varType = SanitizedType.AttributesType.getInstance();
          break;
        case CSS:
          varType = SanitizedType.CssType.getInstance();
          break;
        case HTML:
          varType = SanitizedType.HtmlType.getInstance();
          break;
        case JS:
          varType = SanitizedType.JsType.getInstance();
          break;
        case URI:
          varType = SanitizedType.UriType.getInstance();
          break;
        case TEXT:
        default:
          break;
      }
    }
    node.getVar().setType(varType);
  }

  @Override protected void visitForNode(ForNode node) {
    // Visit the range expressions.
    visitExpressions(node);

    // Set the type of the loop variable.
    node.getVar().setType(IntType.getInstance());

    // Visit the node body
    visitChildren(node);
  }

  @Override protected void visitIfNode(IfNode node) {
    // TODO(user): Also support switch / case.
    TypeSubstitution savedSubstitutionState = substitutions;
    for (SoyNode child : node.getChildren()) {
      if (child instanceof IfCondNode) {
        IfCondNode icn = (IfCondNode) child;
        visitExpressions(icn);

        // Visit the conditional expression to compute which types can be narrowed.
        TypeNarrowingConditionVisitor visitor = new TypeNarrowingConditionVisitor();
        if (icn.getExprUnion().getExpr() != null) {
          visitor.exec(icn.getExprUnion().getExpr());
        }

        // Save the state of substitutions from the previous if block.
        TypeSubstitution previousSubstitutionState = substitutions;

        // Modify the current set of type substitutions for the 'true' branch
        // of the if statement.
        addTypeSubstitutions(visitor.positiveTypeConstraints);
        visitChildren(icn);

        // Rewind the substitutions back to the state before the if-condition.
        // Add in the negative substitutions, which will affect subsequent blocks
        // of the if statement.
        // So for example if we have a variable whose type is (A|B|C) and the
        // first if-block tests whether that variable is type A, then in the
        // 'else' block it must be of type (B|C); If a subsequent 'elseif'
        // statement tests whether it's type B, then in the following else block
        // it can only be of type C.
        substitutions = previousSubstitutionState;
        addTypeSubstitutions(visitor.negativeTypeConstraints);
      } else if (child instanceof IfElseNode) {
        // For the else node, we simply inherit the previous set of subsitutions.
        IfElseNode ien = (IfElseNode) child;
        visitChildren(ien);
      }
    }
    substitutions = savedSubstitutionState;
  }

  // Given a map of type subsitutions, add all the entries to the current set of
  // active substitutions.
  private void addTypeSubstitutions(Map<VarDefn, SoyType> substitutionsToAdd) {
    for (Map.Entry<VarDefn, SoyType> entry : substitutionsToAdd.entrySet()) {
      VarDefn defn = entry.getKey();
      // Get the existing type
      SoyType previousType = defn.type();
      for (TypeSubstitution subst = substitutions; subst != null; subst = subst.parent) {
        if (subst.defn == defn) {
          previousType = subst.type;
          break;
        }
      }

      // If the new type is different than the current type, then add a new type substitution.
      if (entry.getValue() != previousType) {
        substitutions = new TypeSubstitution(substitutions, entry.getKey(), entry.getValue());
      }
    }
  }

  @Override protected void visitForeachNonemptyNode(ForeachNonemptyNode node) {
    // Visit the foreach iterator expression
    visitExpressions(node.getParent());
    // Set the inferred type of the loop variable.
    node.getVar().setType(getElementType(node.getExpr().getType(), node.getParent()));
    // Visit the node body
    visitChildren(node);
  }


  @Override protected void visitSoyNode(SoyNode node) {
    if (node instanceof ExprHolderNode) {
      visitExpressions((ExprHolderNode) node);
    }

    if (node instanceof ParentSoyNode<?>) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }

  private void visitExpressions(ExprHolderNode node) {
    ResolveTypesExprVisitor exprVisitor = new ResolveTypesExprVisitor(node);
    for (ExprUnion exprUnion : node.getAllExprUnions()) {
      if (exprUnion.getExpr() != null) {
        exprVisitor.exec(exprUnion.getExpr());
      }
    }
  }

  /**
   * Given a collection type, compute the element type.
   * @param collectionType The base type.
   * @param owningNode The current error context, in other words the SoyNode owning the
   *     expression being scanned.
   * @return The type of the elements of the collection.
   */
  private SoyType getElementType(SoyType collectionType, ExprHolderNode owningNode) {
    Preconditions.checkNotNull(collectionType);
    switch (collectionType.getKind()) {
      case UNKNOWN:
        // If we don't know anything about the base type, then make no assumptions
        // about the field type.
        return UnknownType.getInstance();

      case LIST:
        return ((ListType) collectionType).getElementType();

      case UNION: {
        // If it's a union, then do the field type calculation for each member of
        // the union and combine the result.
        UnionType unionType = (UnionType) collectionType;
        List<SoyType> fieldTypes = Lists.newArrayList();
        for (SoyType unionMember : unionType.getMembers()) {
          fieldTypes.add(getElementType(unionMember, owningNode));
        }
        return typeOps.computeLeastCommonType(fieldTypes);
      }

      default:
        throw SoySyntaxExceptionUtils.createWithNode(
            "Cannot compute element type for collection of type '" + collectionType, owningNode);
    }
  }


  // -----------------------------------------------------------------------------------------------
  // Expr visitor.


  /**
   * Visitor which resolves all variable and parameter references in expressions
   * to point to the corresponding declaration object.
   */
  private class ResolveTypesExprVisitor extends AbstractExprNodeVisitor<Void> {

    /** SoyNode owning the expression; Used for error reporting. */
    private final ExprHolderNode owningSoyNode;

    /** The root node of the current expression being visited (during an exec call). */
    private ExprRootNode<?> currExprRootNode;

    /**
     * Construct a new ResolveNamesExprVisitor.
     * @param owningSoyNode The current error context, in other words the SoyNode owning the
     *     expression being scanned.
     */
    public ResolveTypesExprVisitor(ExprHolderNode owningSoyNode) {
      this.owningSoyNode = owningSoyNode;
    }

    @Override public Void exec(ExprNode node) {
      Preconditions.checkArgument(node instanceof ExprRootNode<?>);
      this.currExprRootNode = (ExprRootNode<?>) node;
      visit(node);
      this.currExprRootNode = null;
      return null;
    }

    @Override protected void visit(ExprNode node) {
      super.visit(node);
      requireNodeType(node);
    }

    @Override protected void visitExprRootNode(ExprRootNode<?> node) {
      visitChildren(node);
      ExprNode expr = node.getChild(0);
      node.setType(expr.getType());
    }

    @Override protected void visitPrimitiveNode(PrimitiveNode node) {
      // We don't do anything here because primitive nodes already have type information.
    }

    @Override protected void visitListLiteralNode(ListLiteralNode node) {
      visitChildren(node);
      List<SoyType> elementTypes = Lists.newArrayList();
      for (ExprNode child : node.getChildren()) {
        requireNodeType(child);
        elementTypes.add(child.getType());
      }
      // Special case for empty list.
      if (elementTypes.isEmpty()) {
        elementTypes.add(UnknownType.getInstance());
      }
      node.setType(typeOps.getTypeRegistry().getOrCreateListType(
          typeOps.computeLeastCommonType(elementTypes)));
    }

    @Override protected void visitMapLiteralNode(MapLiteralNode node) {

      visitChildren(node);

      int numChildren = node.numChildren();
      if (numChildren % 2 != 0) {
        throw new AssertionError();
      }

      SoyType commonKeyType;
      SoyType commonValueType;
      Multimap<String, SoyType> recordFieldTypes = HashMultimap.create();
      if (numChildren == 0) {
        commonKeyType = UnknownType.getInstance();
        commonValueType = UnknownType.getInstance();
      } else {
        List<SoyType> keyTypes = Lists.newArrayListWithCapacity(numChildren / 2);
        List<SoyType> valueTypes = Lists.newArrayListWithCapacity(numChildren / 2);
        for (int i = 0; i < numChildren; i += 2) {
          ExprNode key = node.getChild(i);
          ExprNode value = node.getChild(i + 1);
          if (key.getKind() == ExprNode.Kind.STRING_NODE) {
            String fieldName = ((StringNode) key).getValue();
            recordFieldTypes.put(fieldName, value.getType());
          }
          keyTypes.add(key.getType());
          valueTypes.add(value.getType());
        }
        commonKeyType = typeOps.computeLeastCommonType(keyTypes);
        commonValueType = typeOps.computeLeastCommonType(valueTypes);
      }

      if (StringType.getInstance().isAssignableFrom(commonKeyType)) {
        // Case 1: Keys are all strings (or unknown). We should be creating a record for the user.
        Map<String, SoyType> leastCommonFieldTypes = Maps.newHashMap();
        for (String fieldName : recordFieldTypes.keySet()) {
          leastCommonFieldTypes.put(
              fieldName,
              typeOps.computeLeastCommonType(recordFieldTypes.get(fieldName)));
        }
        node.setType(typeOps.getTypeRegistry().getOrCreateRecordType(leastCommonFieldTypes));
      } else {
        // Case 2: Keys are not all strings. We should be creating a map for the user.
        node.setType(typeOps.getTypeRegistry().getOrCreateMapType(commonKeyType, commonValueType));
      }
    }

    @Override protected void visitVarRefNode(VarRefNode varRef) {
      if (varRef.getType() == null) {
        throw createExceptionForInvalidExpr("Missing Soy type for variable: " + varRef.getName());
      }
      // If there's a type substitution in effect for this variable, then change
      // the type of the variable reference to the substituted type.
      for (TypeSubstitution subst = substitutions; subst != null; subst = subst.parent) {
        if (subst.defn == varRef.getDefnDecl()) {
          varRef.setSubstituteType(subst.type);
          break;
        }
      }
    }

    @Override protected void visitFieldAccessNode(FieldAccessNode node) {
      visit(node.getBaseExprChild());
      node.setType(getFieldType(
          node.getBaseExprChild().getType(), node.getFieldName(), node.isNullSafe()));
    }

    @Override protected void visitItemAccessNode(ItemAccessNode node) {
      visit(node.getBaseExprChild());
      visit(node.getKeyExprChild());
      node.setType(getItemType(
          node.getBaseExprChild().getType(), node.getKeyExprChild().getType()));
    }

    @Override protected void visitGlobalNode(GlobalNode node) {
      // Do nothing, global nodes already have type information.
    }

    @Override protected void visitNegativeOpNode(NegativeOpNode node) {
      visitChildren(node);
      node.setType(node.getChild(0).getType());
    }

    @Override protected void visitNotOpNode(NotOpNode node) {
      visitChildren(node);
      node.setType(BoolType.getInstance());
    }

    @Override protected void visitTimesOpNode(TimesOpNode node) {
      visitArithmeticOpNode(node);
    }

    @Override protected void visitDivideByOpNode(DivideByOpNode node) {
      visitArithmeticOpNode(node);
    }

    @Override protected void visitModOpNode(ModOpNode node) {
      visitArithmeticOpNode(node);
    }

    @Override protected void visitPlusOpNode(PlusOpNode node) {
      // TODO: Plus is overloaded, so may need special handling here.
      visitArithmeticOpNode(node);
    }

    @Override protected void visitMinusOpNode(MinusOpNode node) {
      visitArithmeticOpNode(node);
    }

    @Override protected void visitLessThanOpNode(LessThanOpNode node) {
      visitComparisonOpNode(node);
    }

    @Override protected void visitGreaterThanOpNode(GreaterThanOpNode node) {
      visitComparisonOpNode(node);
    }

    @Override protected void visitLessThanOrEqualOpNode(LessThanOrEqualOpNode node) {
      visitComparisonOpNode(node);
    }

    @Override protected void visitGreaterThanOrEqualOpNode(GreaterThanOrEqualOpNode node) {
      visitComparisonOpNode(node);
    }

    @Override protected void visitEqualOpNode(EqualOpNode node) {
      visitComparisonOpNode(node);
    }

    @Override protected void visitNotEqualOpNode(NotEqualOpNode node) {
      visitComparisonOpNode(node);
    }

    @Override protected void visitAndOpNode(AndOpNode node) {
      visitLogicalOpNode(node);
    }

    @Override protected void visitOrOpNode(OrOpNode node) {
      visitLogicalOpNode(node);
    }

    @Override protected void visitNullCoalescingOpNode(NullCoalescingOpNode node) {
      visit(node.getChild(0));

      // Save the state of substitutions.
      TypeSubstitution savedSubstitutionState = substitutions;

      // Visit the conditional expression to compute which types can be narrowed.
      TypeNarrowingConditionVisitor visitor = new TypeNarrowingConditionVisitor();
      visitor.visitAndImplicitlyCastToBoolean(node.getChild(0));

      // Now, re-visit the first node but with subsitutions. The reason is because
      // the value of node 0 is what will be returned if node 0 is truthy.
      addTypeSubstitutions(visitor.positiveTypeConstraints);
      visit(node.getChild(0));

      // For the null-coalescing operator, the node 1 only gets evaluated
      // if node 0 is falsey. Use the negative substitutions for this case.
      addTypeSubstitutions(visitor.negativeTypeConstraints);
      visit(node.getChild(1));

      // Restore substitutions to previous state
      substitutions = savedSubstitutionState;

      node.setType(typeOps.computeLeastCommonType(
          node.getChild(0).getType(), node.getChild(1).getType()));
    }

    @Override protected void visitConditionalOpNode(ConditionalOpNode node) {
      visit(node.getChild(0));

      // Save the state of substitutions.
      TypeSubstitution savedSubstitutionState = substitutions;

      // Visit the conditional expression to compute which types can be narrowed.
      TypeNarrowingConditionVisitor visitor = new TypeNarrowingConditionVisitor();
      visitor.visitAndImplicitlyCastToBoolean(node.getChild(0));

      // Modify the current set of type substitutions for the 'true' branch
      // of the conditional.
      addTypeSubstitutions(visitor.positiveTypeConstraints);
      visit(node.getChild(1));

      // Rewind the substitutions back to the state before the expression.
      // Add in the negative substitutions, which will affect the 'false'
      // branch.
      substitutions = savedSubstitutionState;
      addTypeSubstitutions(visitor.negativeTypeConstraints);
      visit(node.getChild(2));

      // Restore substitutions to previous state
      substitutions = savedSubstitutionState;

      // For a conditional node, it will return either child 1 or 2.
      node.setType(typeOps.computeLeastCommonType(
          node.getChild(1).getType(),
          node.getChild(2).getType()));
    }

    @Override protected void visitFunctionNode(FunctionNode node) {
      // We have no way of knowing the return type of a function.
      // TODO: think about adding function type declarations.
      visitChildren(node);
      node.setType(UnknownType.getInstance());
    }

    private void visitLogicalOpNode(AbstractOperatorNode node) {
      visitChildren(node);

      if (declaredSyntaxVersion.num >= SyntaxVersion.V2_3.num) {
        node.setType(BoolType.getInstance());
      } else {
        // In legacy Soy behavior, depending on the language, the 'and' and 'or' operators may
        // return a bool or may return the left or right hand value. So in this case we'll just give
        // up and return unknown type.
        node.setType(UnknownType.getInstance());
      }
    }

    private void visitComparisonOpNode(AbstractOperatorNode node) {
      visitChildren(node);
      node.setType(BoolType.getInstance());
    }

    private void visitArithmeticOpNode(AbstractOperatorNode node) {
      visitChildren(node);
      node.setType(typeOps.computeLeastCommonTypeArithmetic(
          node.getChild(0).getType(),
          node.getChild(1).getType()));
    }

    /**
     * Helper function that throws an exception if a node's type field is {@code null}.
     * The exception will show what kind of node it was, and where in the template it
     * was found.
     * @param node The node to check.
     */
    private void requireNodeType(ExprNode node) {
      if (node.getType() == null) {
        throw createExceptionForInvalidExpr(
            "Missing Soy type for node: " + node.getClass().getName());
      }
    }

    /**
     * Given a base type and a field name, compute the field type.
     * @param baseType The base type.
     * @param fieldName The name of the field.
     * @return The type of the field.
     */
    private SoyType getFieldType(SoyType baseType, String fieldName, boolean isNullSafe) {
      Preconditions.checkNotNull(baseType);
      switch (baseType.getKind()) {
        case UNKNOWN:
          // If we don't know anything about the base type, then make no assumptions
          // about the field type.
          return UnknownType.getInstance();

        case OBJECT: {
          // The base type is an object, so look up the field.
          SoyType fieldType = ((SoyObjectType) baseType).getFieldType(fieldName);
          if (fieldType == null) {
            throw createExceptionForInvalidExpr(
                "Undefined field '" + fieldName + "' for object type " + baseType);
          }
          return fieldType;
        }

        case LIST: {
          if (fieldName.equals("length")) {
            // Backwards compatibility hack - allow list.length.
            currExprRootNode.maybeSetSyntaxVersionBound(new SyntaxVersionBound(
                SyntaxVersion.V2_3,
                "Soy lists do not have field 'length'. Use function length() instead."));
            return IntType.getInstance();
          } else {
            throw createExceptionForInvalidExpr(
                "Undefined field '" + fieldName + "' in type: " + baseType);
          }
        }

        case RECORD: {
          SoyType fieldType = ((SoyObjectType) baseType).getFieldType(fieldName);
          if (fieldType == null) {
            throw createExceptionForInvalidExpr(
                "Undefined field '" + fieldName + "' for record type " + baseType);
          }
          return fieldType;
        }

        case MAP: {
          throw createExceptionForInvalidExpr(
              "Dot-access not supported for type " + baseType + " (consider dict instead of map)");
        }

        case UNION: {
          // If it's a union, then do the field type calculation for each member of
          // the union and combine the result.
          UnionType unionType = (UnionType) baseType;
          List<SoyType> fieldTypes = Lists.newArrayList();
          for (SoyType unionMember : unionType.getMembers()) {
            // TODO: In the future when we have flow-based type analysis, only
            // exclude nulls when isNullSafe is true.
            if (unionMember.getKind() == SoyType.Kind.NULL) {
              continue;
            }
            fieldTypes.add(getFieldType(unionMember, fieldName, isNullSafe));
          }
          return typeOps.computeLeastCommonType(fieldTypes);
        }

        default:
          throw createExceptionForInvalidExpr(
              "Dot-access not supported for type " + baseType + ".");
      }
    }

    /**
     * Given a base type and an item key type, compute the item value type.
     * @param baseType The base type.
     * @param keyType The type of the item key.
     * @return The type of the item value.
     */
    private SoyType getItemType(SoyType baseType, SoyType keyType) {
      Preconditions.checkNotNull(baseType);
      Preconditions.checkNotNull(keyType);
      switch (baseType.getKind()) {
        case UNKNOWN:
          // If we don't know anything about the base type, then make no assumptions
          // about the item type.
          return UnknownType.getInstance();

        case LIST:
          ListType listType = (ListType) baseType;
          // For lists, the key type must either be unknown or assignable to integer.
          if (keyType.getKind() != SoyType.Kind.UNKNOWN &&
              !IntType.getInstance().isAssignableFrom(keyType)) {
            throw createExceptionForInvalidExpr(
                "Invalid index type " + keyType + " for list of type " + baseType);
          }
          return listType.getElementType();

        case MAP:
          MapType mapType = (MapType) baseType;
          // For maps, the key type must either be unknown or assignable to the declared key type.
          if (keyType.getKind() != SoyType.Kind.UNKNOWN &&
              !mapType.getKeyType().isAssignableFrom(keyType)) {
            throw createExceptionForInvalidExpr(
                "Invalid key type " + keyType + " for map of type " + baseType);
          }
          return mapType.getValueType();

        case UNION: {
          // If it's a union, then do the item type calculation for each member of
          // the union and combine the result.
          UnionType unionType = (UnionType) baseType;
          List<SoyType> itemTypes = Lists.newArrayList();
          for (SoyType unionMember : unionType.getMembers()) {
            itemTypes.add(getItemType(unionMember, keyType));
          }
          return typeOps.computeLeastCommonType(itemTypes);
        }

        default:
          throw createExceptionForInvalidExpr(
              "Type " + baseType + " does not support bracket-access.");
      }
    }

    /**
     * Private helper to create a SoySyntaxException whose error message incorporates both the
     * owningSoyNode and the currExprRootNode.
     */
    private SoySyntaxException createExceptionForInvalidExpr(String errorMsg) {
      return SoySyntaxExceptionUtils.createWithNode(
          "Invalid expression \"" + currExprRootNode.toSourceString() + "\": " + errorMsg,
          owningSoyNode);
    }
  }

  /**
   * Visitor which analyzes a boolean expression and determines if any of the variables
   * involved in the expression can have their types narrowed depending on the outcome
   * of the condition.
   *
   * For example, if the condition is "$var != null", then we know that if the condition
   * is true, then $var cannot be of null type. We also know that if the condition is
   * false, then $var must be of the null type.
   *
   * The result of the analysis is two "constraint maps", which map variables to types,
   * indicating that the variable satisfies the criteria of being of that type.
   *
   * The "positiveConstraints" map is the set of constraints that will be satisfied if
   * the condition is true, the "negativeConstraints" is the set of constraints that will
   * be satisfied if the condition is false.
   *
   * TODO(user) - support instanceof tests. Right now the only type tests supported are
   * comparisons with null. If we added an 'instanceof' operator to Soy, the combination
   * of instanceof + flow-based type analysis would effectively allow template authors
   * to do typecasts, without having to add a cast operator to the language.
   */
  private class TypeNarrowingConditionVisitor extends AbstractExprNodeVisitor<Void> {
    // Type constraints that are valid if the condition is true.
    public Map<VarDefn, SoyType> positiveTypeConstraints = Maps.newHashMap();

    // Type constraints that are valid if the condition is false.
    public Map<VarDefn, SoyType> negativeTypeConstraints = Maps.newHashMap();

    @Override public Void exec(ExprNode node) {
      visit(node);
      return null;
    }

    @Override protected void visitExprRootNode(ExprRootNode<?> node) {
      visitAndImplicitlyCastToBoolean(node.getChild(0));
    }

    public void visitAndImplicitlyCastToBoolean(ExprNode node) {
      // In places where the expression is implicitly cast to a boolean, treat
      // a reference to a variable as a comparison of that variable with null.
      // So for example an expression like {if $var} should be treated as
      // {if $var != null} but something like {if $var > 0} should not be changed.
      if (node.getKind() == ExprNode.Kind.VAR_REF_NODE) {
        VarRefNode varRef = (VarRefNode) node;
        positiveTypeConstraints.put(varRef.getDefnDecl(), removeNullability(varRef.getType()));
        negativeTypeConstraints.put(varRef.getDefnDecl(), NullType.getInstance());
      } else {
        visit(node);
      }
    }

    @Override protected void visitAndOpNode(AndOpNode node) {
      Preconditions.checkArgument(node.getChildren().size() == 2);
      // Create two separate visitors to analyze each side of the expression.
      TypeNarrowingConditionVisitor leftVisitor = new TypeNarrowingConditionVisitor();
      TypeNarrowingConditionVisitor rightVisitor = new TypeNarrowingConditionVisitor();
      leftVisitor.visitAndImplicitlyCastToBoolean(node.getChild(0));
      rightVisitor.visitAndImplicitlyCastToBoolean(node.getChild(1));

      // Both the left side and right side constraints will be valid if the condition is true.
      positiveTypeConstraints.putAll(computeUnion(
          leftVisitor.positiveTypeConstraints, rightVisitor.positiveTypeConstraints));
      // If the condition is false, then the overall constraint is the intersection of
      // the complements of the true constraints.
      negativeTypeConstraints.putAll(computeIntersection(
          leftVisitor.negativeTypeConstraints, rightVisitor.negativeTypeConstraints));
    }

    @Override protected void visitOrOpNode(OrOpNode node) {
      Preconditions.checkArgument(node.getChildren().size() == 2);
      // Create two separate visitors to analyze each side of the expression.
      TypeNarrowingConditionVisitor leftVisitor = new TypeNarrowingConditionVisitor();
      TypeNarrowingConditionVisitor rightVisitor = new TypeNarrowingConditionVisitor();
      leftVisitor.visitAndImplicitlyCastToBoolean(node.getChild(0));
      rightVisitor.visitAndImplicitlyCastToBoolean(node.getChild(1));

      // If the condition is true, then only constraints that appear on both sides of the
      // operator will be valid.
      positiveTypeConstraints.putAll(computeIntersection(
          leftVisitor.positiveTypeConstraints, rightVisitor.positiveTypeConstraints));
      // If the condition is false, then both sides must be false, so the overall constraint
      // is the union of the complements of the constraints on each side.
      negativeTypeConstraints.putAll(computeUnion(
          leftVisitor.negativeTypeConstraints, rightVisitor.negativeTypeConstraints));
    }

    @Override protected void visitNotOpNode(NotOpNode node) {
      // For a logical not node, compute the positive and negative constraints of the
      // operand and then simply swap them.
      TypeNarrowingConditionVisitor childVisitor = new TypeNarrowingConditionVisitor();
      childVisitor.visitAndImplicitlyCastToBoolean(node.getChild(0));
      positiveTypeConstraints.putAll(childVisitor.negativeTypeConstraints);
      negativeTypeConstraints.putAll(childVisitor.positiveTypeConstraints);
    }

    @Override protected void visitEqualOpNode(EqualOpNode node) {
      if (node.getChild(0).getKind() == ExprNode.Kind.VAR_REF_NODE) {
        if (node.getChild(1).getKind() == ExprNode.Kind.NULL_NODE) {
          VarRefNode varRef = (VarRefNode) node.getChild(0);
          positiveTypeConstraints.put(varRef.getDefnDecl(), NullType.getInstance());
          negativeTypeConstraints.put(varRef.getDefnDecl(), removeNullability(varRef.getType()));
        }
      } else if (node.getChild(1).getKind() == ExprNode.Kind.VAR_REF_NODE) {
        if (node.getChild(0).getKind() == ExprNode.Kind.NULL_NODE) {
          VarRefNode varRef = (VarRefNode) node.getChild(1);
          positiveTypeConstraints.put(varRef.getDefnDecl(), NullType.getInstance());
          negativeTypeConstraints.put(varRef.getDefnDecl(), removeNullability(varRef.getType()));
        }
      }
      // Otherwise don't make any inferences (don't visit children).
    }

    @Override protected void visitNotEqualOpNode(NotEqualOpNode node) {
      if (node.getChild(0).getKind() == ExprNode.Kind.VAR_REF_NODE) {
        if (node.getChild(1).getKind() == ExprNode.Kind.NULL_NODE) {
          VarRefNode varRef = (VarRefNode) node.getChild(0);
          positiveTypeConstraints.put(varRef.getDefnDecl(), removeNullability(varRef.getType()));
          negativeTypeConstraints.put(varRef.getDefnDecl(), NullType.getInstance());
        }
      } else if (node.getChild(1).getKind() == ExprNode.Kind.VAR_REF_NODE) {
        if (node.getChild(0).getKind() == ExprNode.Kind.NULL_NODE) {
          VarRefNode varRef = (VarRefNode) node.getChild(1);
          positiveTypeConstraints.put(varRef.getDefnDecl(), removeNullability(varRef.getType()));
          negativeTypeConstraints.put(varRef.getDefnDecl(), NullType.getInstance());
        }
      }
      // Otherwise don't make any inferences (don't visit children).
    }

    @Override protected void visitNullCoalescingOpNode(NullCoalescingOpNode node) {
      // Don't make any inferences (don't visit children).
      // Note: It would be possible to support this case by expanding it into
      // if-statements.
    }

    @Override protected void visitConditionalOpNode(ConditionalOpNode node) {
      // Don't make any inferences (don't visit children).
      // Note: It would be possible to support this case by expanding it into
      // if-statements.
    }

    @Override protected void visitFunctionNode(FunctionNode node) {
      // Handle 'isNonnull($variable)
      if (node.numChildren() == 1 && node.getFunctionName().equals("isNonnull")) {
        ExprNode argNode = node.getChild(0);
        if (argNode.getKind() == ExprNode.Kind.VAR_REF_NODE) {
          VarRefNode varRef = (VarRefNode) argNode;
          positiveTypeConstraints.put(varRef.getDefnDecl(), removeNullability(varRef.getType()));
          negativeTypeConstraints.put(varRef.getDefnDecl(), NullType.getInstance());
        }
      }
      // Otherwise don't make any inferences (don't visit children).
    }

    @Override protected void visitExprNode(ExprNode node) {
      if (node instanceof ParentExprNode) {
        visitChildren((ParentExprNode) node);
      }
    }

    /**
     * Compute a map which combines the constraints from both the left and right
     * side of an expression. The result should be a set of constraints which satisfy
     * <strong>both</strong> sides.
     * @param left Constraints from the left side.
     * @param right Constraints from the right side.
     * @return The combined constraint.
     */
    private Map<VarDefn, SoyType> computeUnion(
        Map<VarDefn, SoyType> left, Map<VarDefn, SoyType> right) {
      if (left.isEmpty()) {
        return right;
      }
      if (right.isEmpty()) {
        return left;
      }
      Map<VarDefn, SoyType> result = Maps.newHashMap(left);
      for (Map.Entry<VarDefn, SoyType> entry : right.entrySet()) {
        // The union of two constraints is a *stricter* constraint.
        // Thus "((a instanceof any) AND (a instanceof bool)) == (a instanceof bool)"
        if (left.containsKey(entry.getKey())) {
          // For now, it's sufficient that the map contains an entry for the variable
          // (since we're only testing for nullability). Once we add support for more
          // complex type tests, we'll need to add code here that combines the two
          // constraints.
        } else {
          result.put(entry.getKey(), entry.getValue());
        }
      }
      return result;
    }

    /**
     * Compute a map which combines the constraints from both the left and right
     * side of an expression. The result should be a set of constraints which satisfy
     * <strong>either</strong> sides.
     * @param left Constraints from the left side.
     * @param right Constraints from the right side.
     * @return The combined constraint.
     */
    private Map<VarDefn, SoyType> computeIntersection(
        Map<VarDefn, SoyType> left, Map<VarDefn, SoyType> right) {
      if (left.isEmpty()) {
        return left;
      }
      if (right.isEmpty()) {
        return right;
      }
      Map<VarDefn, SoyType> result = Maps.newHashMap();
      for (Map.Entry<VarDefn, SoyType> entry : left.entrySet()) {
        // A variable must be present in both the left and right sides in order to be
        // included in the output.
        if (right.containsKey(entry.getKey())) {
          // The intersection of two constraints is a *looser* constraint.
          // Thus "((a instanceof any) OR (a instanceof bool)) == (a instanceof any)"
          SoyType rightSideType = right.get(entry.getKey());
          result.put(entry.getKey(),
              typeOps.computeLeastCommonType(entry.getValue(), rightSideType));
        }
      }
      return result;
    }

    private SoyType removeNullability(SoyType type) {
      if (type.getKind() == SoyType.Kind.UNION) {
        // Filter out nulls from union.
        Set<SoyType> nonNullMemberTypes = Sets.filter(
            ((UnionType) type).getMembers(), new Predicate<SoyType> () {
              @Override public boolean apply(@Nullable SoyType memberType) {
                return memberType.getKind() != SoyType.Kind.NULL;
              }
            });
        if (nonNullMemberTypes.size() == 1) {
          return nonNullMemberTypes.iterator().next();
        } else {
          return typeOps.getTypeRegistry().getOrCreateUnionType(nonNullMemberTypes);
        }
      }
      return type;
    }
  }

  /**
   * Class that is used to temporarily substitute the type of a variable.
   */
  private static class TypeSubstitution {
    /** Parent substitution. */
    public final TypeSubstitution parent;

    /** The variable whose type we are overriding. */
    public final VarDefn defn;

    /** The new type of the variable. */
    public final SoyType type;

    public TypeSubstitution(@Nullable TypeSubstitution parent, VarDefn var, SoyType type) {
      this.parent = parent;
      this.defn = var;
      this.type = type;
    }
  }
}
