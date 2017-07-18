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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.basetree.AbstractNodeVisitor;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.basetree.Node;
import com.google.template.soy.basetree.NodeVisitor;
import com.google.template.soy.basetree.ParentNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.LocalVarNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

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
    class Visitor implements NodeVisitor<Node, Boolean> {
      boolean found;

      @Override
      public Boolean exec(Node node) {
        for (Class type : types) {
          if (type.isInstance(node)) {
            found = true;
            return false; // short circuit
          }
        }
        return true; // keep going
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

  /**
   * Runs the visitor on all nodes (including {@link ExprNode expr nodes}) reachable from the given
   * node. The order of visiting is undefined.
   *
   * <p>If the visitor return {@code false} from {@link NodeVisitor#exec(Node)} we will short
   * circuit visiting.
   */
  public static void visitAllNodes(Node node, NodeVisitor<? super Node, Boolean> visitor) {
    Deque<Node> queue = new ArrayDeque<>();
    queue.add(node);
    Node current;
    while ((current = queue.pollLast()) != null) {
      if (!visitor.exec(current)) {
        return;
      }
      if (current instanceof ParentNode<?>) {
        queue.addAll(((ParentNode<?>) current).getChildren());
      }
      if (current instanceof ExprHolderNode) {
        for (ExprRootNode union : ((ExprHolderNode) current).getExprList()) {
          queue.add(union);
        }
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
  public static <T extends Node> List<T> getAllNodesOfType(
      SoyNode rootSoyNode, final Class<T> classObject) {
    return getAllNodesOfType(rootSoyNode, classObject, true);
  }

  /**
   * Retrieves all nodes in a tree that are an instance of a particular class.
   *
   * @param <T> The type of node to retrieve.
   * @param rootSoyNode The parse tree to search.
   * @param classObject The class whose instances to search for, including subclasses.
   * @param doSearchSubtreesOfMatchedNodes Whether to keep searching subtrees of matched nodes for
   *     more nodes of the given type.
   * @return The nodes in the order they appear.
   */
  public static <T extends Node> List<T> getAllNodesOfType(
      SoyNode rootSoyNode,
      final Class<T> classObject,
      final boolean doSearchSubtreesOfMatchedNodes) {

    final ImmutableList.Builder<T> matchedNodesBuilder = ImmutableList.builder();

    final boolean exploreExpressions = ExprNode.class.isAssignableFrom(classObject);
    final AbstractNodeVisitor<ExprNode, Void> exprVisitor =
        exploreExpressions
            ? new AbstractNodeVisitor<ExprNode, Void>() {
              @Override
              protected void visit(ExprNode exprNode) {
                if (classObject.isInstance(exprNode)) {
                  matchedNodesBuilder.add(classObject.cast(exprNode));
                  if (!doSearchSubtreesOfMatchedNodes) {
                    return;
                  }
                }
                if (exprNode instanceof ParentExprNode) {
                  visitChildren((ParentExprNode) exprNode);
                }
              }
            }
            : null;

    AbstractNodeVisitor<SoyNode, Void> visitor =
        new AbstractNodeVisitor<SoyNode, Void>() {
          @Override
          protected void visit(SoyNode soyNode) {
            if (classObject.isInstance(soyNode)) {
              matchedNodesBuilder.add(classObject.cast(soyNode));
              if (!doSearchSubtreesOfMatchedNodes) {
                return;
              }
            }
            if (soyNode instanceof ParentSoyNode<?>) {
              visitChildren((ParentSoyNode<?>) soyNode);
            }
            if (exploreExpressions && soyNode instanceof ExprHolderNode) {
              for (ExprRootNode expr : ((ExprHolderNode) soyNode).getExprList()) {
                exprVisitor.exec(expr);
              }
            }
          }
        };

    visitor.exec(rootSoyNode);
    return matchedNodesBuilder.build();
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

  /** Similar to {@link buildAstString}, but also print the source string for debug usages. */
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
      SoyNode node, AbstractNodeVisitor<ExprNode, R> exprNodeVisitor) {
    new VisitAllV2ExprsVisitor<R>(exprNodeVisitor).exec(node);
  }

  /**
   * Private helper class for {@code visitAllExprs} and {@code visitAllExprsShortcircuitably}.
   *
   * @param <R> The ExprNode visitor's return type.
   */
  private static final class VisitAllV2ExprsVisitor<R> extends AbstractNodeVisitor<SoyNode, R> {

    private final AbstractNodeVisitor<ExprNode, R> exprNodeVisitor;

    private VisitAllV2ExprsVisitor(AbstractNodeVisitor<ExprNode, R> exprNodeVisitor) {
      this.exprNodeVisitor = exprNodeVisitor;
    }

    @Override
    protected void visit(SoyNode node) {

      if (node instanceof ParentSoyNode<?>) {
        visitChildren((ParentSoyNode<?>) node);
      }

      if (node instanceof ExprHolderNode) {
        for (ExprRootNode expr : ((ExprHolderNode) node).getExprList()) {
          exprNodeVisitor.exec(expr);
        }
      }
    }
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
    T clone = cloneNode(origNode);

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
      T clone = cloneNode(origNode);
      (new GenNewIdsVisitor(nodeIdGen)).exec(clone);
      clones.add(clone);
    }

    return clones;
  }

  /**
   * Clones a SoyNode but unlike SoyNode.copy(copyState) keeps {@link VarRefNode#getDefnDecl()}
   * pointing at the correct tree.
   */
  public static <T extends SoyNode> T cloneNode(T original) {
    @SuppressWarnings("unchecked") // this holds for all SoyNode types
    // TODO(lukes): eliminate this method once all logic has been moved into copy state
    T cloned = (T) original.copy(new CopyState());

    // TODO(lukes):  this is not efficient but it is the only way to work around the limitations
    // of the Object.copy(copyState) interface.  A better solution would be to introduce our own
    // clone method which could take a parameter.  For nodes in the AST object graph that are the
    // back edges in cycles (e.g. LocalVar) we could maintain an identity map which could be used to
    // efficiently reconstruct the cycles.  For now we just fix it up after the fact.

    // All vardefns in varrefs have been invalidated.  Currently we only reassign vardefns for
    // LocalVarNodes because those have links (via declaringNode()) back up the tree, so we need to
    // make sure that the declaringNode() links are correctly defined to point at the new tree
    // instead of the previous one.
    List<LocalVarNode> originalLocalVarNodes = getAllNodesOfType(original, LocalVarNode.class);
    List<LocalVarNode> newLocalVarNodes = getAllNodesOfType(cloned, LocalVarNode.class);
    Map<VarDefn, VarDefn> replacementMap = new IdentityHashMap<>();
    for (int i = 0; i < newLocalVarNodes.size(); i++) {
      VarDefn oldDefn = originalLocalVarNodes.get(i).getVar();
      VarDefn newDefn = newLocalVarNodes.get(i).getVar();
      checkState(oldDefn.name().equals(newDefn.name())); // sanity check
      replacementMap.put(oldDefn, newDefn);
    }
    // limiting this to just local vars would make sense.
    for (VarRefNode varRef : getAllNodesOfType(cloned, VarRefNode.class)) {
      VarDefn replacement = replacementMap.get(varRef.getDefnDecl());
      if (replacement != null) {
        varRef.setDefn(replacement);
      }
    }
    return cloned;
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
}
