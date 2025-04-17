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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.basetree.Node;
import com.google.template.soy.basetree.ParentNode;
import com.google.template.soy.exprtree.AbstractLocalVarDefn;
import com.google.template.soy.exprtree.ExprEquivalence;
import com.google.template.soy.exprtree.ExprRootNode;

/**
 * This class defines the base interface for a node in the parse tree, as well as a number of
 * subinterfaces that extend the base interface in various aspects. Every concrete node implements
 * some subset of these interfaces.
 *
 * <p>The top level definition is the base node interface.
 */
public interface SoyNode extends Node {

  /** Enum of specific node kinds (corresponding to specific node types). */
  enum Kind {
    SOY_FILE_SET_NODE,
    SOY_FILE_NODE,
    IMPORT_NODE,

    TEMPLATE_BASIC_NODE,
    TEMPLATE_DELEGATE_NODE,
    TEMPLATE_ELEMENT_NODE,

    RAW_TEXT_NODE,

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

    CONST_NODE,
    TYPEDEF_NODE,
    EXTERN_NODE,
    JAVA_IMPL_NODE,
    JS_IMPL_NODE,
    AUTO_IMPL_NODE,
    LET_VALUE_NODE,
    LET_CONTENT_NODE,

    RETURN_NODE,
    ASSIGNMENT_NODE,

    IF_NODE,
    IF_COND_NODE,
    IF_ELSE_NODE,

    SWITCH_NODE,
    SWITCH_CASE_NODE,
    SWITCH_DEFAULT_NODE,

    FOR_NODE,
    FOR_NONEMPTY_NODE,

    WHILE_NODE,

    BREAK_NODE,
    CONTINUE_NODE,

    CALL_BASIC_NODE,
    CALL_DELEGATE_NODE,
    CALL_PARAM_VALUE_NODE,
    CALL_PARAM_CONTENT_NODE,

    HTML_OPEN_TAG_NODE,
    HTML_CLOSE_TAG_NODE,
    HTML_ATTRIBUTE_NODE,
    HTML_ATTRIBUTE_VALUE_NODE,
    HTML_COMMENT_NODE,

    KEY_NODE,
    SKIP_NODE,

    VE_LOG_NODE,

    LOG_NODE,
    DEBUGGER_NODE,
    EVAL_NODE
  }

  /** Returns this node's kind (corresponding to this node's specific type). */
  @Override
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
   * <p>Includes nodes such as SoyFileSetNode, SoyFileNode, IfNode, SwitchNode, ForNode, CallNode,
   * etc.
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
    ParentSoyNode<StandaloneNode> getParent();

    @Override
    StandaloneNode copy(CopyState copyState);
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
  }

  // -----------------------------------------------------------------------------------------------

  /** A node that represents a Soy command that encloses a template block. */
  interface BlockCommandNode extends CommandNode, BlockNode {
    SourceLocation getOpenTagLocation();
  }

  // -----------------------------------------------------------------------------------------------

  /** A node that represents an independent unit of rendering. */
  interface RenderUnitNode extends BlockCommandNode {

    /**
     * Returns the content kind for strict autoescape. If not explicitly set, will throw an
     * exception if not inferred yet.
     */
    SanitizedContentKind getContentKind();

    /** Returns if the kind is not explicitly set and has not been inferred yet. */
    boolean isImplicitContentKind();
  }

  /** Node that provides a Java stack trace element. */
  interface StackContextNode extends SoyNode {
    StackTraceElement createStackTraceElement(SourceLocation srcLocation);
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
   * ForNonemptyNode, ForNode, WhileNode, etc.
   */
  interface ConditionalBlockNode extends BlockNode {}

  // -----------------------------------------------------------------------------------------------

  /**
   * A node that adds a new local variable. The scope of the new local variable comprises either the
   * children of this node or the younger siblings of this node.
   */
  interface LocalVarNode extends SoyNode {

    /** Returns the name of this node's local variable (without the preceding '$'). */
    default String getVarName() {
      return getVar().name();
    }

    default String getVarRefName() {
      return getVar().refName();
    }

    /** Returns the variable definition. */
    AbstractLocalVarDefn<?> getVar();
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
    ImmutableList<ExprRootNode> getExprList();
  }

  // -----------------------------------------------------------------------------------------------

  /**
   * A substitution unit is any non-raw-text message part, since it will be replaced when the
   * message is rendered. Currently, one of {@link MsgPlaceholderNode}, {@link MsgSelectNode}, or
   * {@link MsgPluralNode}.
   */
  interface MsgSubstUnitNode extends StandaloneNode {

    @Override
    MsgBlockNode getParent();

    /**
     * Returns the placeholder meta data for this substitution unit. {@code
     * MessagePlaceholder::name} is the base placeholder name, it might have a suffix appended if it
     * collides with another.
     *
     * <p>Note: This isn't quite correct semantically. It's conceivable that a new type of
     * substitution unit in the future could have multiple vars. But until that happens, this
     * simpler model is sufficient.
     */
    MessagePlaceholder getPlaceholder();

    /**
     * Returns whether this substitution unit should use the same var name as another substitution
     * unit. (For placeholders, this means the other placeholder is exactly the same as this one,
     * i.e. it appears twice in the same message.)
     *
     * @param other The other substitution unit to check against.
     */
    boolean shouldUseSameVarNameAs(MsgSubstUnitNode other, ExprEquivalence exprEquivalence);
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

    /** A value object that can be used to test for placeholder */
    interface SamenessKey {
      SamenessKey copy(CopyState copy);
    }

    /** A SamenessKey that uses the identity of a SoyNode. */
    final class IdentitySamenessKey implements SamenessKey {
      private SoyNode node;

      IdentitySamenessKey(SoyNode node) {
        this.node = checkNotNull(node);
      }

      private IdentitySamenessKey(IdentitySamenessKey orig, CopyState copyState) {
        this.node = orig.node;
        copyState.registerRefListener(orig.node, newNode -> this.node = newNode);
      }

      @Override
      public IdentitySamenessKey copy(CopyState copyState) {
        return new IdentitySamenessKey(this, copyState);
      }

      @Override
      public boolean equals(Object other) {
        return other instanceof IdentitySamenessKey && ((IdentitySamenessKey) other).node == node;
      }

      @Override
      public int hashCode() {
        return System.identityHashCode(node);
      }
    }

    /** Base placeholder name and associated info, for this node. */
    MessagePlaceholder getPlaceholder();

    /**
     * Generates the key object used in comparisons to determine whether two placeholder nodes
     * should be represented by the same placeholder.
     *
     * @return The key object for determining whether this node and another node should be
     *     represented by the same placeholder.
     */
    SamenessKey genSamenessKey();
  }
}
