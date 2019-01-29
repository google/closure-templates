/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.soytree;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.basetree.AbstractNodeVisitor;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.basetree.Node;
import com.google.template.soy.basetree.NodeVisitor;
import com.google.template.soy.basetree.ParentNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.shared.restricted.SoyPureFunction;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.Kind;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared utilities for the 'soytree' package.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class SoyTreeUtils {

  private static final Joiner COMMA_JOINER = Joiner.on(", ");

  private SoyTreeUtils() {}

  /** Returns true if the given {@code node} contains any children of the given types. */
  @SafeVarargs
  public static boolean hasNodesOfType(Node node, final Class<? extends Node>... types) {
    class Visitor implements NodeVisitor<Node, VisitDirective> {
      boolean found;

      @Override
      public VisitDirective exec(Node node) {
        for (Class<?> type : types) {
          if (type.isInstance(node)) {
            found = true;
            return VisitDirective.ABORT;
          }
        }
        return VisitDirective.CONTINUE;
      }
    }
    Visitor v = new Visitor();
    visitAllNodes(node, v);
    return v.found;
  }

  /** Returns true if the given {@code node} contains any children that are HTML nodes. */
  public static boolean hasHtmlNodes(Node node) {
    return hasNodesOfType(
        node,
        HtmlOpenTagNode.class,
        HtmlCloseTagNode.class,
        HtmlCommentNode.class,
        HtmlAttributeNode.class,
        HtmlAttributeValueNode.class);
  }

  /** An enum that allows a {#visitAllNodes} visitor to control how the AST is traversed. */
  public enum VisitDirective {
    /**
     * This means that the childrent of the current node should not be visited, but traversal should
     * continue.
     */
    SKIP_CHILDREN,
    /** This means that the whole visit operation should be abrubptly halted. */
    ABORT,
    /** This means that traversal should continue as normal. */
    CONTINUE;
  }

  /**
   * Runs the visitor on all nodes (including {@link ExprNode expr nodes}) reachable from the given
   * node. The order of visiting is breadth first.
   *
   * <p>If the visitor return {@code false} from {@link NodeVisitor#exec(Node)} we will short
   * circuit visiting.
   */
  public static void visitAllNodes(Node node, NodeVisitor<? super Node, VisitDirective> visitor) {
    ArrayDeque<Node> queue = new ArrayDeque<>();
    queue.add(node);
    Node current;
    while ((current = queue.poll()) != null) {
      switch (visitor.exec(current)) {
        case ABORT:
          return;
        case CONTINUE:
          if (current instanceof ParentNode<?>) {
            queue.addAll(((ParentNode<?>) current).getChildren());
          }
          if (current instanceof ExprHolderNode) {
            queue.addAll(((ExprHolderNode) current).getExprList());
          }
          continue;
        case SKIP_CHILDREN:
          continue;
      }
    }
  }

  /**
   * Retrieves all nodes in a tree that are an instance of a particular class.
   *
   * @param <T> The type of node to retrieve.
   * @param rootSoyNode The parse tree to search.
   * @param classObject The class whose instances to search for, including subclasses.
   * @return The nodes in the order they appear.
   */
  public static <T extends Node> ImmutableList<T> getAllNodesOfType(
      Node rootSoyNode, final Class<T> classObject) {
    return getAllMatchingNodesOfType(rootSoyNode, classObject, Predicates.alwaysTrue());
  }

  /**
   * Retrieves all nodes in a tree that are an instance of a particular class and match the given
   * predicate.
   */
  private static <T extends Node> ImmutableList<T> getAllMatchingNodesOfType(
      Node rootSoyNode, final Class<T> classObject, final Predicate<T> filter) {
    final ImmutableList.Builder<T> matchedNodesBuilder = ImmutableList.builder();
    // optimization to avoid navigating into expr trees if we can't possibly match anything
    final boolean exploreExpressions = ExprNode.class.isAssignableFrom(classObject);
    visitAllNodes(
        rootSoyNode,
        new NodeVisitor<Node, VisitDirective>() {
          @Override
          public VisitDirective exec(Node node) {
            if (classObject.isInstance(node)) {
              T typedNode = classObject.cast(node);
              if (filter.apply(typedNode)) {
                matchedNodesBuilder.add(typedNode);
              }
            }
            if (!exploreExpressions && node instanceof ExprNode) {
              return VisitDirective.SKIP_CHILDREN;
            }
            return VisitDirective.CONTINUE;
          }
        });
    return matchedNodesBuilder.build();
  }

  /**
   * Returns all {@link FunctionNode}s in a tree that are calls of the given {@link SoyFunction}.
   */
  public static ImmutableList<FunctionNode> getAllFunctionInvocations(
      Node rootSoyNode, final SoyFunction functionToMatch) {
    return getAllMatchingNodesOfType(
        rootSoyNode,
        FunctionNode.class,
        function -> functionToMatch.equals(function.getSoyFunction()));
  }

  /**
   * Given a Soy node, returns a {@code StringBuilder} that can be used to pretty print the AST
   * structure.
   *
   * <p>For example, for the following soy source <code><pre>
   * {for i in range(5)}
   *   {if $i % 2 == 0}
   *     foo
   *   {/if}
   * {/for}
   * </pre></code> This method prints the AST string as follow: <code><pre>
   * FOR_NODE
   *   IF_NODE
   *     IF_COND_NODE
   *       PRINT_NODE
   * </pre></code>
   *
   * @param node The root of the AST.
   * @param indent The indentation for each level.
   * @param sb The StringBuilder instance used for recursion.
   * @return The StringBuilder instance.
   */
  public static StringBuilder buildAstString(ParentSoyNode<?> node, int indent, StringBuilder sb) {
    for (SoyNode child : node.getChildren()) {
      sb.append(Strings.repeat("  ", indent)).append(child.getKind()).append('\n');
      if (child instanceof ParentSoyNode) {
        buildAstString((ParentSoyNode<?>) child, indent + 1, sb);
      }
    }
    return sb;
  }

  /** Similar to {@link #buildAstString}, but also print the source string for debug usages. */
  public static StringBuilder buildAstStringWithPreview(
      ParentSoyNode<?> node, int indent, StringBuilder sb) {
    for (SoyNode child : node.getChildren()) {
      sb.append(Strings.repeat("  ", indent))
          .append(child.getKind())
          .append(": ")
          .append(child.toSourceString())
          .append('\n');
      if (child instanceof ParentSoyNode) {
        buildAstString((ParentSoyNode<?>) child, indent + 1, sb);
      }
    }
    return sb;
  }

  // -----------------------------------------------------------------------------------------------
  // Utils for executing an ExprNode visitor on all expressions in a Soy tree.

  /**
   * Given a Soy node and a visitor for expression trees, traverses the subtree of the node and
   * executes the visitor on all expressions held by nodes in the subtree.
   *
   * <p>Only processes expressions in V2 syntax. Ignores all expressions in V1 syntax.
   *
   * @param <R> The ExprNode visitor's return type.
   * @param node The root of the subtree to visit all expressions in.
   * @param exprNodeVisitor The visitor to execute on all expressions.
   */
  public static <R> void execOnAllV2Exprs(
      SoyNode node, final AbstractNodeVisitor<ExprNode, R> exprNodeVisitor) {
    visitAllNodes(
        node,
        new NodeVisitor<Node, VisitDirective>() {
          @Override
          public VisitDirective exec(Node node) {
            if (node instanceof ExprHolderNode) {
              for (ExprRootNode expr : ((ExprHolderNode) node).getExprList()) {
                exprNodeVisitor.exec(expr);
              }
            } else if (node instanceof ExprNode) {
              return VisitDirective.SKIP_CHILDREN;
            }
            return VisitDirective.CONTINUE;
          }
        });
  }

  // -----------------------------------------------------------------------------------------------
  // Utils for cloning.

  /**
   * Clones the given node and then generates and sets new ids on all the cloned nodes (by default,
   * SoyNode.copy(copyState) creates cloned nodes with the same ids as the original nodes).
   *
   * <p>This function will use the original Soy tree's node id generator to generate the new node
   * ids for the cloned nodes. Thus, the original node to be cloned must be part of a full Soy tree.
   * However, this does not mean that the cloned node will become part of the original tree (unless
   * it is manually attached later). The cloned node will be an independent subtree with parent set
   * to null.
   *
   * @param <T> The type of the node being cloned.
   * @param origNode The original node to be cloned. This node must be part of a full Soy tree,
   *     because the generator for the new node ids will be retrieved from the root (SoyFileSetNode)
   *     of the tree.
   * @param nodeIdGen The ID generator used for the tree.
   * @return The cloned node, with all new ids for its subtree.
   */
  public static <T extends SoyNode> T cloneWithNewIds(T origNode, IdGenerator nodeIdGen) {

    // Clone the node.
    @SuppressWarnings("unchecked")
    T clone = (T) origNode.copy(new CopyState());

    // Generate new ids.
    (new GenNewIdsVisitor(nodeIdGen)).exec(clone);

    return clone;
  }

  /**
   * Clones the given list of nodes and then generates and sets new ids on all the cloned nodes (by
   * default, SoyNode.copy(copyState) creates cloned nodes with the same ids as the original nodes).
   *
   * <p>This function will use the original Soy tree's node id generator to generate the new node
   * ids for the cloned nodes. Thus, the original nodes to be cloned must be part of a full Soy
   * tree. However, this does not mean that the cloned nodes will become part of the original tree
   * (unless they are manually attached later). The cloned nodes will be independent subtrees with
   * parents set to null.
   *
   * @param <T> The type of the nodes being cloned.
   * @param origNodes The original nodes to be cloned. These nodes must be part of a full Soy tree,
   *     because the generator for the new node ids will be retrieved from the root (SoyFileSetNode)
   *     of the tree.
   * @param nodeIdGen The ID generator used for the tree.
   * @return The cloned nodes, with all new ids for their subtrees.
   */
  public static <T extends SoyNode> List<T> cloneListWithNewIds(
      List<T> origNodes, IdGenerator nodeIdGen) {

    Preconditions.checkNotNull(origNodes);
    List<T> clones = new ArrayList<>(origNodes.size());
    for (T origNode : origNodes) {
      @SuppressWarnings("unchecked")
      T clone = (T) origNode.copy(new CopyState());
      (new GenNewIdsVisitor(nodeIdGen)).exec(clone);
      clones.add(clone);
    }

    return clones;
  }

  /** Private helper for cloneWithNewIds() to set new ids on a cloned subtree. */
  private static class GenNewIdsVisitor extends AbstractNodeVisitor<SoyNode, Void> {

    /** The generator for new node ids. */
    private IdGenerator nodeIdGen;

    /** @param nodeIdGen The generator for new node ids. */
    public GenNewIdsVisitor(IdGenerator nodeIdGen) {
      this.nodeIdGen = nodeIdGen;
    }

    @Override
    protected void visit(SoyNode node) {
      node.setId(nodeIdGen.genId());
      if (node instanceof ParentSoyNode<?>) {
        visitChildren((ParentSoyNode<?>) node);
      }
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Miscellaneous.

  /** Returns true if {@code node} is a descendant of {@code ancestor}. */
  public static boolean isDescendantOf(SoyNode node, SoyNode ancestor) {
    for (; node != null; node = node.getParent()) {
      if (ancestor == node) {
        return true;
      }
    }
    return false;
  }

  public static String toSourceString(List<? extends Node> nodes) {
    List<String> strings = new ArrayList<String>(nodes.size());
    for (Node node : nodes) {
      strings.add(node.toSourceString());
    }
    return COMMA_JOINER.join(strings);
  }

  /**
   * Return whether the given root node is a constant expression or not. Pure functions are
   * considered constant iff their parameter is a constant expression.
   *
   * @param rootSoyNode the root of the expression tree.
   * @return {@code true} if the expression is constant in evaluation, {@code false} otherwise.
   */
  public static boolean isConstantExpr(ExprNode rootSoyNode) {
    class ConstantNodeVisitor implements NodeVisitor<Node, VisitDirective> {
      boolean isConstant = true;

      @Override
      public VisitDirective exec(Node node) {
        // Note: ExprNodes only contain other ExprNodes, so this down-cast is safe.
        switch (((ExprNode) node).getKind()) {
          case VAR_REF_NODE:
            isConstant = false;
            return VisitDirective.ABORT;
          case FUNCTION_NODE:
            FunctionNode fn = (FunctionNode) node;
            if (fn.getSoyFunction().getClass().isAnnotationPresent(SoyPureFunction.class)) {
              // Continue to evaluate the const-ness of the pure function's parameters.
              return VisitDirective.CONTINUE;
            } else {
              isConstant = false;
              return VisitDirective.ABORT;
            }
          default:
            return VisitDirective.CONTINUE;
        }
      }
    }

    ConstantNodeVisitor visitor = new ConstantNodeVisitor();
    visitAllNodes(rootSoyNode, visitor);
    return visitor.isConstant;
  }

  /**
   * Returns the node as an HTML tag node, if one can be extracted from it (e.g. wrapped in a
   * MsgPlaceholderNode). Otherwise, returns null.
   */
  public static HtmlTagNode getNodeAsHtmlTagNode(SoyNode node, boolean openTag) {
    SoyNode.Kind tagKind =
        openTag ? SoyNode.Kind.HTML_OPEN_TAG_NODE : SoyNode.Kind.HTML_CLOSE_TAG_NODE;
    if (node.getKind() == tagKind) {
      return (HtmlTagNode) node;
    }
    // In a msg tag it will be a placeholder, wrapping a MsgHtmlTagNode wrapping the HtmlTagNode.
    if (node.getKind() == Kind.MSG_PLACEHOLDER_NODE) {
      MsgPlaceholderNode placeholderNode = (MsgPlaceholderNode) node;
      if (placeholderNode.numChildren() == 1
          && placeholderNode.getChild(0).getKind() == Kind.MSG_HTML_TAG_NODE) {
        MsgHtmlTagNode msgHtmlTagNode = (MsgHtmlTagNode) placeholderNode.getChild(0);
        if (msgHtmlTagNode.numChildren() == 1 && msgHtmlTagNode.getChild(0).getKind() == tagKind) {
          return (HtmlTagNode) msgHtmlTagNode.getChild(0);
        }
      }
    }
    return null;
  }
}
