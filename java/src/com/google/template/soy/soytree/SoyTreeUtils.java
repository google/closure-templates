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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.template.soy.error.ErrorReporter.exploding;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.basetree.AbstractNodeVisitor;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.basetree.Node;
import com.google.template.soy.basetree.NodeVisitor;
import com.google.template.soy.basetree.ParentNode;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.ListComprehensionNode;
import com.google.template.soy.plugin.restricted.SoySourceFunction;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.Kind;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.types.BoolType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators.AbstractSpliterator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

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
  public static boolean hasNodesOfType(Node node, Class<? extends Node>... types) {
    return allNodes(node)
        .anyMatch(
            n -> {
              for (Class<?> type : types) {
                if (type.isInstance(n)) {
                  return true;
                }
              }
              return false;
            });
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

  /** Returns the next sibling of {@code node} or {@code null} if none exists. */
  public static SoyNode nextSibling(SoyNode node) {
    return (SoyNode) nextSiblingNode(node);
  }

  public static Node nextSiblingNode(Node node) {
    ParentNode<?> parent = node.getParent();
    if (parent == null) {
      return null;
    }
    int index = parent.getChildIndex(node);
    Preconditions.checkState(index >= 0);
    int nextIndex = index + 1;
    return parent.numChildren() > nextIndex ? parent.getChild(nextIndex) : null;
  }

  /** An enum that allows a {#visitAllNodes} visitor to control how the AST is traversed. */
  public enum VisitDirective {
    /**
     * This means that the childrent of the current node should not be visited, but traversal should
     * continue.
     */
    SKIP_CHILDREN,
    /** This means that traversal should continue as normal. */
    CONTINUE;
  }

  private static VisitDirective visitAll(Node n) {
    return VisitDirective.CONTINUE;
  }

  /**
   * Runs the visitor on all nodes (including {@link ExprNode expr nodes}) reachable from the given
   * node. The order of visiting is breadth first.
   */
  public static void visitAllNodes(Node node, NodeVisitor<? super Node, VisitDirective> visitor) {
    long unused = allNodes(node, visitor).count();
  }

  /** Returns a breadth-first stream traversal of the AST tree starting at {@code node}. */
  public static Stream<? extends Node> allNodes(Node node) {
    return allNodes(node, SoyTreeUtils::visitAll);
  }

  public static <T extends Node> Stream<T> allNodesOfType(Node rootSoyNode, Class<T> classObject) {
    // optimization to avoid navigating into expr trees if we can't possibly match anything
    boolean exploreExpressions = ExprNode.class.isAssignableFrom(classObject);
    return allNodes(
            rootSoyNode,
            exploreExpressions
                ? SoyTreeUtils::visitAll
                : n ->
                    n instanceof ExprNode ? VisitDirective.SKIP_CHILDREN : VisitDirective.CONTINUE)
        .filter(classObject::isInstance)
        .map(classObject::cast);
  }

  /**
   * Returns a breadth-first stream traversal of the AST tree starting at {@code node}. {@code
   * visitor} can return {@link VisitDirective#SKIP_CHILDREN} to skip sections of the tree.
   */
  public static Stream<? extends Node> allNodes(
      Node root, NodeVisitor<? super Node, VisitDirective> visitor) {
    Deque<Node> generations = new ArrayDeque<>();
    generations.add(root);
    return StreamSupport.stream(
        new AbstractSpliterator<Node>(
            // Our Baseclass says to pass MAX_VALUE for unsized streams
            Long.MAX_VALUE,
            // The order is meaningfull and every item returned is unique.
            Spliterator.ORDERED | Spliterator.DISTINCT) {
          @Override
          public boolean tryAdvance(Consumer<? super Node> action) {
            Node next = generations.poll();
            if (next == null) {
              return false;
            }
            switch (visitor.exec(next)) {
              case SKIP_CHILDREN:
                break;
              case CONTINUE:
                if (next instanceof ParentNode<?>) {
                  generations.addAll(((ParentNode<?>) next).getChildren());
                }
                if (next instanceof ExprHolderNode) {
                  generations.addAll(((ExprHolderNode) next).getExprList());
                }
                break;
            }
            action.accept(next);
            return true;
          }
        },
        /* parallel= */ false);
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
    return getAllMatchingNodesOfType(rootSoyNode, classObject, arg -> true);
  }

  /**
   * Retrieves all nodes in a tree that are an instance of a particular class and match the given
   * predicate.
   */
  public static <T extends Node> ImmutableList<T> getAllMatchingNodesOfType(
      Node rootSoyNode, Class<T> classObject, Predicate<T> filter) {
    return allNodesOfType(rootSoyNode, classObject).filter(filter).collect(toImmutableList());
  }

  /**
   * Returns all {@link FunctionNode}s in a tree that are calls of the given {@link SoyFunction}.
   */
  public static ImmutableList<FunctionNode> getAllFunctionInvocations(
      Node rootSoyNode, SoyFunction functionToMatch) {
    return getAllMatchingNodesOfType(
        rootSoyNode,
        FunctionNode.class,
        function -> function.isResolved() && functionToMatch.equals(function.getSoyFunction()));
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

  public static FunctionNode buildNotNull(ExprNode node, SoySourceFunction isNonNullFn) {
    SourceLocation unknown = node.getSourceLocation().clearRange();
    FunctionNode isNonnull =
        FunctionNode.newPositional(Identifier.create("isNonnull", unknown), isNonNullFn, unknown);
    isNonnull.addChild(new ExprRootNode(node.copy(new CopyState())));
    isNonnull.setType(BoolType.getInstance());
    return isNonnull;
  }

  public static IfNode buildPrintIfNotNull(
      ExprNode node, Supplier<Integer> id, SoySourceFunction isNonnull) {
    SourceLocation unknown = node.getSourceLocation().clearRange();
    IfNode ifNode = new IfNode(id.get(), unknown);
    IfCondNode ifCondNode =
        new IfCondNode(id.get(), unknown, unknown, "if", buildNotNull(node, isNonnull));
    ifNode.addChild(ifCondNode);
    PrintNode printNode =
        new PrintNode(id.get(), unknown, true, node, ImmutableList.of(), exploding());
    ifCondNode.addChild(printNode);
    return ifNode;
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

  /**
   * Similar to {@link #buildAstString}, but for ExprNodes and also prints the source string for
   * debug usages.
   */
  public static StringBuilder buildAstStringWithPreview(
      ParentExprNode node, int indent, StringBuilder sb) {
    for (ExprNode child : node.getChildren()) {
      sb.append(Strings.repeat("  ", indent))
          .append(child.getKind())
          .append(": ")
          .append(child.toSourceString())
          .append('\n');
      if (child instanceof ParentExprNode) {
        buildAstStringWithPreview((ParentExprNode) child, indent + 1, sb);
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
    allNodes(
            node,
            n -> n instanceof ExprNode ? VisitDirective.SKIP_CHILDREN : VisitDirective.CONTINUE)
        .filter(n -> n instanceof ExprHolderNode)
        .map(ExprHolderNode.class::cast)
        .flatMap(n -> n.getExprList().stream())
        .forEach(exprNodeVisitor::exec);
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
      if (node instanceof ExprHolderNode) {
        ExprHolderNode exprHolderNode = (ExprHolderNode) node;
        for (ExprRootNode expr : exprHolderNode.getExprList()) {
          new GenNewIdsExprVisitor(nodeIdGen).exec(expr);
        }
      }

      if (node instanceof ParentSoyNode<?>) {
        visitChildren((ParentSoyNode<?>) node);
      }
    }
  }

  private static class GenNewIdsExprVisitor extends AbstractExprNodeVisitor<Void> {
    /** The generator for new node ids. */
    private final IdGenerator nodeIdGen;

    /** @param nodeIdGen The generator for new node ids. */
    public GenNewIdsExprVisitor(IdGenerator nodeIdGen) {
      this.nodeIdGen = nodeIdGen;
    }

    @Override
    protected void visitExprRootNode(ExprRootNode node) {
      visitChildren(node);
    }

    @Override
    protected void visitExprNode(ExprNode node) {
      if (node instanceof ParentExprNode) {
        visitChildren((ParentExprNode) node);
      }
    }

    @Override
    protected void visitListComprehensionNode(ListComprehensionNode node) {
      node.setNodeId(nodeIdGen.genId());
      visitChildren(node);
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Miscellaneous.

  /** Returns true if {@code node} is a descendant of {@code ancestor}. */
  public static boolean isDescendantOf(Node node, Node ancestor) {
    if (node instanceof ExprNode) {
      ExprNode nodeAsExpr = (ExprNode) node;
      if (ancestor instanceof ExprNode) {
        return isDescendantOf(nodeAsExpr, (ExprNode) ancestor);
      }
      return isDescendantOf(nodeAsExpr, (SoyNode) ancestor);
    }
    if (ancestor instanceof ExprNode) {
      return false;
    }
    return isDescendantOf((SoyNode) node, (SoyNode) ancestor);
  }

  /** Returns true if {@code node} is a descendant of {@code ancestor}. */
  public static boolean isDescendantOf(SoyNode node, SoyNode ancestor) {
    return doIsDescendantOf(node, ancestor);
  }

  /** Returns true if {@code node} is a descendant of {@code ancestor}. */
  public static boolean isDescendantOf(ExprNode node, ExprNode ancestor) {
    return doIsDescendantOf(node, ancestor);
  }

  /** Returns true if {@code node} is a descendant of {@code ancestor}. */
  public static boolean isDescendantOf(ExprNode node, SoyNode ancestor) {
    // first find the root of the expression tree
    while (node.getParent() != null) {
      node = node.getParent();
    }
    Node nodeRoot = node;
    // compare against all reachable expr roots.
    return allNodesOfType(ancestor, ExprHolderNode.class)
        .flatMap(holder -> holder.getExprList().stream())
        .anyMatch(root -> root == nodeRoot);
  }

  private static boolean doIsDescendantOf(Node node, Node ancestor) {
    for (; node != null; node = node.getParent()) {
      if (ancestor == node) {
        return true;
      }
    }
    return false;
  }

  public static String toSourceString(List<? extends Node> nodes) {
    List<String> strings = new ArrayList<>(nodes.size());
    for (Node node : nodes) {
      strings.add(node.toSourceString());
    }
    return COMMA_JOINER.join(strings);
  }

  private static boolean isNonConstant(ExprNode expr) {
    switch (expr.getKind()) {
      case VAR_REF_NODE:
        return true;
      case FUNCTION_NODE:
        return !((FunctionNode) expr).isPure();
      default:
        return false;
    }
  }

  /**
   * Return whether the given root node is a constant expression or not. Pure functions are
   * considered constant iff their parameters are all constant expressions.
   *
   * @param rootSoyNode the root of the expression tree.
   * @return {@code true} if the expression is constant in evaluation, {@code false} otherwise.
   */
  public static boolean isConstantExpr(ExprNode rootSoyNode) {
    // Note: ExprNodes only contain other ExprNodes, so this down-cast is safe.
    return allNodes(rootSoyNode).map(ExprNode.class::cast).noneMatch(SoyTreeUtils::isNonConstant);
  }

  /**
   * Returns a list of all the subexpressions that are non-constant. Pure functions are considered
   * constant iff their parameters are all constant expressions.
   *
   * @param rootSoyNode the root of the expression tree.
   */
  public static ImmutableList<ExprNode> getNonConstantChildren(ExprNode rootSoyNode) {
    return allNodes(rootSoyNode)
        .map(ExprNode.class::cast)
        .filter(SoyTreeUtils::isNonConstant)
        .collect(toImmutableList());
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
