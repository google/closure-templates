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
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.template.soy.soytree.CommandTagAttribute.MISSING_ATTRIBUTE;
import static com.google.template.soy.soytree.CommandTagAttribute.UNSUPPORTED_ATTRIBUTE_KEY;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.basetree.CopyState.Listener;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprEquivalence;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.CommandTagAttribute.CommandTagAttributesHolder;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.MsgBlockNode;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Pattern;
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
    implements ExprHolderNode, MsgBlockNode, CommandTagAttributesHolder {

  private static final SoyErrorKind WRONG_NUMBER_OF_GENDER_EXPRS =
      SoyErrorKind.of("Attribute ''genders'' should contain 1-3 expressions.");

  private static final SoyErrorKind BASE_NAME_COLLISION_SINGLE =
      SoyErrorKind.of(
          "Placeholder ''{0}'' collision with element at {1},"
              + " please collapse them into a single Soy variable,"
              + " or change `phname` of at last one to avoid conflict (go/soy-msg#msg).");

  private static final SoyErrorKind BASE_NAME_COLLISION_MULTIPLE =
      SoyErrorKind.of(
          "Placeholder ''{0}'' collision with element at {1},"
              + " please collapse them into a single Soy variable,"
              + " or change `phname`  of at last one to avoid conflict,"
              + " ditto for these {2} (go/soy-msg#msg).");

  private static final SoyErrorKind INCOMPATIBLE_PLACEHOLDER_EXAMPLES =
      SoyErrorKind.of(
          "The example set on this placeholder is incompatible with other examples for "
              + "the same placeholder. If a placeholder occurs multiple times in a msg, then all "
              + "examples must be the same.");

  // Note that the first set of whitespace is reluctant.
  private static final Pattern LINE_BOUNDARY_PATTERN = Pattern.compile("\\s*?(\\n|\\r)\\s*");

  /** We don't use different content types. It may be a historical artifact in the TC. */
  private static final String DEFAULT_CONTENT_TYPE = "text/html";

  @VisibleForTesting
  static final class SubstUnitInfo {

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
    public final ImmutableMap<MsgSubstUnitNode, MessagePlaceholder.Summary> nodeToVarNameMap;

    public SubstUnitInfo(
        Map<String, MsgSubstUnitNode> varNameToRepNodeMap,
        Map<MsgSubstUnitNode, MessagePlaceholder.Summary> nodeToVarNameMap) {
      this.varNameToRepNodeMap = ImmutableMap.copyOf(varNameToRepNodeMap);
      this.nodeToVarNameMap = ImmutableMap.copyOf(nodeToVarNameMap);
    }

    public SubstUnitInfo copy(Map<MsgSubstUnitNode, MsgSubstUnitNode> oldToNew) {
      Function<MsgSubstUnitNode, MsgSubstUnitNode> oldToNewFunction = Functions.forMap(oldToNew);
      ImmutableMap.Builder<MsgSubstUnitNode, MessagePlaceholder.Summary> builder =
          ImmutableMap.builder();
      for (Map.Entry<MsgSubstUnitNode, MessagePlaceholder.Summary> entry :
          nodeToVarNameMap.entrySet()) {
        builder.put(oldToNew.get(entry.getKey()), entry.getValue());
      }
      return new SubstUnitInfo(
          Maps.transformValues(varNameToRepNodeMap, oldToNewFunction),
          builder.build());
    }
  }

  /** The list of expressions for gender values. Null after rewriting. */
  @Nullable private ImmutableList<ExprRootNode> genderExprs;

  /** The meaning string if set, otherwise null (usually null). */
  @Nullable private final String meaning;

  /** The description string for translators. Required. */
  private final String desc;

  /** Whether the message should be added as 'hidden' in the TC. */
  private final boolean isHidden;

  /** Used for formatting */
  private final List<CommandTagAttribute> attributes;

  private final SourceLocation openTagLocation;

  /** The string representation of genderExprs, for debugging. */
  @Nullable private final String genderExprsString;

  /** The substitution unit info (var name mappings, or null if not yet generated). */
  @Nullable private SubstUnitInfo substUnitInfo = null;

  /** The EscapingMode where this message is used. */
  @Nullable private EscapingMode escapingMode = null;

  /** The optional alternate id to be used if a translation for the msg id is missing. */
  private final OptionalLong alternateId;

  public MsgNode(
      int id,
      SourceLocation location,
      SourceLocation openTagLocation,
      String commandName,
      List<CommandTagAttribute> attributes,
      ErrorReporter errorReporter) {
    super(id, location, openTagLocation, commandName);

    String meaning = null;
    String desc = null;
    boolean hidden = false;
    ImmutableList<ExprRootNode> genders = null;
    OptionalLong alternateId = OptionalLong.empty();

    this.attributes = attributes;
    this.openTagLocation = openTagLocation;
    for (CommandTagAttribute attr : attributes) {
      String name = attr.getName().identifier();

      switch (attr.getName().identifier()) {
        case "meaning":
          meaning = attr.getValue();
          // join multi-line meaning strings
          // While weird, it would be difficult to stop doing this.  This is for compatibility with
          // the old parser which would always strip newlines from command attributes.
          meaning = LINE_BOUNDARY_PATTERN.matcher(meaning).replaceAll(" ");
          break;
        case "desc":
          desc = attr.getValue();
          break;
        case "hidden":
          hidden = attr.valueAsEnabled(errorReporter);
          break;
        case "genders":
          genders = attr.valueAsExprList();
          if (genders.isEmpty() || genders.size() > 3) {
            errorReporter.report(attr.getValueLocation(), WRONG_NUMBER_OF_GENDER_EXPRS);
          }
          break;
        case "alternateId":
          alternateId = attr.valueAsOptionalLong(errorReporter);
          break;
        default:
          errorReporter.report(
              attr.getName().location(),
              UNSUPPORTED_ATTRIBUTE_KEY,
              name,
              commandName,
              ImmutableList.of("meaning", "desc", "hidden", "genders", "alternateId"));
          break;
      }
    }

    if (desc == null) {
      errorReporter.report(location, MISSING_ATTRIBUTE, "desc", commandName);
      desc = "";
    }

    this.meaning = meaning;
    this.desc = desc;
    this.isHidden = hidden;
    this.genderExprs = genders;
    this.alternateId = alternateId;

    // Calculate eagerly so we still have this even after getAndRemoveGenderExprs() is called.
    this.genderExprsString = (genders != null) ? SoyTreeUtils.toSourceString(genders) : null;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private MsgNode(MsgNode orig, CopyState copyState) {
    super(orig, copyState);
    this.openTagLocation = orig.openTagLocation;
    this.attributes =
        orig.attributes.stream().map(c -> c.copy(copyState)).collect(toImmutableList());
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
    if (orig.substUnitInfo != null) {
      // we need to fix references in the substUnitInfo
      final IdentityHashMap<MsgSubstUnitNode, MsgSubstUnitNode> oldToNew = new IdentityHashMap<>();
      // NOTE: because we only hold references to our children, and all our children will have been
      // copied by the time 'super' returns, we can count on these listeners firing synchronously.
      for (final MsgSubstUnitNode old : orig.substUnitInfo.nodeToVarNameMap.keySet()) {
        copyState.registerRefListener(
            old,
            new Listener<MsgSubstUnitNode>() {
              @Override
              public void newVersion(MsgSubstUnitNode newObject) {
                oldToNew.put(old, newObject);
              }
            });
      }
      this.substUnitInfo = orig.substUnitInfo.copy(oldToNew);
    }
    this.genderExprsString = orig.genderExprsString;
    this.escapingMode = orig.escapingMode;
    this.alternateId = orig.alternateId;
  }

  /** The location of the {msg ...} */
  @Override
  public SourceLocation getOpenTagLocation() {
    return this.openTagLocation;
  }

  @Override
  public Kind getKind() {
    return Kind.MSG_NODE;
  }

  @Override
  public List<CommandTagAttribute> getAttributes() {
    return attributes;
  }

  /**
   * Returns the list of expressions for gender values and sets that field to null.
   *
   * <p>Note that this node's command text will still contain the substring genders="...". We think
   * this is okay since the command text is only used for reporting errors (in fact, it might be
   * good as a reminder of how the msg was originally written).
   */
  @Nullable
  public List<ExprRootNode> getAndRemoveGenderExprs() {
    List<ExprRootNode> genderExprs = this.genderExprs;
    this.genderExprs = null;
    return genderExprs;
  }

  public void calculateSubstitutionInfo(ErrorReporter reporter) {
    if (substUnitInfo == null) {
      substUnitInfo = genSubstUnitInfo(this, reporter);
    } else {
      throw new IllegalStateException("calculateSubstitutionInfo has already been called.");
    }
  }

  @VisibleForTesting
  SubstUnitInfo getSubstUnitInfo() {
    if (substUnitInfo == null) {
      throw new IllegalStateException("calculateSubstitutionInfo hasn't been called yet.");
    }
    return substUnitInfo;
  }

  public EscapingMode getEscapingMode() {
    return escapingMode;
  }

  public void setEscapingMode(EscapingMode escapingMode) {
    this.escapingMode = escapingMode;
  }

  @Override
  public ImmutableList<ExprRootNode> getExprList() {
    if (genderExprs != null) {
      return ImmutableList.copyOf(genderExprs);
    }
    return ImmutableList.of();
  }

  /** Returns the meaning string if set, otherwise null (usually null). */
  @Nullable
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

  /** Returns the optional alternate id. */
  public OptionalLong getAlternateId() {
    return alternateId;
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
    checkState(numChildren() > 0);
    return numChildren() == 1 && (getChild(0) instanceof MsgSelectNode);
  }

  /** Returns whether this is a plural message. */
  public boolean isPluralMsg() {
    checkState(numChildren() > 0);
    return numChildren() == 1 && (getChild(0) instanceof MsgPluralNode);
  }

  /** Returns whether this is a raw text message. */
  public boolean isRawTextMsg() {
    checkState(numChildren() > 0);
    return numChildren() == 1 && (getChild(0) instanceof RawTextNode);
  }

  /**
   * This class lazily allocates some datastructures for accessing data about nested placeholders.
   * This method ensures that generation and access to that data structure hasn't happened yet. This
   * is important if passes are adding new placeholder nodes.
   */
  public void ensureSubstUnitInfoHasNotBeenAccessed() {
    if (substUnitInfo != null) {
      throw new IllegalStateException("Substitution info has already been accessed.");
    }
  }

  /**
   * Gets the representative placeholder node for a given placeholder name.
   *
   * @param placeholderName The placeholder name.
   * @return The representative placeholder node for the given placeholder name.
   */
  public MsgPlaceholderNode getRepPlaceholderNode(String placeholderName) {
    return (MsgPlaceholderNode) getSubstUnitInfo().varNameToRepNodeMap.get(placeholderName);
  }

  /**
   * Gets the placeholder name for a given placeholder node.
   *
   * @param placeholderNode The placeholder node.
   * @return The placeholder name for the given placeholder node.
   */
  public MessagePlaceholder.Summary getPlaceholder(MsgPlaceholderNode placeholderNode) {
    return getSubstUnitInfo().nodeToVarNameMap.get(placeholderNode);
  }

  /**
   * Gets the representative plural node for a given plural variable name.
   *
   * @param pluralVarName The plural variable name.
   * @return The representative plural node for the given plural variable name.
   */
  public MsgPluralNode getRepPluralNode(String pluralVarName) {
    return (MsgPluralNode) getSubstUnitInfo().varNameToRepNodeMap.get(pluralVarName);
  }

  /**
   * Gets the variable name associated with a given plural node.
   *
   * @param pluralNode The plural node.
   * @return The plural variable name for the given plural node.
   */
  public String getPluralVarName(MsgPluralNode pluralNode) {
    return getSubstUnitInfo().nodeToVarNameMap.get(pluralNode).name();
  }

  /**
   * Gets the representative select node for a given select variable name.
   *
   * @param selectVarName The select variable name.
   * @return The representative select node for the given select variable name.
   */
  public MsgSelectNode getRepSelectNode(String selectVarName) {
    return (MsgSelectNode) getSubstUnitInfo().varNameToRepNodeMap.get(selectVarName);
  }

  /**
   * Gets the variable name associated with a given select node.
   *
   * @param selectNode The select node.
   * @return The select variable name for the given select node.
   */
  public String getSelectVarName(MsgSelectNode selectNode) {
    return getSubstUnitInfo().nodeToVarNameMap.get(selectNode).name();
  }

  /** Getter for the generated map from substitution unit var name to representative node. */
  public ImmutableMap<String, MsgSubstUnitNode> getVarNameToRepNodeMap() {
    return getSubstUnitInfo().varNameToRepNodeMap;
  }

  @Override
  public String getCommandText() {
    StringBuilder commandText = new StringBuilder();
    if (meaning != null) {
      commandText.append(" meaning=\"").append(meaning).append('"');
    }
    if (desc != null) {
      commandText.append(" desc=\"").append(desc).append('"');
    }
    if (isHidden) {
      commandText.append(" hidden=\"").append(isHidden).append('"');
    }
    if (genderExprsString != null) {
      commandText.append(" genders=\"").append(genderExprsString).append('"');
    }
    if (alternateId.isPresent()) {
      commandText.append(" alternateId=\"").append(alternateId).append('"');
    }
    return commandText.toString().trim();
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
  private static SubstUnitInfo genSubstUnitInfo(MsgNode msgNode, ErrorReporter errorReporter) {
    return genFinalSubstUnitInfoMapsHelper(
        RepresentativeNodes.createFromNode(msgNode, errorReporter), errorReporter);
  }

  /**
   * Private helper class for genSubstUnitInfo(). Determines representative nodes and builds
   * preliminary maps.
   *
   * <p>If there are multiple nodes in the message that should share the same var name, then the
   * first such node encountered becomes the "representative node" for the group ("repNode" in
   * variable names). The rest of the nodes in the group are non-representative ("nonRepNode").
   *
   * <p>The baseNameToRepNodesMap is a multimap from each base name to its list of representative
   * nodes (they all generate the same base var name, but should not have the same final var name).
   * If we encounter a non-representative node, then we insert it into repNodeToNonRepNodesMap,
   * mapping from its corresponding representative node.
   *
   * <p>The base var names are preliminary because some of the final var names will be the base
   * names plus a unique suffix.
   */
  @AutoValue
  abstract static class RepresentativeNodes {
    static RepresentativeNodes createFromNode(MsgNode msgNode, ErrorReporter reporter) {
      ListMultimap<String, MsgSubstUnitNode> baseNameToRepNodesMap = LinkedListMultimap.create();
      ListMultimap<MsgSubstUnitNode, MsgSubstUnitNode> repNodeToNonRepNodesMap =
          LinkedListMultimap.create();
      Map<MsgSubstUnitNode, String> repNodeToExample = new HashMap<>();
      Deque<SoyNode> traversalQueue = new ArrayDeque<>();
      ExprEquivalence exprEquivalence = new ExprEquivalence();

      // Seed the traversal queue with the direct children of this MsgNode.
      // NOTE: the placeholder name selection algorithm depends on the order of iteration in these
      // loops.  So we cannot trivially switch the SoyTreeUtils.visitAllNodes or getAllNodesOfType
      // since the iteration order is either undefined or slightly different.
      for (SoyNode child : msgNode.getChildren()) {
        maybeEnqueue(traversalQueue, child);
      }

      while (!traversalQueue.isEmpty()) {
        SoyNode node = traversalQueue.remove();

        if (node instanceof MsgSelectNode) {
          maybeEnqueueMsgNode(traversalQueue, (MsgSelectNode) node);
        } else if (node instanceof MsgPluralNode) {
          maybeEnqueueMsgNode(traversalQueue, (MsgPluralNode) node);
        } else if (node instanceof VeLogNode) {
          VeLogNode velogNode = (VeLogNode) node;
          for (SoyNode grandchild : velogNode.getChildren()) {
            maybeEnqueue(traversalQueue, grandchild);
          }
        }

        if (node instanceof MsgSubstUnitNode) {
          MsgSubstUnitNode substUnit = (MsgSubstUnitNode) node;
          String baseName = substUnit.getPlaceholder().name();
          if (!baseNameToRepNodesMap.containsKey(baseName)) {
            // Case 1: First occurrence of this base name.
            baseNameToRepNodesMap.put(baseName, substUnit);
            getPhExample(substUnit).ifPresent(example -> repNodeToExample.put(substUnit, example));
          } else {
            boolean isNew = true;
            for (MsgSubstUnitNode repNode : baseNameToRepNodesMap.get(baseName)) {
              if (substUnit.shouldUseSameVarNameAs(repNode, exprEquivalence)) {
                // Case 2: Should use same var name as another node we've seen.
                repNodeToNonRepNodesMap.put(repNode, substUnit);
                String example = checkCompatibleExamples(substUnit, repNode, reporter);
                if (example != null) {
                  repNodeToExample.put(repNode, example);
                }
                isNew = false;
                break;
              }
            }
            if (isNew) {
              // Case 3: New representative node that has the same base name as another node we've
              // seen, but should not use the same var name.
              baseNameToRepNodesMap.put(baseName, substUnit);
              getPhExample(substUnit)
                  .ifPresent(example -> repNodeToExample.put(substUnit, example));
            }
          }
        }
      }
      return new AutoValue_MsgNode_RepresentativeNodes(
          ImmutableListMultimap.copyOf(baseNameToRepNodesMap),
          ImmutableListMultimap.copyOf(repNodeToNonRepNodesMap),
          ImmutableMap.copyOf(Maps.filterValues(repNodeToExample, Objects::nonNull)));
    }

    private static void maybeEnqueue(Deque<SoyNode> traversalQueue, SoyNode child) {
      if (child instanceof MsgSubstUnitNode || child instanceof VeLogNode) {
        traversalQueue.add(child);
      }
    }

    private static void maybeEnqueueMsgNode(
        Deque<SoyNode> traversalQueue, ParentSoyNode<CaseOrDefaultNode> node) {
      for (CaseOrDefaultNode child : node.getChildren()) {
        for (SoyNode grandchild : child.getChildren()) {
          maybeEnqueue(traversalQueue, grandchild);
        }
      }
    }

    /**
     * Examples are compatible if either only one instance sets an example, or if they set the same
     * example.
     */
    @Nullable
    private static String checkCompatibleExamples(
        MsgSubstUnitNode left, MsgSubstUnitNode right, ErrorReporter reporter) {
      Optional<String> leftExample = getPhExample(left);
      Optional<String> rightExample = getPhExample(right);
      if (leftExample.isPresent()
          && rightExample.isPresent()
          && !leftExample.equals(rightExample)) {
        reporter.report(left.getSourceLocation(), INCOMPATIBLE_PLACEHOLDER_EXAMPLES);
        return null;
      }
      return leftExample.orElse(rightExample.orElse(null));
    }

    abstract ImmutableListMultimap<String, MsgSubstUnitNode> baseNameToRepNodesMap();

    abstract ImmutableListMultimap<MsgSubstUnitNode, MsgSubstUnitNode> repNodeToNonRepNodesMap();

    abstract ImmutableMap<MsgSubstUnitNode, String> repNodeToPhExample();
  }

  /** @return Location of `phname` attribute value, if found; otherwise, node location. */
  private static SourceLocation phnameLocation(MsgSubstUnitNode node) {
    return node.getPlaceholder().userSuppliedNameLocation().orElse(node.getSourceLocation());
  }

  /**
   * Private helper for genSubstUnitInfo(). Generates the final SubstUnitInfo given preliminary
   * maps.
   *
   * @return The generated SubstUnitInfo.
   */
  private static SubstUnitInfo genFinalSubstUnitInfoMapsHelper(
      RepresentativeNodes representativeNodes, ErrorReporter errorReporter) {

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

    for (String baseName : representativeNodes.baseNameToRepNodesMap().keySet()) {
      List<MsgSubstUnitNode> nodesWithSameBaseName =
          representativeNodes.baseNameToRepNodesMap().get(baseName);
      checkState(
          !nodesWithSameBaseName.isEmpty(), "%s not found in `baseNameToRepNodesMap`.", baseName);
      if (nodesWithSameBaseName.size() == 1) {
        // Expected success case, one node per base name.
        substUnitVarNameToRepNodeMap.put(baseName, nodesWithSameBaseName.get(0));
      } else {
        // Case 2: Multiple nodes generate this base name. Need number suffixes.
        int nextSuffix = 1;
        for (int i = 0; i < nodesWithSameBaseName.size(); ++i) {
          MsgSubstUnitNode repNode = nodesWithSameBaseName.get(i);
          String newName;
          do {
            newName = baseName + "_" + nextSuffix;
            ++nextSuffix;
          } while (representativeNodes.baseNameToRepNodesMap().containsKey(newName));
          substUnitVarNameToRepNodeMap.put(newName, repNode);
          if (repNode.getPlaceholder().userSuppliedName().isPresent()) {
            MsgSubstUnitNode exampleCollidingNode = nodesWithSameBaseName.get(i == 0 ? 1 : 0);
            if (representativeNodes.repNodeToNonRepNodesMap().containsKey(repNode)) {
              errorReporter.report(
                  phnameLocation(repNode),
                  BASE_NAME_COLLISION_MULTIPLE,
                  baseName,
                  phnameLocation(exampleCollidingNode).toLineColumnString(),
                  representativeNodes.repNodeToNonRepNodesMap().get(repNode).stream()
                      .map(node -> phnameLocation(node).toLineColumnString())
                      .collect(toImmutableList()));
            } else {
              errorReporter.report(
                  phnameLocation(repNode),
                  BASE_NAME_COLLISION_SINGLE,
                  baseName,
                  phnameLocation(exampleCollidingNode).toLineColumnString());
            }
          }
        }
      }
    }

    // ------ Step 2: Create map of every node to its var name. ------

    Map<MsgSubstUnitNode, MessagePlaceholder.Summary> substUnitNodeToVarNameMap =
        new LinkedHashMap<>();

    // Reverse the map of names to representative nodes.
    for (Map.Entry<String, MsgSubstUnitNode> entry : substUnitVarNameToRepNodeMap.entrySet()) {
      substUnitNodeToVarNameMap.put(
          entry.getValue(),
          MessagePlaceholder.Summary.create(
              entry.getKey(),
              Optional.ofNullable(representativeNodes.repNodeToPhExample().get(entry.getValue()))));
    }

    // Add mappings for the non-representative nodes.
    for (Map.Entry<MsgSubstUnitNode, MsgSubstUnitNode> entry :
        representativeNodes.repNodeToNonRepNodesMap().entries()) {
      MsgSubstUnitNode repNode = entry.getKey();
      MsgSubstUnitNode nonRepNode = entry.getValue();
      substUnitNodeToVarNameMap.put(nonRepNode, substUnitNodeToVarNameMap.get(repNode));
    }

    return new SubstUnitInfo(
        ImmutableMap.copyOf(substUnitVarNameToRepNodeMap),
        ImmutableMap.copyOf(substUnitNodeToVarNameMap));
  }

  private static Optional<String> getPhExample(MsgSubstUnitNode node) {
    if (node instanceof MsgPlaceholderNode) {
      return ((MsgPlaceholderNode) node).getPlaceholder().example();
    }
    return Optional.empty();
  }
}
