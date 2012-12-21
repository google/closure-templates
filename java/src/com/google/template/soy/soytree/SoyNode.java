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

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.Node;
import com.google.template.soy.basetree.ParentNode;
import com.google.template.soy.data.SanitizedContent.ContentKind;

import java.util.List;

import javax.annotation.Nullable;


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
   * Enum of specific node kinds (coresponding to specific node types).
   * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
   */
  public static enum Kind {

    SOY_FILE_SET_NODE,
    SOY_FILE_NODE,

    TEMPLATE_BASIC_NODE,
    TEMPLATE_DELEGATE_NODE,

    RAW_TEXT_NODE,

    GOOG_MSG_NODE,
    GOOG_MSG_REF_NODE,

    MSG_NODE,
    MSG_PLURAL_NODE,
    MSG_PLURAL_CASE_NODE,
    MSG_PLURAL_DEFAULT_NODE,
    MSG_PLURAL_REMAINDER_NODE,
    MSG_SELECT_NODE,
    MSG_SELECT_CASE_NODE,
    MSG_SELECT_DEFAULT_NODE,
    MSG_PLACEHOLDER_NODE,
    MSG_HTML_TAG_NODE,

    PRINT_NODE,
    PRINT_DIRECTIVE_NODE,

    CSS_NODE,

    LET_VALUE_NODE,
    LET_CONTENT_NODE,

    IF_NODE,
    IF_COND_NODE,
    IF_ELSE_NODE,

    SWITCH_NODE,
    SWITCH_CASE_NODE,
    SWITCH_DEFAULT_NODE,

    FOREACH_NODE,
    FOREACH_NONEMPTY_NODE,
    FOREACH_IFEMPTY_NODE,

    FOR_NODE,

    CALL_BASIC_NODE,
    CALL_DELEGATE_NODE,
    CALL_PARAM_VALUE_NODE,
    CALL_PARAM_CONTENT_NODE,

    LOG_NODE,
    DEBUGGER_NODE,
  }


  /**
   * Enum for the syntax version.
   * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
   */
  public static enum SyntaxVersion { V1, V2 }


  /**
   * Returns this node's kind (corresponding to this node's specific type).
   */
  public Kind getKind();

  /**
   * Sets this node's id.
   * <p> Important: The id should already be set during construction, so this method should only be
   * used during cloning.
   * @param id The new id for this node.
   */
  public void setId(int id);

  /**
   * Returns this node's id.
   */
  public int getId();

  /**
   * Sets the source location (file path and line number) for this node.
   * @param srcLoc The source location for this node.
   */
  public void setSourceLocation(SourceLocation srcLoc);

  /**
   * Returns the source location (file path and line number) for this node.
   */
  public SourceLocation getSourceLocation();

  /**
   * Returns the syntax version of this node.
   */
  public SyntaxVersion getSyntaxVersion();

  @Override public ParentSoyNode<?> getParent();

  /**
   * {@inheritDoc}
   * <p> The cloned nodes will have the same ids as the original nodes. If you need to clone a
   * subtree with new ids assigned to the cloned nodes, use {@link SoytreeUtils#cloneWithNewIds}.
   */
  @Override public SoyNode clone();


  // -----------------------------------------------------------------------------------------------


  /**
   * A node in a Soy parse tree that may be a parent.
   */
  public static interface ParentSoyNode<N extends SoyNode> extends SoyNode, ParentNode<N> {

    /**
     * Sets whether this node needs an env frame when the template is being interpreted.
     * @param needsEnvFrameDuringInterp Whether this node needs an env frame during interpretation,
     *     or null if unknown.
     */
    public void setNeedsEnvFrameDuringInterp(Boolean needsEnvFrameDuringInterp);

    /**
     * Returns whether this node needs an env frame during interpretation, or null if unknown.
     */
    public Boolean needsEnvFrameDuringInterp();
  }


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
   * A node that can legally appear as the direct child of some block node (doesn't necessarily have
   * to be legal as the direct child of a template). To put it another way, a node that can legally
   * appear as the sibling of a RawTextNode or PrintNode.
   */
  public static interface StandaloneNode extends SoyNode {

    @Override public BlockNode getParent();
  }


  // -----------------------------------------------------------------------------------------------


  /**
   * A node that represents a template block.
   */
  public static interface BlockNode extends ParentSoyNode<StandaloneNode> {}


  // -----------------------------------------------------------------------------------------------


  /**
   * A node that represents a specific Soy command.
   */
  public static interface CommandNode extends SoyNode {

    /**
     * Returns the Soy command name.
     */
    public String getCommandName();

    /**
     * Returns the command text (may be the empty string).
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
   * A node that represents a Soy command that encloses a template block.
   */
  public static interface BlockCommandNode extends CommandNode, BlockNode {}


  // -----------------------------------------------------------------------------------------------


  /**
   * A node that represents an independent unit of rendering.
   */
  public static interface RenderUnitNode extends BlockCommandNode {

    /**
     * Returns the content kind for strict autoescape, or null if not specified or not applicable.
     */
    @Nullable public ContentKind getContentKind();
  }


  // -----------------------------------------------------------------------------------------------


  /**
   * A node that represents a specific Soy statement.
   */
  public static interface StatementNode extends StandaloneNode {}


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
  public static interface ConditionalBlockNode extends BlockNode {}


  // -----------------------------------------------------------------------------------------------


  /**
   * A node that represents a block of code that is executed in a loop.
   *
   * <p> Includes nodes such as ForeachNonemptyNode and ForNode.
   */
  public static interface LoopNode extends BlockNode {}


  // -----------------------------------------------------------------------------------------------


  /**
   * A node that adds a new local variable. The scope of the new local variable comprises either
   * the children of this node or the younger siblings of this node.
   */
  public static interface LocalVarNode extends SoyNode {

    /**
     * Returns the name of this node's local variable (without the preceding '$').
     */
    public String getVarName();
  }


  // -----------------------------------------------------------------------------------------------


  /**
   * A node that adds a new local variable whose scope comprises the children of this code.
   */
  public static interface LocalVarBlockNode extends LocalVarNode, BlockNode {}


  // -----------------------------------------------------------------------------------------------


  /**
   * A node that adds a new local variable whose scope comprises the younger siblings of this node.
   */
  public static interface LocalVarInlineNode extends LocalVarNode, StandaloneNode {}


  // -----------------------------------------------------------------------------------------------


  /**
   * A node that holds some expressions in its fields/properties.
   */
  public static interface ExprHolderNode extends SoyNode {

    /**
     * Returns the list of expressions in this node.
     */
    public List<ExprUnion> getAllExprUnions();
  }


  // -----------------------------------------------------------------------------------------------


  /**
   * A block node that can hold message content. Every direct child of a MsgBlockNode must be one
   * of: RawTextNode, MsgPlaceholderNode, MsgSelectNode, MsgPluralNode, or MsgPluralRemainderNode.
   */
  public static interface MsgBlockNode extends BlockNode {}


  // -----------------------------------------------------------------------------------------------


  /**
   * A node that can be the initial content (i.e. initial child) of a MsgPlaceholderNode.
   */
  public static interface MsgPlaceholderInitialNode extends StandaloneNode {

    /**
     * Gets the user-supplied placeholder name, or null if not supplied or not applicable. Note that
     * this raw name can be any identifier (not necessarily in upper-underscore format).
     * @return The user-supplied placeholder name, or null if not supplied or not applicable.
     */
    public String getUserSuppliedPlaceholderName();

    /**
     * Generates the base placeholder name for this node.
     * @return The base placeholder name for this node.
     */
    public String genBasePlaceholderName();

    /**
     * Generates the key object used in comparisons to determine whether two placeholder nodes
     * should be represented by the same placeholder.
     * @return The key object for determining whether this node and another node should be
     *     represented by the same placeholder.
     */
    public Object genSamenessKey();
  }

}
