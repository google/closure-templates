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

import com.google.template.soy.basetree.Node;
import com.google.template.soy.basetree.ParentNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;

import java.util.List;


/**
 * This class defines the base interface for a node in the parse tree, as well as a number of
 * subinterfaces that extend the base interface in various aspects. Every concrete node implements
 * some subset of these interfaces.
 *
 * The top level definition is the base node interface.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public interface SoyNode extends Node {

  /**
   * Enum for the syntax version.
   */
  public static enum SyntaxVersion { V1, V2 }

  /**
   * Gets this node's id.
   * @return This node's id.
   */
  public String getId();

  /**
   * Gets the syntax version of this node.
   * @return The syntax version of this node.
   */
  public SyntaxVersion getSyntaxVersion();

  @Override public ParentSoyNode<? extends SoyNode> getParent();


  // -----------------------------------------------------------------------------------------------


  /**
   * A node in a Soy parse tree that may be a parent.
   */
  public static interface ParentSoyNode<N extends SoyNode> extends SoyNode, ParentNode<N> {}


  // -----------------------------------------------------------------------------------------------


  /**
   * A node that represents the top of a split-level structure in the parse tree. This indicates
   * there are special structural requirements on its immediate children (e.g. IfNode may only
   * have IfCondNode and IfElseNode as children).
   *
   * <p> Includes nodes such as SoyFileSetNode, SoyFileNode, IfNode, SwitchNode, ForeachNode,
   * CallNode, etc.
   *
   * <p> During optimization, the immediate children should never be moved, but lower descendents
   * may be freely moved (either moved within the node's subtree or moved outside of the node's
   * subtree).
   */
  public static interface SplitLevelTopNode<N extends SoyNode> extends ParentSoyNode<N> {}


  // -----------------------------------------------------------------------------------------------


  /**
   * A node that represents a specific Soy command.
   */
  public static interface SoyCommandNode extends SoyNode {

    /**
     * Gets the Soy command name.
     * @return The Soy command name.
     */
    public String getCommandName();

    /**
     * Gets the command text (may be the empty string).
     * @return The command text (may be the empty string).
     */
    public String getCommandText();

    /**
     * Builds a Soy tag string that could be the Soy tag for this node. Note that this may not
     * necessarily be the actual original Soy tag, but a (sort of) canonical equivalent.
     * @return A Soy tag string that could be the Soy tag for this node.
     */
    public String getTagString();
  }


  // -----------------------------------------------------------------------------------------------


  /**
   * A node that represents a specific Soy statement.
   */
  public static interface SoyStatementNode extends SoyNode {}


  // -----------------------------------------------------------------------------------------------


  /**
   * A node that represents a block of Soy code that is conditionally executed. During optimization,
   * descendents should generally never be moved outside of the subtree of such a node. We make an
   * exception for LoopNodes because we don't want to lose the ability to pull invariants out of
   * loops.
   *
   * <p> Includes nodes such as IfCondNode, IfElseNode, SwitchCaseNode, SwitchDefaultNode,
   * ForeachNonemptyNode, ForeachIfemptyNode, ForNode etc.
   */
  public static interface ConditionalBlockNode<N extends SoyNode> extends ParentSoyNode<N> {}


  // -----------------------------------------------------------------------------------------------


  /**
   * A node that represents a block of code that is executed in a loop.
   *
   * <p> Includes nodes such as ForeachNonemptyNode and ForNode.
   */
  public static interface LoopNode<N extends SoyNode> extends ParentSoyNode<N> {}


  // -----------------------------------------------------------------------------------------------


  /**
   * A node that adds a new local variable. The scope of the new local variable comprises either
   * the children of this node or the younger siblings of this node.
   */
  public static interface LocalVarNode extends SoyNode {

    /**
     * Gets the name of this node's local variable (without the preceding '$').
     * @return The name of this node's local variable (without the preceding '$').
     */
    public String getLocalVarName();
  }


  // -----------------------------------------------------------------------------------------------


  /**
   * A node that adds a new local variable whose scope comprises the children of this code.
   */
  public static interface LocalVarBlockNode<N extends SoyNode>
      extends LocalVarNode, ParentSoyNode<N> {}


  // -----------------------------------------------------------------------------------------------


  /**
   * A node that adds a new local variable whose scope comprises the younger siblings of this node.
   */
  public static interface LocalVarInlineNode extends LocalVarNode {}


  // -----------------------------------------------------------------------------------------------


  /**
   * A node that holds some expressions in its fields/properties.
   */
  public static interface ExprHolderNode extends SoyNode {

    /**
     * Gets the list of expressions in this node.
     * @return The list of expressions in this node.
     */
    public List<? extends ExprRootNode<? extends ExprNode>> getAllExprs();
  }


  // -----------------------------------------------------------------------------------------------


  /**
   * A node that may be a parent and that holds some expressions in its fields/properties.
   */
  public static interface ParentExprHolderNode<N extends SoyNode>
      extends ExprHolderNode, ParentSoyNode<N> {}


  // -----------------------------------------------------------------------------------------------


  /**
   * A node that is the direct child of a MsgNode and will turn into a placeholder. Every direct
   * child of a MsgNode must either be a RawTextNode or a MsgPlaceholderNode.
   */
  public static interface MsgPlaceholderNode extends SoyNode {

    /**
     * Generates the base placeholder name for this node.
     * @return The base placeholder name for this node.
     */
    public String genBasePlaceholderName();

    /**
     * Determines whether this node and the given other node are the same, such that they should be
     * represented by the same placeholder.
     * @param other The other MsgPlaceholderNode to compare to.
     * @return True if this and the other node should be represented by the same placeholder.
     */
    public boolean isSamePlaceholderAs(MsgPlaceholderNode other);
  }

}
