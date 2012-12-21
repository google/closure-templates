/*
 * Copyright 2011 Google Inc.
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.template.soy.base.BaseUtils;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.exprtree.DataRefAccessKeyNode;
import com.google.template.soy.exprtree.DataRefNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.soytree.CommandTextAttributesParser.Attribute;
import com.google.template.soy.soytree.SoyNode.MsgBlockNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;


/**
 * Abstract node representing a 'msg' block. Each child must be a RawTextNode, MsgPlaceholderNode,
 * MsgPluralNode, or MsgSelectNode.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public abstract class AbstractMsgNode extends AbstractBlockCommandNode
    implements StandaloneNode, MsgBlockNode {


  /** Parser for the command text. */
  private static final CommandTextAttributesParser ATTRIBUTES_PARSER =
      new CommandTextAttributesParser("msg",
          new Attribute("meaning", Attribute.ALLOW_ALL_VALUES, null),
          new Attribute("desc", Attribute.ALLOW_ALL_VALUES,
                        Attribute.NO_DEFAULT_VALUE_BECAUSE_REQUIRED),
          new Attribute("hidden", Attribute.BOOLEAN_VALUES, "false"));


  /** We don't support different content types. Always provide this value to the TC. */
  private static final String DEFAULT_CONTENT_TYPE = "text/html";


  /** The meaning string if set, otherwise null (usually null). */
  private final String meaning;

  /** The description string for translators. */
  private final String desc;

  /** Whether the message should be added as 'hidden' in the TC. */
  private final boolean isHidden;

  /** The generated map from placeholder name to representative node, or null if not generated. */
  private Map<String, MsgPlaceholderNode> phNameToRepNodeMap = null;

  /** The generated map from placeholder node to placeholder name, or null if not generated. */
  private Map<MsgPlaceholderNode, String> phNodeToNameMap = null;

  /** The generated map from plural var name to rep node, or null if not generated. */
  private Map<String, MsgPluralNode> pluralVarNameToRepNodeMap = null;

  /** The generated map from plural node to plural var name, or null if not generated. */
  private Map<MsgPluralNode, String> pluralNodeToVarNameMap = null;

  /** The generated map from select var name to rep node, or null if not generated. */
  private Map<String, MsgSelectNode> selectVarNameToRepNodeMap = null;

  /** The generated map from select node to select var name, or null if not generated. */
  private Map<MsgSelectNode, String> selectNodeToVarNameMap = null;


  /**
   * @param id The id for this node.
   * @param commandText The command text.
   * @throws SoySyntaxException If a syntax error is found.
   */
  public AbstractMsgNode(int id, String commandText) throws SoySyntaxException {
    super(id, "msg", commandText);

    Map<String, String> attributes = ATTRIBUTES_PARSER.parse(commandText);
    meaning = attributes.get("meaning");
    desc = attributes.get("desc");
    isHidden = attributes.get("hidden").equals("true");
  }


  /**
   * Constructor that copies most fields (except id). This constructor assumes children nodes will
   * not change, so we simply reuse the existing internal maps with pointers to the original
   * children. This constructor should be used when creating a GoogMsgNode from a MsgNode.
   * @param id The id for this node.
   * @param orig The original node from which to derive this node.
   * @see #AbstractMsgNode(AbstractMsgNode)
   */
  protected AbstractMsgNode(int id, AbstractMsgNode orig) {
    super(id, "msg", orig.getCommandText());
    this.meaning = orig.meaning;
    this.desc = orig.desc;
    this.isHidden = orig.isHidden;
    this.phNameToRepNodeMap =
        (orig.phNameToRepNodeMap != null) ? ImmutableMap.copyOf(orig.phNameToRepNodeMap) : null;
    this.phNodeToNameMap =
        (orig.phNodeToNameMap != null) ? ImmutableMap.copyOf(orig.phNodeToNameMap) : null;
    this.pluralVarNameToRepNodeMap =
        (orig.pluralVarNameToRepNodeMap != null) ?
            ImmutableMap.copyOf(orig.pluralVarNameToRepNodeMap) : null;
    this.pluralNodeToVarNameMap =
        (orig.pluralNodeToVarNameMap != null) ?
            ImmutableMap.copyOf(orig.pluralNodeToVarNameMap) : null;
    this.selectVarNameToRepNodeMap =
        (orig.selectVarNameToRepNodeMap != null) ?
            ImmutableMap.copyOf(orig.selectVarNameToRepNodeMap) : null;
    this.selectNodeToVarNameMap =
        (orig.selectNodeToVarNameMap != null) ?
            ImmutableMap.copyOf(orig.selectNodeToVarNameMap) : null;
  }


  /**
   * Copy constructor. This constructor clones the children nodes and regenerates all the internal
   * maps with pointers to the new children. This constructor should be used when cloning.
   * @param orig The node to copy.
   */
  protected AbstractMsgNode(AbstractMsgNode orig) {
    super(orig);
    this.meaning = orig.meaning;
    this.desc = orig.desc;
    this.isHidden = orig.isHidden;
    // The only reason we don't run genPhNamesAndSelectPluralVarsHelper from the other constructors
    // is because the children haven't been added yet. But for cloning, the children already exist,
    // so there's no reason not to run genPhNamesAndSelectPluralVarsHelper now.
    genPhNamesAndSelectPluralVarsHelper();
  }


  /** Returns the meaning string if set, otherwise null (usually null). */
  public String getMeaning() {
    return meaning;
  }

  /** Returns the description string for translators. */
  public String getDesc() {
    return desc;
  }

  /** Returns whether the message should be added as 'hidden' in the TC. */
  public boolean isHidden() {
    return isHidden;
  }

  /** Returns the content type for the TC. */
  public String getContentType() {
    return DEFAULT_CONTENT_TYPE;
  }


  /** Returns whether this is a plural or select message. */
  public boolean isPlrselMsg() {
    return getChildren().size() == 1 &&
        (getChild(0) instanceof MsgPluralNode || getChild(0) instanceof MsgSelectNode);
  }


  /**
   * Gets the representative placeholder node for a given placeholder name.
   * @param placeholderName The placeholder name.
   * @return The representative placeholder node for the given placeholder name.
   */
  public MsgPlaceholderNode getRepPlaceholderNode(String placeholderName) {
    if (phNameToRepNodeMap == null) {
      genPhNamesAndSelectPluralVarsHelper();
    }
    return phNameToRepNodeMap.get(placeholderName);
  }


  /**
   * Gets the placeholder name for a given placeholder node.
   * @param placeholderNode The placeholder node.
   * @return The placeholder name for the given placeholder node.
   */
  public String getPlaceholderName(MsgPlaceholderNode placeholderNode) {
    if (phNodeToNameMap == null) {
      genPhNamesAndSelectPluralVarsHelper();
    }
    return phNodeToNameMap.get(placeholderNode);
  }


  /**
   * Gets the representative plural node for a given plural variable name.
   * @param pluralVarName The plural variable name.
   * @return The representative plural node for the given plural variable name.
   */
  public MsgPluralNode getRepPluralNode(String pluralVarName) {
    if (pluralVarNameToRepNodeMap == null) {
      genPhNamesAndSelectPluralVarsHelper();
    }
    return pluralVarNameToRepNodeMap.get(pluralVarName);
  }


  /**
   * Gets the variable name associated with a given plural node.
   * @param pluralNode The plural node.
   * @return The plural variable name for the given plural node.
   */
  public String getPluralVarName(MsgPluralNode pluralNode) {
    if (pluralNodeToVarNameMap == null) {
      genPhNamesAndSelectPluralVarsHelper();
    }
    return pluralNodeToVarNameMap.get(pluralNode);
  }


  /**
   * Gets the representative select node for a given select variable name.
   * @param selectVarName The select variable name.
   * @return The representative select node for the given select variable name.
   */
  public MsgSelectNode getRepSelectNode(String selectVarName) {
    if (selectVarNameToRepNodeMap == null) {
      genPhNamesAndSelectPluralVarsHelper();
    }
    return selectVarNameToRepNodeMap.get(selectVarName);
  }


  /**
   * Gets the variable name associated with a given select node.
   * @param selectNode The select node.
   * @return The select variable name for the given select node.
   */
  public String getSelectVarName(MsgSelectNode selectNode) {
    if (selectNodeToVarNameMap == null) {
      genPhNamesAndSelectPluralVarsHelper();
    }
    return selectNodeToVarNameMap.get(selectNode);
  }


  /*
   * Helper function to generate internal maps with details of
   * placeholders, select variables and plural variables and the
   * corresponding nodes.  This should be called before retrieving
   * data from any of the internal maps.
   *
   * This builds the following maps for each of placeholders, plurals and selects.
   * <li>Map from name or var name to the representative node.  There is only
   *     one rep node corresponding to a given name.  There may be many nodes
   *     with exactly same var name and source string, but only one will be
   *     mapped here. If the source strings are different, the var names will be
   *     different.
   * <li>Map from a node to its name or var name.  All nodes are included in this
   *     map. There may be many nodes that map to the same name or var name.
   *
   * It is guaranteed that:
   * <li>A plural var name, a select var name and placeholder name will never
   *     be the same in the same message, even if they have identical source
   *     strings.
   * <li>The plural/select var name of a node will never be the same as the
   *     plural/select var name of another node with a different source string.
   *     If the source strings are identical, the var names also will be
   *     identical.
   * <li>The name of a placeholder will never be the same as another placeholder
   *     with a different source string.  If the source strings are the same,
   *     the placeholder names also will be the same.
   */
  @SuppressWarnings("SuspiciousMethodCalls")
  protected void genPhNamesAndSelectPluralVarsHelper() {

    // ------ Step 1: Determine representative nodes and build preliminary map ------
    //
    // Specifically, we are building (base name) -> (list of RepNodes) map for
    // placeholders, select nodes and plural nodes.
    //
    // If there are multiple nodes in the message that are exactly the same, then the
    // first such node encountered becomes the "representative node" for the group ("RepNode" in
    // variable names). The rest of the same nodes are non-representative ("nonRepNode").
    //
    // The (base name) -> (list of RepNodes) map is from base name to the list of
    // representative nodes (not exactly same) that all generate the same base name. If we
    // encounter a non-representative node, then we insert it into nonRepNodeToRepNodeMap, mapping
    // it to its corresponding representative node.
    //
    // The (base name) -> (list of RepNodes) map is preliminary because some of the final
    // names will be the base names plus some unique suffix.

    Map<String, List<SoyNode>> baseNameToRepNodesMap = Maps.newHashMap();
    Map<SoyNode, SoyNode> nonRepNodeToRepNodeMap = Maps.newHashMap();

    Deque<SoyNode> traversalQueue = new ArrayDeque<SoyNode>();
    // Seed the traversal queue with the children of this MsgNode.
    for (SoyNode child : this.getChildren()) {
      if (child instanceof MsgPlaceholderNode || child instanceof MsgPluralNode ||
          child instanceof MsgSelectNode) {
        traversalQueue.add(child);
      }
    }

    while (traversalQueue.size() > 0) {
      SoyNode node = traversalQueue.remove();
      String baseName;
      if (node instanceof MsgSelectNode)  {
        addGrandchildrenToQueue(traversalQueue, (MsgSelectNode) node);
        baseName = genBaseNameFromExpr(((MsgSelectNode) node).getExpr(), "STATUS");
      } else if (node instanceof MsgPluralNode) {
        addGrandchildrenToQueue(traversalQueue, (MsgPluralNode) node);
        baseName = genBaseNameFromExpr(((MsgPluralNode) node).getExpr(), "NUM");
      } else if (node instanceof MsgPlaceholderNode) {
        baseName = ((MsgPlaceholderNode) node).genBasePlaceholderName();
      } else {
        throw new AssertionError();
      }
      if (!baseNameToRepNodesMap.containsKey(baseName)) {
        // Case 1: First occurrence of this base name.
        baseNameToRepNodesMap.put(baseName, Lists.newArrayList(node));
      } else {
        List<SoyNode> nodesWithSameBaseName = baseNameToRepNodesMap.get(baseName);
        boolean isNew = true;
        for (SoyNode other : nodesWithSameBaseName) {
          if (isSameAs(node, other)) {
            // Case 2: Exactly same as another node we've seen.
            nonRepNodeToRepNodeMap.put(node, other);
            isNew = false;
            break;
          }
        }
        if (isNew) {
          // Case 3: New representative node that has the same base name as another
          // node we've seen, but is not the same node.
          nodesWithSameBaseName.add(node);
        }
      }
    }

    // ------ Step 2: Build final maps of name to representative node ------
    //
    // The final map *NameToRepNodeMap must be a one-to-one mapping. If a base name
    // only maps to one representative node, then we simply put that same mapping into the final
    // map. But if a base name maps to multiple nodes, we must append number suffixes
    // ("_1", "_2", etc) to make the names unique.
    //
    // Note: We must be careful that, while appending number suffixes, we don't generate a new
    // name that is the same as an existing base name.

    phNameToRepNodeMap = Maps.newHashMap();
    pluralVarNameToRepNodeMap = Maps.newHashMap();
    selectVarNameToRepNodeMap = Maps.newHashMap();

    for (Map.Entry<String, List<SoyNode>> entry : baseNameToRepNodesMap.entrySet()) {
      String baseName = entry.getKey();
      List<SoyNode> nodesWithSameBaseName = entry.getValue();
      if (nodesWithSameBaseName.size() == 1) {
        updateFinalMapsWithNode(baseName, nodesWithSameBaseName.get(0));
      } else {
        // Case 2: Multiple nodes generate this base name. Need number suffixes.
        int nextSuffix = 1;
        for (SoyNode repNode : nodesWithSameBaseName) {
          String newName;
          do {
            newName = baseName + "_" + nextSuffix;
            ++nextSuffix;
          } while (baseNameToRepNodesMap.containsKey(newName));
          updateFinalMapsWithNode(newName, repNode);
        }
      }
    }

    // ------ Step 3: Create maps of every node to its name ------

    // Reverse the maps of names to representative nodes.
    phNodeToNameMap = Maps.newHashMap();
    for (Map.Entry<String, MsgPlaceholderNode> entry : phNameToRepNodeMap.entrySet()) {
      phNodeToNameMap.put(entry.getValue(), entry.getKey());
    }

    selectNodeToVarNameMap = Maps.newHashMap();
    for (Map.Entry<String, MsgSelectNode> entry : selectVarNameToRepNodeMap.entrySet()) {
      selectNodeToVarNameMap.put(entry.getValue(), entry.getKey());
    }

    pluralNodeToVarNameMap = Maps.newHashMap();
    for (Map.Entry<String, MsgPluralNode> entry : pluralVarNameToRepNodeMap.entrySet()) {
      pluralNodeToVarNameMap.put(entry.getValue(), entry.getKey());
    }

    // Add mappings for the non-representative nodes.
    for (Map.Entry<SoyNode, SoyNode> entry : nonRepNodeToRepNodeMap.entrySet()) {
      SoyNode nonRepNode = entry.getKey();
      SoyNode repNode = entry.getValue();
      if (nonRepNode instanceof MsgPlaceholderNode) {
        phNodeToNameMap.put(
            (MsgPlaceholderNode) nonRepNode, phNodeToNameMap.get(repNode));
      } else if (nonRepNode instanceof MsgSelectNode) {
        selectNodeToVarNameMap.put(
            (MsgSelectNode) nonRepNode, selectNodeToVarNameMap.get(repNode));
      } else if (nonRepNode instanceof MsgPluralNode) {
        pluralNodeToVarNameMap.put(
            (MsgPluralNode) nonRepNode, pluralNodeToVarNameMap.get(repNode));
      }
    }
  }


  /**
   * Helper function to add a parent node and its children to the traversal queue.
   * @param traversalQueue The traversal queue.
   * @param selectOrPluralNode The parent node.
   */
  private static void addGrandchildrenToQueue(
      Deque<SoyNode> traversalQueue, ParentSoyNode<CaseOrDefaultNode> selectOrPluralNode) {
    for (CaseOrDefaultNode child : selectOrPluralNode.getChildren()) {
      for (SoyNode grandchild : child.getChildren()) {
        if (grandchild instanceof MsgPlaceholderNode || grandchild instanceof MsgPluralNode ||
            grandchild instanceof MsgSelectNode) {
          traversalQueue.add(grandchild);
        }
      }
    }
  }


  /**
   * Helper function to update the appropriate map with a (baseName, node) info.
   * @param baseName The base name.
   * @param node The node.
   */
  private void updateFinalMapsWithNode(String baseName, SoyNode node) {
    if (node instanceof MsgPlaceholderNode) {
      phNameToRepNodeMap.put(baseName, (MsgPlaceholderNode) node);
    } else if (node instanceof MsgSelectNode) {
      selectVarNameToRepNodeMap.put(baseName, (MsgSelectNode) node);
    } else if (node instanceof MsgPluralNode){
      pluralVarNameToRepNodeMap.put(baseName, (MsgPluralNode) node);
    }
  }


  /**
   * Helper function to determine whether two nodes are equivalent.
   * @param node The first node.
   * @param otherNode The second node.
   * @return true if they contain the same data, false otherwise.
   */
  private static boolean isSameAs(SoyNode node, SoyNode otherNode) {
    if ((node instanceof MsgPlaceholderNode) && (otherNode instanceof MsgPlaceholderNode)) {
      return ((MsgPlaceholderNode) node).isSamePlaceholderAs((MsgPlaceholderNode) otherNode);
    } else if ((node instanceof MsgPluralNode) && (otherNode instanceof MsgPluralNode)) {
      return (((MsgPluralNode) node).getCommandText().equals(
          ((MsgPluralNode) otherNode).getCommandText()));
    } else if ((node instanceof MsgSelectNode) && (otherNode instanceof MsgSelectNode)) {
      return (((MsgSelectNode) node).getCommandText().equals(
          ((MsgSelectNode) otherNode).getCommandText()));
    } else {
      return false;
    }
  }


  @Override public BlockNode getParent() {
    return (BlockNode) super.getParent();
  }


  // -----------------------------------------------------------------------------------------------
  // Static helpers that other nodes can use.


  /**
   * Helper function to get the base placeholder (or plural/select var) name from an expression.
   *
   * If the expression is a data ref or global, then the last key (if any) is used as the base
   * placeholder name. Otherwise, the fallback name is used.
   *
   * For example,
   *   $foo -> FOO
   *   $foo.0 -> fallback
   *   $foo[0] -> fallback
   *   $foo.0.bar -> BAR
   *   $fooBar -> FOO_BAR
   *   $foo_bar -> FOO_BAR
   *   $foo.barBaz -> BAR_BAZ
   *   foo.BAR_BAZ -> BAR_BAZ
   *   min($foo, 4) -> fallback
   *
   * @param exprRoot The root node for an expression.
   * @param fallbackBaseName The fallback base name.
   * @return The base placeholder (or plural/select var) name for the given expression.
   */
  static String genBaseNameFromExpr(ExprRootNode<?> exprRoot, String fallbackBaseName) {

    ExprNode exprNode = exprRoot.getChild(0);

    if (exprNode instanceof DataRefNode) {
      DataRefNode dataRefNode = (DataRefNode) exprNode;

      if (dataRefNode.numChildren() > 0) {
        ExprNode lastChild = dataRefNode.getChild(dataRefNode.numChildren() - 1);
        // Only handle if last child is a key. Else, fall through.
        if (lastChild instanceof DataRefAccessKeyNode) {
          return BaseUtils.convertToUpperUnderscore(((DataRefAccessKeyNode) lastChild).getKey());
        }
      } else {
        // No children, so the first key is the last key.
        return BaseUtils.convertToUpperUnderscore(dataRefNode.getFirstKey());
      }

    } else if (exprNode instanceof GlobalNode) {
      String globalName = ((GlobalNode) exprNode).getName();
      return BaseUtils.convertToUpperUnderscore(BaseUtils.extractPartAfterLastDot(globalName));
    }

    return fallbackBaseName;
  }

}
