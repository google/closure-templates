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

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SourceLocation.ByteSpan;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.SanitizedContentKind;
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
import com.google.template.soy.exprtree.MapLiteralFromListNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.internal.util.TreeStreams;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.Kind;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.TemplateType;
import com.google.template.soy.types.ast.FunctionTypeNode;
import com.google.template.soy.types.ast.GenericTypeNode;
import com.google.template.soy.types.ast.IndexedTypeNode;
import com.google.template.soy.types.ast.IntersectionTypeNode;
import com.google.template.soy.types.ast.LiteralTypeNode;
import com.google.template.soy.types.ast.NamedTypeNode;
import com.google.template.soy.types.ast.RecordTypeNode;
import com.google.template.soy.types.ast.RecordTypeNode.Property;
import com.google.template.soy.types.ast.TemplateTypeNode;
import com.google.template.soy.types.ast.TypeNode;
import com.google.template.soy.types.ast.TypeNodeVisitor;
import com.google.template.soy.types.ast.TypesHolderNode;
import com.google.template.soy.types.ast.UnionTypeNode;
import com.google.template.soy.types.ast.VarArgsTypeNode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** Shared utilities for the 'soytree' package. */
public final class SoyTreeUtils {

  static final ImmutableSet<SoyNode.Kind> NODES_THAT_DONT_CONTRIBUTE_OUTPUT =
      Sets.immutableEnumSet(
          SoyNode.Kind.LET_CONTENT_NODE,
          SoyNode.Kind.LET_VALUE_NODE,
          SoyNode.Kind.DEBUGGER_NODE,
          SoyNode.Kind.LOG_NODE);

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

  /** Returns the next sibling of {@code node} or {@code null} if none exists. */
  public static SoyNode nextSibling(SoyNode node) {
    return (SoyNode) nextSiblingNode(node);
  }

  @Nullable
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
     * This means that the children of the current node should not be visited, but traversal should
     * continue.
     */
    SKIP_CHILDREN,
    /** This means that traversal should continue as normal. */
    CONTINUE
  }

  private static VisitDirective visitAll(Node n) {
    return VisitDirective.CONTINUE;
  }

  private static VisitDirective visitNonExpr(Node n) {
    return n instanceof ExprNode ? VisitDirective.SKIP_CHILDREN : VisitDirective.CONTINUE;
  }

  /**
   * Runs the visitor on all nodes (including {@link ExprNode expr nodes}) reachable from the given
   * node. The order of visiting is breadth first.
   */
  public static void visitAllNodes(Node node, NodeVisitor<? super Node, VisitDirective> visitor) {
    long unused = allNodes(node, visitor).count();
  }

  /** Returns a stream beginning with {@code root} and ending in root's most distant ancestor. */
  public static Stream<? extends Node> ancestors(Node root) {
    return TreeStreams.ancestor(root, Node::getParent);
  }

  /** Returns a breadth-first stream traversal of the AST tree starting at {@code node}. */
  public static Stream<? extends Node> allNodes(Node node) {
    return allNodes(node, SoyTreeUtils::visitAll);
  }

  /**
   * Returns a breadth-first stream traversal of the AST tree starting at {@code node}. {@code
   * visitor} can return {@link VisitDirective#SKIP_CHILDREN} to skip sections of the tree.
   */
  public static Stream<? extends Node> allNodes(
      Node root, NodeVisitor<? super Node, VisitDirective> visitor) {
    return TreeStreams.breadthFirst(
        root,
        next -> {
          if (visitor.exec(next) == VisitDirective.CONTINUE) {
            if (next instanceof ParentNode<?>) {
              if (next instanceof ExprHolderNode) {
                return Iterables.concat(
                    ((ParentNode<?>) next).getChildren(), ((ExprHolderNode) next).getExprList());
              }
              return ((ParentNode<?>) next).getChildren();
            }
            if (next instanceof ExprHolderNode) {
              return ((ExprHolderNode) next).getExprList();
            }
          }
          return ImmutableList.of();
        });
  }

  public static <T extends Node> Stream<T> allNodesOfType(Node rootSoyNode, Class<T> classObject) {
    // optimization to avoid navigating into expr trees if we can't possibly match anything
    boolean exploreExpressions = !SoyNode.class.isAssignableFrom(classObject);
    return allNodesOfType(
        rootSoyNode,
        classObject,
        exploreExpressions ? SoyTreeUtils::visitAll : SoyTreeUtils::visitNonExpr);
  }

  /**
   * Returns all nodes in the AST tree starting at {@code rootSoyNode} that are an instance of
   * {@code classObject}. {@code visitor} can return {@link VisitDirective#SKIP_CHILDREN} to skip
   * sections of the tree.
   */
  public static <T extends Node> Stream<T> allNodesOfType(
      Node rootSoyNode, Class<T> classObject, NodeVisitor<? super Node, VisitDirective> visitor) {
    return allNodes(rootSoyNode, visitor).filter(classObject::isInstance).map(classObject::cast);
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
      Node rootSoyNode, Class<T> classObject) {
    return allNodesOfType(rootSoyNode, classObject).collect(toImmutableList());
  }

  public static Stream<FunctionNode> allFunctionInvocations(
      Node rootSoyNode, SoyFunction functionToMatch) {
    return allNodesOfType(rootSoyNode, FunctionNode.class)
        .filter(
            function -> function.isResolved() && functionToMatch.equals(function.getSoyFunction()));
  }

  public static void visitExprNodesWithHolder(
      SoyNode root, BiConsumer<ExprHolderNode, ExprNode> visitor) {
    visitExprNodesWithHolder(root, ExprNode.class, visitor);
  }

  public static <T extends ExprNode> void visitExprNodesWithHolder(
      SoyNode root, Class<T> exprType, BiConsumer<ExprHolderNode, T> visitor) {
    SoyTreeUtils.allNodesOfType(root, ExprHolderNode.class)
        .forEach(
            exprHolder ->
                exprHolder.getExprList().stream()
                    .flatMap(rootExpr -> SoyTreeUtils.allNodesOfType(rootExpr, exprType))
                    .forEach(expr -> visitor.accept(exprHolder, expr)));
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
   * @return The String instance.
   */
  public static String buildAstString(ParentSoyNode<?> node) {
    return buildAstStringHelper(node, 0, new StringBuilder()).toString();
  }

  /**
   * Given an expr node, returns a {@code StringBuilder} that can be used to pretty print the AST
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
   * @return The String instance.
   */
  public static String buildAstString(ParentExprNode node) {
    return buildAstStringHelper(node, 0, new StringBuilder()).toString();
  }

  /**
   * @param node The root of the AST.
   * @param indent The indentation for each level.
   * @param sb The StringBuilder instance used for recursion.
   * @return The StringBuilder instance.
   */
  private static StringBuilder buildAstStringHelper(
      ParentNode<?> node, int indent, StringBuilder sb) {
    for (Node child : node.getChildren()) {
      sb.append("  ".repeat(indent)).append(child.getKind()).append('\n');
      if (child instanceof ParentNode) {
        buildAstStringHelper((ParentNode<?>) child, indent + 1, sb);
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
    allNodes(node, SoyTreeUtils::visitNonExpr)
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
    new GenNewIdsVisitor(nodeIdGen).exec(clone);

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
      new GenNewIdsVisitor(nodeIdGen).exec(clone);
      clones.add(clone);
    }

    return clones;
  }

  /** Private helper for cloneWithNewIds() to set new ids on a cloned subtree. */
  private static class GenNewIdsVisitor extends AbstractNodeVisitor<SoyNode, Void> {

    /** The generator for new node ids. */
    private final IdGenerator nodeIdGen;

    /**
     * @param nodeIdGen The generator for new node ids.
     */
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

    /**
     * @param nodeIdGen The generator for new node ids.
     */
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

    @Override
    protected void visitMapLiteralFromListNode(MapLiteralFromListNode node) {
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
        VarRefNode refNode = (VarRefNode) expr;
        VarDefn varDefn = refNode.getDefnDecl();
        if (varDefn != null && (varDefn.kind() == VarDefn.Kind.SYMBOL)) {
          // This method is called while inferring parameter types, before the type has been set on
          // refNode.
          return false;
        }
        if (refNode.hasType()) {
          switch (refNode.getType().getKind()) {
            case TEMPLATE_TYPE:
            case PROTO_TYPE:
            case PROTO_ENUM_TYPE:
              return false;
            default:
              return true;
          }
        }
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
  @Nullable
  public static HtmlTagNode getNodeAsHtmlTagNode(SoyNode node, boolean openTag) {
    if (node == null) {
      return null;
    }
    SoyNode.Kind tagKind =
        openTag ? SoyNode.Kind.HTML_OPEN_TAG_NODE : SoyNode.Kind.HTML_CLOSE_TAG_NODE;
    if (NODES_THAT_DONT_CONTRIBUTE_OUTPUT.contains(node.getKind())) {
      return getNodeAsHtmlTagNode(nextSibling(node), openTag);
    }
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

  private static final TypeNodeVisitor<List<? extends TypeNode>> TRAVERSING =
      new TypeNodeVisitor<>() {
        @Override
        public ImmutableList<? extends TypeNode> visit(NamedTypeNode node) {
          return ImmutableList.of();
        }

        @Override
        public ImmutableList<? extends TypeNode> visit(IndexedTypeNode node) {
          return ImmutableList.of(node.type());
        }

        @Override
        public ImmutableList<? extends TypeNode> visit(GenericTypeNode node) {
          return node.arguments();
        }

        @Override
        public ImmutableList<? extends TypeNode> visit(UnionTypeNode node) {
          return node.candidates();
        }

        @Override
        public ImmutableList<? extends TypeNode> visit(IntersectionTypeNode node) {
          return node.candidates();
        }

        @Override
        public ImmutableList<? extends TypeNode> visit(RecordTypeNode node) {
          return node.properties().stream().map(Property::type).collect(toImmutableList());
        }

        @Override
        public ImmutableList<? extends TypeNode> visit(TemplateTypeNode node) {
          ImmutableList.Builder<TypeNode> types = ImmutableList.builder();
          types.add(node.returnType());
          node.parameters().forEach(p -> types.add(p.type()));
          return types.build();
        }

        @Override
        public ImmutableList<? extends TypeNode> visit(FunctionTypeNode node) {
          ImmutableList.Builder<TypeNode> types = ImmutableList.builder();
          types.add(node.returnType());
          node.parameters().forEach(p -> types.add(p.type()));
          return types.build();
        }

        @Override
        public List<? extends TypeNode> visit(LiteralTypeNode node) {
          return ImmutableList.of();
        }

        @Override
        public ImmutableList<? extends TypeNode> visit(VarArgsTypeNode node) {
          return ImmutableList.of(node.baseType());
        }
      };

  /** Returns a breadth-first stream starting at root and containing all nested type nodes. */
  public static Stream<? extends TypeNode> allTypeNodes(TypeNode root) {
    return TreeStreams.breadthFirst(root, TRAVERSING::exec);
  }

  /** Returns a stream of all the type nodes contained in a Soy node branch. */
  public static Stream<TypeNode> allTypeNodes(SoyNode root) {
    return allNodesOfType(root, TypesHolderNode.class).flatMap(TypesHolderNode::getTypeNodes);
  }

  public static ExprNodeToHolderIndex buildExprNodeToHolderIndex(SoyNode root) {
    return new ExprNodeToHolderIndex(root);
  }

  /** Index mapping an expression node to the soy node that holds it. */
  public static class ExprNodeToHolderIndex {

    private final ImmutableMap<ExprRootNode, ExprHolderNode> index;

    ExprNodeToHolderIndex(SoyNode root) {
      ImmutableMap.Builder<ExprRootNode, ExprHolderNode> index = ImmutableMap.builder();
      allNodesOfType(root, ExprHolderNode.class)
          .forEach(
              holder -> {
                for (ExprRootNode exprRoot : holder.getExprList()) {
                  index.put(exprRoot, holder);
                }
              });
      this.index = index.buildOrThrow();
    }

    ExprHolderNode getHolder(ExprRootNode root) {
      return Preconditions.checkNotNull(index.get(root));
    }

    public ExprHolderNode getHolder(ExprNode node) {
      while (node.getParent() != null) {
        node = node.getParent();
      }
      return getHolder((ExprRootNode) node);
    }
  }

  public static ByteSpan getByteSpan(SoyNode node) {
    return getByteSpan(node, node.getSourceLocation());
  }

  public static ByteSpan getByteSpan(SoyNode node, SourceLocation location) {
    if (!location.isKnown()) {
      return ByteSpan.UNKNOWN;
    }
    return node.getNearestAncestor(SoyFileNode.class).getByteOffsetIndex().getByteSpan(location);
  }

  public static SourceLocation getOriginalSourceLocation(SoyNode node, SourceLocation location) {
    if (!location.isKnown()) {
      return location;
    }
    return node.getNearestAncestor(SoyFileNode.class).getSourceMap().map(location);
  }

  @Nullable
  public static SanitizedContentKind inferSanitizedContentKindFromChildren(
      ParentSoyNode<? extends SoyNode> node) {
    SanitizedContentKind kind = null;
    for (SoyNode child : getChildrenNoControlFlows(node)) {
      if (child instanceof LetContentNode || child instanceof LetValueNode) {
        continue;
      }
      if (!(child instanceof CallBasicNode)) {
        return null;
      }
      CallBasicNode callNode = (CallBasicNode) child;
      SoyType type = callNode.getCalleeExpr().getType();
      if (type instanceof TemplateType) {
        SanitizedContentKind childKind =
            ((TemplateType) type).getContentKind().getSanitizedContentKind();
        if (kind == null) {
          kind = childKind;
        } else if (kind != childKind) {
          return null;
        }
      }
    }
    return kind;
  }

  private static ImmutableList<SoyNode> getChildrenNoControlFlows(
      ParentSoyNode<? extends SoyNode> node) {
    ImmutableList.Builder<SoyNode> nodes = ImmutableList.builder();
    for (SoyNode child : node.getChildren()) {
      if ((child instanceof IfNode)
          || (child instanceof SwitchNode)
          || (child instanceof ForNode)) {
        // These all have an intermediate node between the content: IfCondNode, SwitchCaseNode,
        // ForNonEmptyNode &c.
        for (SoyNode condNode : ((ParentSoyNode<? extends SoyNode>) child).getChildren()) {
          nodes.addAll(getChildrenNoControlFlows((ParentSoyNode<? extends SoyNode>) condNode));
        }
      } else {
        nodes.add(child);
      }
    }
    return nodes.build();
  }
}
