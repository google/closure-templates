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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprparse.ExpressionParser;
import com.google.template.soy.exprparse.SoyParsingContext;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.internal.base.Pair;
import com.google.template.soy.soytree.CommandTextAttributesParser.Attribute;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.MsgBlockNode;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Node representing a 'msg' block. Every child must be a RawTextNode, MsgPlaceholderNode,
 * MsgPluralNode, or MsgSelectNode.
 *
 * <p>The AST will be one of the following
 *
 * <ul>
 *   <li>A single {@link RawTextNode}
 *   <li>A mix of {@link RawTextNode} and {@link MsgPlaceholderNode}
 *   <li>A single {@link MsgPluralNode}
 *   <li>A single {@link MsgSelectNode}
 * </ul>
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class MsgNode extends AbstractBlockCommandNode
    implements ExprHolderNode, MsgBlockNode {

  static final SoyErrorKind WRONG_NUMBER_OF_GENDER_EXPRS =
      SoyErrorKind.of("Attribute ''genders'' does not contain 1-3 expressions");

  /**
   * Returns a new {@link Builder} representing a {@code msg} MsgNode.
   *
   * @param id The node's id.
   * @param commandText The node's command text.
   * @param sourceLocation The node's source location.
   */
  public static Builder msg(int id, String commandText, SourceLocation sourceLocation) {
    return new Builder(id, "msg", commandText, sourceLocation);
  }

  /**
   * Returns a new {@link Builder} representing a {@code fallbackmsg} MsgNode.
   *
   * @param id The node's id.
   * @param commandText The node's command text.
   * @param sourceLocation The node's source location.
   */
  public static Builder fallbackmsg(int id, String commandText, SourceLocation sourceLocation) {
    return new Builder(id, "fallbackmsg", commandText, sourceLocation);
  }

  private static class SubstUnitInfo {

    /**
     * The generated map from substitution unit var name to representative node.
     *
     * <p>There is only one rep node for each var name. There may be multiple nodes with the same
     * var name, but only one will be mapped here. If two nodes should not have the same var name,
     * then their var names will be different, even if their base var names are the same.
     */
    public final ImmutableMap<String, MsgSubstUnitNode> varNameToRepNodeMap;
    /**
     * The generated map from substitution unit node to var name.
     *
     * <p>There may be multiple nodes that map to the same var name.
     */
    public final ImmutableMap<MsgSubstUnitNode, String> nodeToVarNameMap;

    public SubstUnitInfo(
        ImmutableMap<String, MsgSubstUnitNode> varNameToRepNodeMap,
        ImmutableMap<MsgSubstUnitNode, String> nodeToVarNameMap) {
      this.varNameToRepNodeMap = varNameToRepNodeMap;
      this.nodeToVarNameMap = nodeToVarNameMap;
    }
  }

  /** Parser for the command text. */
  private static final CommandTextAttributesParser ATTRIBUTES_PARSER =
      new CommandTextAttributesParser(
          "msg",
          new Attribute("genders", Attribute.ALLOW_ALL_VALUES, null),
          new Attribute("meaning", Attribute.ALLOW_ALL_VALUES, null),
          new Attribute(
              "desc", Attribute.ALLOW_ALL_VALUES, Attribute.NO_DEFAULT_VALUE_BECAUSE_REQUIRED),
          new Attribute("hidden", Attribute.BOOLEAN_VALUES, "false"));

  /** We don't use different content types. It may be a historical artifact in the TC. */
  private static final String DEFAULT_CONTENT_TYPE = "text/html";

  /** The list of expressions for gender values. Null after rewriting. */
  @Nullable private List<ExprRootNode> genderExprs;

  /** The meaning string if set, otherwise null (usually null). */
  private final String meaning;

  /** The description string for translators. */
  private final String desc;

  /** Whether the message should be added as 'hidden' in the TC. */
  private final boolean isHidden;

  /** The substitution unit info (var name mappings, or null if not yet generated. */
  private SubstUnitInfo substUnitInfo = null;

  private MsgNode(
      int id,
      SourceLocation sourceLocation,
      @Nullable List<ExprRootNode> genderExprs,
      String commandName,
      String commandText,
      String meaning,
      String desc,
      boolean isHidden) {
    super(id, sourceLocation, commandName, commandText);
    this.genderExprs = genderExprs;
    this.meaning = meaning;
    this.desc = desc;
    this.isHidden = isHidden;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private MsgNode(MsgNode orig, CopyState copyState) {
    super(orig, copyState);
    if (orig.genderExprs != null) {
      ImmutableList.Builder<ExprRootNode> builder = ImmutableList.builder();
      for (ExprRootNode node : orig.genderExprs) {
        builder.add(node.copy(copyState));
      }
      this.genderExprs = builder.build();
    } else {
      this.genderExprs = null;
    }
    this.meaning = orig.meaning;
    this.desc = orig.desc;
    this.isHidden = orig.isHidden;
    // The only reason we don't run genSubstUnitInfo from the other constructors is because the
    // children haven't been added yet. But for cloning, the children already exist, so there's no
    // reason not to run genSubstUnitInfo now.
    substUnitInfo = genSubstUnitInfo(this);
  }

  @Override
  public Kind getKind() {
    return Kind.MSG_NODE;
  }

  /**
   * Returns the list of expressions for gender values and sets that field to null. Note that this
   * node's command text will still contain the substring genders="...". We think this is okay since
   * the command text is only used for reporting errors (in fact, it might be good as a reminder of
   * how the msg was originally written).
   */
  @Nullable
  public List<ExprRootNode> getAndRemoveGenderExprs() {
    List<ExprRootNode> genderExprs = this.genderExprs;
    this.genderExprs = null;
    return genderExprs;
  }

  @Override
  public List<ExprUnion> getAllExprUnions() {
    if (genderExprs != null) {
      throw new AssertionError();
    }
    return ImmutableList.of();
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
    return isSelectMsg() || isPluralMsg();
  }

  /** Returns whether this is a select message. */
  public boolean isSelectMsg() {
    return getChildren().size() == 1 && (getChild(0) instanceof MsgSelectNode);
  }

  /** Returns whether this is a plural message. */
  public boolean isPluralMsg() {
    return getChildren().size() == 1 && (getChild(0) instanceof MsgPluralNode);
  }

  /** Returns whether this is a raw text message. */
  public boolean isRawTextMsg() {
    return getChildren().size() == 1 && (getChild(0) instanceof RawTextNode);
  }

  /**
   * Gets the representative placeholder node for a given placeholder name.
   *
   * @param placeholderName The placeholder name.
   * @return The representative placeholder node for the given placeholder name.
   */
  public MsgPlaceholderNode getRepPlaceholderNode(String placeholderName) {
    if (substUnitInfo == null) {
      substUnitInfo = genSubstUnitInfo(this);
    }
    return (MsgPlaceholderNode) substUnitInfo.varNameToRepNodeMap.get(placeholderName);
  }

  /**
   * Gets the placeholder name for a given placeholder node.
   *
   * @param placeholderNode The placeholder node.
   * @return The placeholder name for the given placeholder node.
   */
  public String getPlaceholderName(MsgPlaceholderNode placeholderNode) {
    if (substUnitInfo == null) {
      substUnitInfo = genSubstUnitInfo(this);
    }
    return substUnitInfo.nodeToVarNameMap.get(placeholderNode);
  }

  /**
   * Gets the representative plural node for a given plural variable name.
   *
   * @param pluralVarName The plural variable name.
   * @return The representative plural node for the given plural variable name.
   */
  public MsgPluralNode getRepPluralNode(String pluralVarName) {
    if (substUnitInfo == null) {
      substUnitInfo = genSubstUnitInfo(this);
    }
    return (MsgPluralNode) substUnitInfo.varNameToRepNodeMap.get(pluralVarName);
  }

  /**
   * Gets the variable name associated with a given plural node.
   *
   * @param pluralNode The plural node.
   * @return The plural variable name for the given plural node.
   */
  public String getPluralVarName(MsgPluralNode pluralNode) {
    if (substUnitInfo == null) {
      substUnitInfo = genSubstUnitInfo(this);
    }
    return substUnitInfo.nodeToVarNameMap.get(pluralNode);
  }

  /**
   * Gets the representative select node for a given select variable name.
   *
   * @param selectVarName The select variable name.
   * @return The representative select node for the given select variable name.
   */
  public MsgSelectNode getRepSelectNode(String selectVarName) {
    if (substUnitInfo == null) {
      substUnitInfo = genSubstUnitInfo(this);
    }
    return (MsgSelectNode) substUnitInfo.varNameToRepNodeMap.get(selectVarName);
  }

  /**
   * Gets the variable name associated with a given select node.
   *
   * @param selectNode The select node.
   * @return The select variable name for the given select node.
   */
  public String getSelectVarName(MsgSelectNode selectNode) {
    if (substUnitInfo == null) {
      substUnitInfo = genSubstUnitInfo(this);
    }
    return substUnitInfo.nodeToVarNameMap.get(selectNode);
  }

  /** Getter for the generated map from substitution unit var name to representative node. */
  public ImmutableMap<String, MsgSubstUnitNode> getVarNameToRepNodeMap() {
    if (substUnitInfo == null) {
      substUnitInfo = genSubstUnitInfo(this);
    }
    return substUnitInfo.varNameToRepNodeMap;
  }

  @Override
  public String toSourceString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getTagString());
    appendSourceStringForChildren(sb);
    // Note: No end tag.
    return sb.toString();
  }

  @Override
  public MsgNode copy(CopyState copyState) {
    return new MsgNode(this, copyState);
  }

  // -----------------------------------------------------------------------------------------------
  // Static helpers for building SubstUnitInfo.

  /**
   * Helper function to generate SubstUnitInfo, which contains mappings from/to substitution unit
   * nodes (placeholders and plural/select nodes) to/from generated var names.
   *
   * <p>It is guaranteed that the same var name will never be shared by multiple nodes of different
   * types (types are placeholder, plural, and select).
   *
   * @param msgNode The MsgNode to process.
   * @return The generated SubstUnitInfo for the given MsgNode.
   */
  private static SubstUnitInfo genSubstUnitInfo(MsgNode msgNode) {
    return genFinalSubstUnitInfoMapsHelper(genPrelimSubstUnitInfoMapsHelper(msgNode));
  }

  /**
   * Private helper for genSubstUnitInfo(). Determines representative nodes and builds preliminary
   * maps.
   *
   * <p>If there are multiple nodes in the message that should share the same var name, then the
   * first such node encountered becomes the "representative node" for the group ("repNode" in
   * variable names). The rest of the nodes in the group are non-representative ("nonRepNode").
   *
   * <p>The baseNameToRepNodesMap is a multimap from each base name to its list of representative
   * nodes (they all generate the same base var name, but should not have the same final var name).
   * If we encounter a non-representative node, then we insert it into nonRepNodeToRepNodeMap,
   * mapping it to its corresponding representative node.
   *
   * <p>The base var names are preliminary because some of the final var names will be the base
   * names plus a unique suffix.
   *
   * @param msgNode The MsgNode being processed.
   * @return A pair of (1) a multimap from each base name to its list of representative nodes, and
   *     (2) a map from each non-representative node to its respective representative node.
   */
  @SuppressWarnings("unchecked")
  private static Pair<
          ListMultimap<String, MsgSubstUnitNode>, Map<MsgSubstUnitNode, MsgSubstUnitNode>>
      genPrelimSubstUnitInfoMapsHelper(MsgNode msgNode) {

    ListMultimap<String, MsgSubstUnitNode> baseNameToRepNodesMap = LinkedListMultimap.create();
    Map<MsgSubstUnitNode, MsgSubstUnitNode> nonRepNodeToRepNodeMap = new HashMap<>();

    Deque<MsgSubstUnitNode> traversalQueue = new ArrayDeque<>();

    // Seed the traversal queue with the children of this MsgNode.
    for (SoyNode child : msgNode.getChildren()) {
      if (child instanceof MsgSubstUnitNode) {
        traversalQueue.add((MsgSubstUnitNode) child);
      }
    }

    while (!traversalQueue.isEmpty()) {
      MsgSubstUnitNode node = traversalQueue.remove();

      if ((node instanceof MsgSelectNode) || (node instanceof MsgPluralNode)) {
        for (CaseOrDefaultNode child : ((ParentSoyNode<CaseOrDefaultNode>) node).getChildren()) {
          for (SoyNode grandchild : child.getChildren()) {
            if (grandchild instanceof MsgSubstUnitNode) {
              traversalQueue.add((MsgSubstUnitNode) grandchild);
            }
          }
        }
      }

      String baseName = node.getBaseVarName();
      if (!baseNameToRepNodesMap.containsKey(baseName)) {
        // Case 1: First occurrence of this base name.
        baseNameToRepNodesMap.put(baseName, node);
      } else {
        boolean isNew = true;
        for (MsgSubstUnitNode other : baseNameToRepNodesMap.get(baseName)) {
          if (node.shouldUseSameVarNameAs(other)) {
            // Case 2: Should use same var name as another node we've seen.
            nonRepNodeToRepNodeMap.put(node, other);
            isNew = false;
            break;
          }
        }
        if (isNew) {
          // Case 3: New representative node that has the same base name as another node we've seen,
          // but should not use the same var name.
          baseNameToRepNodesMap.put(baseName, node);
        }
      }
    }

    return Pair.of(baseNameToRepNodesMap, nonRepNodeToRepNodeMap);
  }

  /**
   * Private helper for genSubstUnitInfo(). Generates the final SubstUnitInfo given preliminary
   * maps.
   *
   * @param prelimMaps A pair of (1) a multimap from each base name to its list of representative
   *     nodes, and (2) a map from each non-representative node to its respective representative
   *     node.
   * @return The generated SubstUnitInfo.
   */
  private static SubstUnitInfo genFinalSubstUnitInfoMapsHelper(
      Pair<ListMultimap<String, MsgSubstUnitNode>, Map<MsgSubstUnitNode, MsgSubstUnitNode>>
          prelimMaps) {

    ListMultimap<String, MsgSubstUnitNode> baseNameToRepNodesMap = prelimMaps.first;
    Map<MsgSubstUnitNode, MsgSubstUnitNode> nonRepNodeToRepNodeMap = prelimMaps.second;

    // ------ Step 1: Build final map of var name to representative node. ------
    //
    // The final map substUnitVarNameToRepNodeMap must be a one-to-one mapping. If a base name only
    // maps to one representative node, then we simply put that same mapping into the final map. But
    // if a base name maps to multiple nodes, we must append number suffixes ("_1", "_2", etc) to
    // make the names unique.
    //
    // Note: We must be careful that, while appending number suffixes, we don't generate a new name
    // that is the same as an existing base name.

    Map<String, MsgSubstUnitNode> substUnitVarNameToRepNodeMap = new LinkedHashMap<>();

    for (String baseName : baseNameToRepNodesMap.keys()) {
      List<MsgSubstUnitNode> nodesWithSameBaseName = baseNameToRepNodesMap.get(baseName);
      if (nodesWithSameBaseName.size() == 1) {
        substUnitVarNameToRepNodeMap.put(baseName, nodesWithSameBaseName.get(0));
      } else {
        // Case 2: Multiple nodes generate this base name. Need number suffixes.
        int nextSuffix = 1;
        for (MsgSubstUnitNode repNode : nodesWithSameBaseName) {
          String newName;
          do {
            newName = baseName + "_" + nextSuffix;
            ++nextSuffix;
          } while (baseNameToRepNodesMap.containsKey(newName));
          substUnitVarNameToRepNodeMap.put(newName, repNode);
        }
      }
    }

    // ------ Step 2: Create map of every node to its var name. ------

    Map<MsgSubstUnitNode, String> substUnitNodeToVarNameMap = new LinkedHashMap<>();

    // Reverse the map of names to representative nodes.
    for (Map.Entry<String, MsgSubstUnitNode> entry : substUnitVarNameToRepNodeMap.entrySet()) {
      substUnitNodeToVarNameMap.put(entry.getValue(), entry.getKey());
    }

    // Add mappings for the non-representative nodes.
    for (Map.Entry<MsgSubstUnitNode, MsgSubstUnitNode> entry : nonRepNodeToRepNodeMap.entrySet()) {
      MsgSubstUnitNode nonRepNode = entry.getKey();
      MsgSubstUnitNode repNode = entry.getValue();
      substUnitNodeToVarNameMap.put(nonRepNode, substUnitNodeToVarNameMap.get(repNode));
    }

    return new SubstUnitInfo(
        ImmutableMap.copyOf(substUnitVarNameToRepNodeMap),
        ImmutableMap.copyOf(substUnitNodeToVarNameMap));
  }

  /**
   * Builder for {@link MsgNode}. Access through {@link MsgNode#msg} or {@link MsgNode#fallbackmsg}.
   */
  public static final class Builder {
    private final int id;
    private final String commandName;
    private final String commandText;
    private final SourceLocation sourceLocation;

    private Builder(int id, String commandName, String commandText, SourceLocation sourceLocation) {
      this.id = id;
      this.commandName = commandName;
      this.commandText = commandText;
      this.sourceLocation = sourceLocation;
    }

    /**
     * Returns a new {@link MsgNode} from the state of this builder, reporting syntax errors to the
     * given {@link ErrorReporter}.
     */
    public MsgNode build(SoyParsingContext context) {

      Map<String, String> attributes =
          ATTRIBUTES_PARSER.parse(commandText, context, sourceLocation);

      String gendersAttr = attributes.get("genders");
      List<ExprRootNode> genderExprs = null;
      if (gendersAttr != null) {
        genderExprs =
            ExprRootNode.wrap(
                new ExpressionParser(gendersAttr, sourceLocation, context).parseExpressionList());
        if (genderExprs.isEmpty() || genderExprs.size() > 3) {
          context.report(sourceLocation, WRONG_NUMBER_OF_GENDER_EXPRS);
        }
      }

      String meaning = attributes.get("meaning");
      String desc = attributes.get("desc");
      boolean isHidden = attributes.get("hidden").equals("true");
      return new MsgNode(
          id, sourceLocation, genderExprs, commandName, commandText, meaning, desc, isHidden);
    }
  }
}
