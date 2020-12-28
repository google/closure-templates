/*
 * Copyright 2012 Google Inc.
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

package com.google.template.soy.passes;

import static com.google.common.base.Preconditions.checkState;
import static com.google.template.soy.soytree.MsgSubstUnitPlaceholderNameUtils.genNaiveBaseNameForExpr;
import static com.google.template.soy.soytree.MsgSubstUnitPlaceholderNameUtils.genNoncollidingBaseNamesForExprs;
import static com.google.template.soy.soytree.MsgSubstUnitPlaceholderNameUtils.genShortestBaseNameForExpr;
import static java.util.stream.Collectors.toList;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ErrorReporter.Checkpoint;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.MsgPlaceholderNode;
import com.google.template.soy.soytree.MsgPluralNode;
import com.google.template.soy.soytree.MsgSelectCaseNode;
import com.google.template.soy.soytree.MsgSelectDefaultNode;
import com.google.template.soy.soytree.MsgSelectNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Visitor for rewriting 'msg' nodes with 'genders' attribute into 'msg' nodes with one or more
 * levels of 'select'.
 *
 */
final class RewriteGenderMsgsPass implements CompilerFilePass {

  private static final SoyErrorKind MORE_THAN_THREE_TOTAL_GENDERS =
      SoyErrorKind.of(
          "A message can only contain at most 3 genders between the ''genders'' attribute and "
              + "''select'' command.");

  private static final SoyErrorKind MORE_THAN_TWO_GENDER_EXPRS_WITH_PLURAL =
      SoyErrorKind.of(
          "A msg with ''plural'' can contain at most 2 gender expressions between the "
              + "''genders'' attribute and ''select'' command (otherwise, combinatorial explosion "
              + "would cause a gigantic generated message).");

  /** Fallback base select var name. */
  private static final String FALLBACK_BASE_SELECT_VAR_NAME = "GENDER";

  private final ErrorReporter errorReporter;

  RewriteGenderMsgsPass(ErrorReporter errorReporter) {
    this.errorReporter = Preconditions.checkNotNull(errorReporter);
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (MsgNode msg : SoyTreeUtils.getAllNodesOfType(file, MsgNode.class)) {
      maybeRewriteNode(msg, nodeIdGen);
    }
  }

  private void maybeRewriteNode(MsgNode msg, IdGenerator nodeIdGen) {
    List<ExprRootNode> genderExprs = msg.getAndRemoveGenderExprs();
    if (genderExprs == null) {
      return; // not a msg that this pass should rewrite
    }

    // ------ Do the rewrite. ------

    // Note: We process the genders in reverse order so that the first listed gender will end up
    // being the outermost 'select' level.
    genderExprs = Lists.reverse(genderExprs);

    Checkpoint checkpoint = errorReporter.checkpoint();
    List<String> baseSelectVarNames =
        genNoncollidingBaseNamesForExprs(
            ExprRootNode.unwrap(genderExprs), FALLBACK_BASE_SELECT_VAR_NAME, errorReporter);
    if (errorReporter.errorsSince(checkpoint)) {
      return; // To prevent an IndexOutOfBoundsException below.
    }

    for (int i = 0; i < genderExprs.size(); i++) {
      ExprRootNode genderExpr = genderExprs.get(i);
      String baseSelectVarName = baseSelectVarNames.get(i);

      // Check whether the generated base name would be the same (both for the old naive algorithm
      // and the new algorithm). If so, then there's no need to specify the baseSelectVarName.
      if (genNaiveBaseNameForExpr(genderExpr.getRoot(), FALLBACK_BASE_SELECT_VAR_NAME)
              .equals(baseSelectVarName)
          && genShortestBaseNameForExpr(genderExpr.getRoot(), FALLBACK_BASE_SELECT_VAR_NAME)
              .equals(baseSelectVarName)) {
        baseSelectVarName = null;
      }

      splitMsgForGender(msg, genderExpr, baseSelectVarName, nodeIdGen);
    }

    // ------ Verify from the re-written msg that gender restrictions are followed. ------

    checkExceedsMaxGenders((MsgSelectNode) msg.getChild(0), 1);
  }

  /**
   * Helper to split a msg for gender, by adding a 'select' node and cloning the msg's contents into
   * all 3 cases of the 'select' node ('female'/'male'/default).
   *
   * @param msg The message to split.
   * @param genderExpr The expression for the gender value.
   * @param baseSelectVarName The base select var name to use, or null if it should be generated
   *     from the gender expression.
   * @param nodeIdGen The id generator for the current tree
   */
  private static void splitMsgForGender(
      MsgNode msg,
      ExprRootNode genderExpr,
      @Nullable String baseSelectVarName,
      IdGenerator nodeIdGen) {

    List<StandaloneNode> origChildren = ImmutableList.copyOf(msg.getChildren());
    msg.clearChildren();

    MsgSelectCaseNode femaleCase =
        new MsgSelectCaseNode(
            nodeIdGen.genId(), msg.getSourceLocation(), msg.getOpenTagLocation(), "female");
    femaleCase.addChildren(origChildren);
    MsgSelectCaseNode maleCase =
        new MsgSelectCaseNode(
            nodeIdGen.genId(), msg.getSourceLocation(), msg.getOpenTagLocation(), "male");
    maleCase.addChildren(copyWhilePresevingPlaceholderIdentity(origChildren, nodeIdGen));
    MsgSelectDefaultNode defaultCase =
        new MsgSelectDefaultNode(
            nodeIdGen.genId(), msg.getSourceLocation(), msg.getOpenTagLocation());
    defaultCase.addChildren(copyWhilePresevingPlaceholderIdentity(origChildren, nodeIdGen));

    MsgSelectNode selectNode =
        MsgSelectNode.fromGenderExpr(
            nodeIdGen.genId(),
            msg.getSourceLocation(),
            msg.getOpenTagLocation(),
            genderExpr,
            baseSelectVarName);
    selectNode.addChild(femaleCase);
    selectNode.addChild(maleCase);
    selectNode.addChild(defaultCase);

    msg.addChild(selectNode);
  }

  /**
   * Copies the nodes with new ids but preserves the placeholder identity. This is important
   * because, while we don't have a great algorithm for telling that certain placeholders are
   * actually identical, in the special case of {@code genders=} messages we do know that
   * placeholders are equivalent since we are actually cloning messages into multiple cases.
   */
  private static List<StandaloneNode> copyWhilePresevingPlaceholderIdentity(
      List<StandaloneNode> nodes, IdGenerator nodeIdGen) {
    List<StandaloneNode> copy = SoyTreeUtils.cloneListWithNewIds(nodes, nodeIdGen);
    List<MsgPlaceholderNode> placeholders = allPlaceholders(nodes);
    List<MsgPlaceholderNode> copyPlaceholders = allPlaceholders(copy);
    checkState(copyPlaceholders.size() == placeholders.size());
    for (int i = 0; i < copyPlaceholders.size(); i++) {
      copyPlaceholders.get(i).copySamenessKey(placeholders.get(i));
    }
    return copy;
  }

  private static List<MsgPlaceholderNode> allPlaceholders(List<StandaloneNode> nodes) {
    return nodes.stream()
        .flatMap(node -> SoyTreeUtils.allNodesOfType(node, MsgPlaceholderNode.class))
        .collect(toList());
  }

  /**
   * Helper to verify that a rewritten soy msg tree does not exceed the restriction on number of
   * total genders allowed (2 if includes plural, 3 otherwise).
   *
   * @param selectNode The select node to start searching from.
   * @param depth The current depth of the select node.
   * @return Whether the tree is valid.
   */
  private boolean checkExceedsMaxGenders(MsgSelectNode selectNode, int depth) {
    for (int caseNum = 0; caseNum < selectNode.numChildren(); caseNum++) {
      if (selectNode.getChild(caseNum).numChildren() > 0) {
        StandaloneNode caseNodeChild = selectNode.getChild(caseNum).getChild(0);
        // Plural cannot contain plurals or selects, so no need to recurse further.
        if (caseNodeChild instanceof MsgPluralNode && depth >= 3) {
          errorReporter.report(
              selectNode.getSourceLocation(), MORE_THAN_TWO_GENDER_EXPRS_WITH_PLURAL);
          return false;
        }
        if (caseNodeChild instanceof MsgSelectNode) {
          if (depth >= 3) {
            errorReporter.report(selectNode.getSourceLocation(), MORE_THAN_THREE_TOTAL_GENDERS);
            return false;
          } else {
            boolean validSubtree = checkExceedsMaxGenders((MsgSelectNode) caseNodeChild, depth + 1);
            if (!validSubtree) {
              return false;
            }
          }
        }
      }
    }
    return true;
  }
}
