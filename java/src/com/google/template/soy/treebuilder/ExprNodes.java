/*
 * Copyright 2022 Google Inc.
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

package com.google.template.soy.treebuilder;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.base.internal.QuoteStyle;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.exprtree.BooleanNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.OperatorNode;
import com.google.template.soy.exprtree.FieldAccessNode;
import com.google.template.soy.exprtree.FloatNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.GroupNode;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.ItemAccessNode;
import com.google.template.soy.exprtree.ListComprehensionNode;
import com.google.template.soy.exprtree.ListLiteralNode;
import com.google.template.soy.exprtree.MethodCallNode;
import com.google.template.soy.exprtree.NullNode;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.exprtree.RecordLiteralNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.exprtree.VeLiteralNode;
import com.google.template.soy.shared.internal.BuiltinFunction;
import javax.annotation.Nullable;

/** Utility methods for constructing synthetic ExprNodes. */
public final class ExprNodes {
  public static OperatorNode and(ExprNode left, ExprNode right) {
    return operator(Operator.AND, left, right);
  }

  public static BooleanNode booleanLiteral(boolean value) {
    return new BooleanNode(value, SourceLocation.UNKNOWN);
  }

  public static ExprNode conditional(ExprNode condition, ExprNode left, ExprNode right) {
    // Simplification: for boolean literal conditional, omit the dead branch.
    if (condition instanceof BooleanNode) {
      return ((BooleanNode) condition).getValue() ? maybeCopyNode(left) : maybeCopyNode(right);
    }
    return operator(Operator.CONDITIONAL, condition, left, right);
  }

  public static GlobalNode global(String fullEnumName) {
    return new GlobalNode(identifier(fullEnumName));
  }

  public static OperatorNode equal(ExprNode left, ExprNode right) {
    return operator(Operator.EQUAL, left, right);
  }

  public static FieldAccessNode fieldAccess(ExprNode base, String fieldName, boolean isNullSafe) {
    return new FieldAccessNode(maybeCopyNode(base), fieldName, SourceLocation.UNKNOWN, isNullSafe);
  }

  public static FloatNode floatLiteral(double value) {
    return new FloatNode(value, SourceLocation.UNKNOWN);
  }

  public static FunctionNode function(String name, ExprNode... params) {
    return function(name, ImmutableList.copyOf(params));
  }

  public static FunctionNode function(String name, ImmutableList<ExprNode> params) {
    FunctionNode node = FunctionNode.newPositional(identifier(name), SourceLocation.UNKNOWN, null);
    node.addChildren(maybeCopyNodes(params));
    return node;
  }

  public static FunctionNode elementCall(String name, ImmutableList<NamedExprNode> params) {
    ImmutableList.Builder<Identifier> paramNames = ImmutableList.builder();
    for (NamedExprNode param : params) {
      paramNames.add(identifier(param.name()));
    }
    FunctionNode node =
        FunctionNode.newNamed(identifier(name), paramNames.build(), SourceLocation.UNKNOWN);
    for (NamedExprNode child : params) {
      node.addChild(maybeCopyNode(child.expr()));
    }
    return node;
  }

  public static OperatorNode greaterThan(ExprNode left, ExprNode right) {
    return operator(Operator.GREATER_THAN, left, right);
  }

  public static GroupNode group(ExprNode expr) {
    return new GroupNode(maybeCopyNode(expr), SourceLocation.UNKNOWN);
  }

  public static IntegerNode integerLiteral(long value) {
    return new IntegerNode(value, SourceLocation.UNKNOWN);
  }

  public static ItemAccessNode itemAccess(ExprNode base, ExprNode key, boolean isNullSafe) {
    return new ItemAccessNode(
        maybeCopyNode(base), maybeCopyNode(key), SourceLocation.UNKNOWN, isNullSafe);
  }

  public static ListComprehensionNode listComprehension(
      ExprNode base,
      String listItemVarName,
      @Nullable String indexVarName,
      ExprNode mapExpression,
      @Nullable ExprNode filterExpression) {
    return new ListComprehensionNode(
        maybeCopyNode(base),
        "$" + listItemVarName,
        SourceLocation.UNKNOWN,
        indexVarName == null ? null : "$" + indexVarName,
        SourceLocation.UNKNOWN,
        maybeCopyNode(mapExpression),
        filterExpression == null ? null : maybeCopyNode(filterExpression),
        SourceLocation.UNKNOWN,
        0 /* unused nodeId */);
  }

  public static ListLiteralNode listLiteral(ExprNode... items) {
    return listLiteral(ImmutableList.copyOf(items));
  }

  public static ListLiteralNode listLiteral(ImmutableList<ExprNode> items) {
    return new ListLiteralNode(maybeCopyNodes(items), SourceLocation.UNKNOWN);
  }

  public static MethodCallNode methodCall(
      ExprNode base, String methodName, boolean isNullSafe, ExprNode... params) {
    return MethodCallNode.newWithPositionalArgs(
        maybeCopyNode(base),
        ImmutableList.copyOf(maybeCopyNodes(params)),
        identifier(methodName),
        SourceLocation.UNKNOWN,
        isNullSafe);
  }

  public static OperatorNode notEqual(ExprNode left, ExprNode right) {
    return operator(Operator.NOT_EQUAL, left, right);
  }

  public static OperatorNode not(ExprNode operand) {
    return operator(Operator.NOT, operand);
  }

  public static NullNode nullLiteral() {
    return new NullNode(SourceLocation.UNKNOWN);
  }

  public static OperatorNode nullCoalescing(ExprNode base, ExprNode ifNull) {
    return operator(Operator.NULL_COALESCING, base, ifNull);
  }

  public static OperatorNode operator(Operator op, ImmutableList<ExprNode> children) {
    return operator(op, children.toArray(new ExprNode[] {}));
  }

  public static OperatorNode operator(Operator op, ExprNode... children) {
    return op.createNode(SourceLocation.UNKNOWN, SourceLocation.UNKNOWN, maybeCopyNodes(children));
  }

  public static OperatorNode or(ExprNode left, ExprNode right) {
    return operator(Operator.OR, left, right);
  }

  public static OperatorNode plus(ExprNode left, ExprNode right) {
    return operator(Operator.PLUS, left, right);
  }

  public static FunctionNode protoInit(String protoName, ImmutableList<NamedExprNode> children) {
    FunctionNode node =
        FunctionNode.newNamed(
            identifier(protoName),
            children.stream()
                .map(namedExprNode -> identifier(namedExprNode.name()))
                .collect(toImmutableList()),
            SourceLocation.UNKNOWN);
    node.setSoyFunction(BuiltinFunction.PROTO_INIT);
    for (NamedExprNode child : children) {
      node.addChild(maybeCopyNode(child.expr()));
    }
    return node;
  }

  public static RecordLiteralNode recordLiteral(NamedExprNode... children) {
    return recordLiteral(ImmutableList.copyOf(children));
  }

  public static RecordLiteralNode recordLiteral(ImmutableList<NamedExprNode> children) {
    RecordLiteralNode node =
        new RecordLiteralNode(
            identifier("record"),
            children.stream()
                .map(namedExprNode -> identifier(namedExprNode.name()))
                .collect(toImmutableList()),
            SourceLocation.UNKNOWN);
    for (NamedExprNode child : children) {
      node.addChild(maybeCopyNode(child.expr()));
    }
    return node;
  }

  public static StringNode stringLiteral(String value) {
    return new StringNode(value, QuoteStyle.SINGLE, SourceLocation.UNKNOWN);
  }

  public static TemplateLiteralNode templateLiteral(String templateName) {
    if (templateName.startsWith(".")) {
      templateName = templateName.substring(1);
    }
    return TemplateLiteralNode.forVarRef(
        new VarRefNode(templateName, SourceLocation.UNKNOWN, null));
  }

  public static VarRefNode varRef(String name) {
    return new VarRefNode("$" + name, SourceLocation.UNKNOWN, null);
  }

  public static VeLiteralNode veLiteral(String name) {
    return new VeLiteralNode(identifier("ve"), identifier(name), SourceLocation.UNKNOWN);
  }

  public static Identifier identifier(String identifier) {
    return Identifier.create(identifier, SourceLocation.UNKNOWN);
  }

  /**
   * Helper functions which produce copies of ExprNodes, for the case that they're already attached
   * to the tree.
   */
  private static ImmutableList<ExprNode> maybeCopyNodes(ImmutableList<ExprNode> nodes) {
    return nodes.stream().map(ExprNodes::maybeCopyNode).collect(toImmutableList());
  }

  private static ExprNode[] maybeCopyNodes(ExprNode... nodes) {
    for (int i = 0; i < nodes.length; i++) {
      nodes[i] = maybeCopyNode(nodes[i]);
    }
    return nodes;
  }

  private static ExprNode maybeCopyNode(ExprNode node) {
    if (node.getParent() != null) {
      return node.copy(new CopyState());
    } else {
      return node;
    }
  }

  /**
   * Helper class which associates a string with an ExprNode. Used in the APIs above for
   * constructing record literal nodes and proto init nodes. In both cases, the name is the field
   * name.
   */
  @AutoValue
  public abstract static class NamedExprNode {
    public abstract String name();

    public abstract ExprNode expr();

    public static NamedExprNode create(String name, ExprNode expr) {
      return new AutoValue_ExprNodes_NamedExprNode(name, expr);
    }
  }

  /** Non-instantiable. */
  private ExprNodes() {}
}
