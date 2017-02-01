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

import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.basetree.Node;
import com.google.template.soy.basetree.ParentNode;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.exprtree.VarDefn;
import java.util.List;
import javax.annotation.Nullable;

/**
 * This class defines the base interface for a node in the parse tree, as well as a number of
 * subinterfaces that extend the base interface in various aspects. Every concrete node implements
 * some subset of these interfaces.
 *
 * <p>The top level definition is the base node interface.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public interface SoyNode extends Node {

  /**
   * Enum of specific node kinds (corresponding to specific node types).
   *
   * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
   */
  enum Kind {

    SOY_FILE_SET_NODE,
    SOY_FILE_NODE,

    TEMPLATE_BASIC_NODE,
    TEMPLATE_DELEGATE_NODE,

    RAW_TEXT_NODE,

    GOOG_MSG_DEF_NODE,
    GOOG_MSG_REF_NODE,

    MSG_FALLBACK_GROUP_NODE,
    MSG_NODE,
    MSG_PLURAL_NODE,
    MSG_PLURAL_CASE_NODE,
    MSG_PLURAL_DEFAULT_NODE,
    MSG_SELECT_NODE,
    MSG_SELECT_CASE_NODE,
    MSG_SELECT_DEFAULT_NODE,
    MSG_PLACEHOLDER_NODE,
    MSG_HTML_TAG_NODE,

    PRINT_NODE,
    PRINT_DIRECTIVE_NODE,

    XID_NODE,
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

    // These Node types are created by the com.google.template.soy.html package. RawTextNodes that
    // appear in an HTML or attribute context are transformed into these node types. In general,
    // passes that do not output generated code should not need to worry about these types, other
    // than treating them as generic parent nodes that may contain descendants they are interested
    // in.
    INCREMENTAL_HTML_OPEN_TAG,
    INCREMENTAL_HTML_CLOSE_TAG,
    INCREMENTAL_HTML_ATTRIBUTE,

    // TODO(lukes): These nodes are created by the main parser and should eventually subsume the
    // usecases of the incremental dom nodes defined above (and also MsgHtmlTagNode).  But for the
    // time being we maintain 2 such set of nodes while under development.
    HTML_OPEN_TAG_NODE,
    HTML_CLOSE_TAG_NODE,
    HTML_ATTRIBUTE_NODE,
    HTML_ATTRIBUTE_VALUE_NODE,

    LOG_NODE,
    DEBUGGER_NODE,
  }


  /** Returns this node's kind (corresponding to this node's specific type). */
  Kind getKind();

  /**
   * Sets this node's id.
   *
   * <p>Important: The id should already be set during construction, so this method should only be
   * used during cloning.
   *
   * @param id The new id for this node.
   */
  void setId(int id);

  /** Returns this node's id. */
  int getId();

  @Override
  ParentSoyNode<?> getParent();

  /**
   * {@inheritDoc}
   *
   * <p>The copied nodes will have the same ids as the original nodes. If you need to copy a subtree
   * with new ids assigned to the copied nodes, use {@link SoyTreeUtils#cloneWithNewIds}.
   */
  @Override
  SoyNode copy(CopyState copyState);

  // -----------------------------------------------------------------------------------------------

  /** A node in a Soy parse tree that may be a parent. */
  interface ParentSoyNode<N extends SoyNode> extends SoyNode, ParentNode<N> {}

  // -----------------------------------------------------------------------------------------------

  /**
   * A node that represents the top of a split-level structure in the parse tree. This indicates
   * there are special structural requirements on its immediate children (e.g. IfNode may only have
   * IfCondNode and IfElseNode as children).
   *
   * <p>Includes nodes such as SoyFileSetNode, SoyFileNode, IfNode, SwitchNode, ForeachNode,
   * CallNode, etc.
   *
   * <p>During optimization, the immediate children should never be moved, but lower descendants may
   * be freely moved (either moved within the node's subtree or moved outside of the node's
   * subtree).
   */
  interface SplitLevelTopNode<N extends SoyNode> extends ParentSoyNode<N> {}


  // -----------------------------------------------------------------------------------------------


  /**
   * A node that can legally appear as the direct child of some block node (doesn't necessarily have
   * to be legal as the direct child of a template). To put it another way, a node that can legally
   * appear as the sibling of a RawTextNode or PrintNode.
   */
  interface StandaloneNode extends SoyNode {

    @Override
    BlockNode getParent();
  }


  // -----------------------------------------------------------------------------------------------

  /** A node that represents a template block. */
  interface BlockNode extends ParentSoyNode<StandaloneNode> {}

  // -----------------------------------------------------------------------------------------------

  /** A node that represents a specific Soy command. */
  interface CommandNode extends SoyNode {

    /** Returns the Soy command name. */
    String getCommandName();

    /** Returns the command text (may be the empty string). */
    String getCommandText();

    /**
     * Builds a Soy tag string that could be the Soy tag for this node. Note that this may not
     * necessarily be the actual original Soy tag, but a (sort of) canonical equivalent.
     * @return A Soy tag string that could be the Soy tag for this node.
     */
    String getTagString();
  }


  // -----------------------------------------------------------------------------------------------

  /** A node that represents a Soy command that encloses a template block. */
  interface BlockCommandNode extends CommandNode, BlockNode {}

  // -----------------------------------------------------------------------------------------------

  /** A node that represents an independent unit of rendering. */
  interface RenderUnitNode extends BlockCommandNode {

    /**
     * Returns the content kind for strict autoescape, or null if not specified or not applicable.
     */
    @Nullable
    ContentKind getContentKind();
  }


  // -----------------------------------------------------------------------------------------------

  /** A node that represents a specific Soy statement. */
  interface StatementNode extends StandaloneNode {}

  // -----------------------------------------------------------------------------------------------

  /**
   * A node that represents a block of Soy code that is conditionally executed. During optimization,
   * descendants should generally never be moved outside of the subtree of such a node. We make an
   * exception for LoopNodes because we don't want to lose the ability to pull invariants out of
   * loops.
   *
   * <p>Includes nodes such as IfCondNode, IfElseNode, SwitchCaseNode, SwitchDefaultNode,
   * ForeachNonemptyNode, ForeachIfemptyNode, ForNode etc.
   */
  interface ConditionalBlockNode extends BlockNode {}

  // -----------------------------------------------------------------------------------------------

  /**
   * A node that represents a block of code that is executed in a loop.
   *
   * <p>Includes nodes such as ForeachNonemptyNode and ForNode.
   */
  interface LoopNode extends BlockNode {}

  // -----------------------------------------------------------------------------------------------

  /**
   * A node that adds a new local variable. The scope of the new local variable comprises either the
   * children of this node or the younger siblings of this node.
   */
  interface LocalVarNode extends SoyNode {

    /** Returns the name of this node's local variable (without the preceding '$'). */
    String getVarName();

    /** Returns the variable definition. */
    VarDefn getVar();
  }


  // -----------------------------------------------------------------------------------------------

  /** A node that adds a new local variable whose scope comprises the children of this code. */
  interface LocalVarBlockNode extends LocalVarNode, BlockNode {}


  // -----------------------------------------------------------------------------------------------


  /**
   * A node that adds a new local variable whose scope comprises the younger siblings of this node.
   */
  interface LocalVarInlineNode extends LocalVarNode, StandaloneNode {}


  // -----------------------------------------------------------------------------------------------

  /** A node that holds some expressions in its fields/properties. */
  interface ExprHolderNode extends SoyNode {

    /** Returns the list of expressions in this node. */
    List<ExprUnion> getAllExprUnions();
  }


  // -----------------------------------------------------------------------------------------------


  /**
   * A substitution unit is any non-raw-text message part, since it will be replaced when the
   * message is rendered. Currently, one of MsgPlaceholderNode, MsgSelectNode, MsgPluralNode, or
   * MsgPluralRemainderNode.
   */
  interface MsgSubstUnitNode extends StandaloneNode {

    @Override
    MsgBlockNode getParent();

    /**
     * Returns the base var name for this substitution unit. (For a placeholder, this is the base
     * placeholder name.)
     *
     * <p>Note: This isn't quite correct semantically. It's conceivable that a new type of
     * substitution unit in the future could have multiple vars. But until that happens, this
     * simpler model is sufficient.
     */
    String getBaseVarName();

    /**
     * Returns whether this substitution unit should use the same var name as another substitution
     * unit. (For placeholders, this means the other placeholder is exactly the same as this one,
     * i.e. it appears twice in the same message.)
     * @param other The other substitution unit to check against.
     */
    boolean shouldUseSameVarNameAs(MsgSubstUnitNode other);
  }


  // -----------------------------------------------------------------------------------------------


  /**
   * A block node that can hold message content. Every direct child of a MsgBlockNode must be either
   * a RawTextNode or a MsgSubstUnitNode.
   */
  interface MsgBlockNode extends BlockNode {}


  // -----------------------------------------------------------------------------------------------

  /** A node that can be the initial content (i.e. initial child) of a MsgPlaceholderNode. */
  interface MsgPlaceholderInitialNode extends StandaloneNode {

    /**
     * Gets the user-supplied placeholder name, or null if not supplied or not applicable. Note that
     * this raw name can be any identifier (not necessarily in upper-underscore format).
     * @return The user-supplied placeholder name, or null if not supplied or not applicable.
     */
    String getUserSuppliedPhName();

    /**
     * Generates the base placeholder name for this node.
     * @return The base placeholder name for this node.
     */
    String genBasePhName();

    /**
     * Generates the key object used in comparisons to determine whether two placeholder nodes
     * should be represented by the same placeholder.
     * @return The key object for determining whether this node and another node should be
     *     represented by the same placeholder.
     */
    Object genSamenessKey();
  }

}
