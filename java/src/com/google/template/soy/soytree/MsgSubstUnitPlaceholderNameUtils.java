/*
 * Copyright 2013 Google Inc.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.DataAccessNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.FieldAccessNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.ItemAccessNode;
import com.google.template.soy.exprtree.NullSafeAccessNode;
import com.google.template.soy.exprtree.VarRefNode;
import java.util.List;

/**
 * Static helpers for generating base names for msg substitution units (i.e. placeholder names and
 * plural/select vars).
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class MsgSubstUnitPlaceholderNameUtils {

  private static final SoyErrorKind COLLIDING_EXPRESSIONS =
      SoyErrorKind.of(
          "Cannot generate noncolliding base names for vars. "
              + "Colliding expressions: ''{0}'' and ''{1}''. "
              + "Add explicit base names with the ''phname'' attribute.");

  // Disallow instantiation.
  private MsgSubstUnitPlaceholderNameUtils() {}

  /**
   * Helper function to generate a base placeholder (or plural/select var) name from an expression,
   * using the naive algorithm.
   *
   * <p>If the expression is a data ref or global, then the last key (if any) is used as the base
   * placeholder name. Otherwise, the fallback name is used.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>{@code $aaaBbb -> AAA_BBB}
   *   <li>{@code $aaa_bbb -> AAA_BBB}
   *   <li>{@code $aaa.bbb -> BBB}
   *   <li>{@code $ij.aaa -> AAA}
   *   <li>{@code aaa.BBB -> BBB}
   *   <li>{@code $aaa.0 -> fallback}
   *   <li>{@code $aaa[0] -> fallback}
   *   <li>{@code $aaa[0].bbb -> BBB}
   *   <li>{@code length($aaa) -> fallback}
   *   <li>{@code $aaa + 1 -> fallback}
   * </ul>
   *
   * @param exprNode The root node for an expression.
   * @param fallbackBaseName The fallback base name.
   * @return The base placeholder (or plural/select var) name for the given expression.
   */
  public static String genNaiveBaseNameForExpr(ExprNode exprNode, String fallbackBaseName) {
    if (exprNode instanceof NullSafeAccessNode) {
      throw new IllegalStateException(
          "Msg placeholders cannot be generated for NullSafeAccessNodes; they must be created"
              + " before the NullSafeAccessPass");
    }
    if (exprNode instanceof VarRefNode) {
      return BaseUtils.convertToUpperUnderscore(
          ((VarRefNode) exprNode).getNameWithoutLeadingDollar());
    } else if (exprNode instanceof FieldAccessNode) {
      return BaseUtils.convertToUpperUnderscore(((FieldAccessNode) exprNode).getFieldName());
    } else if (exprNode instanceof GlobalNode) {
      String globalName = ((GlobalNode) exprNode).getName();
      return BaseUtils.convertToUpperUnderscore(BaseUtils.extractPartAfterLastDot(globalName));
    }

    return fallbackBaseName;
  }

  /**
   * The equivalent of {@code genNaiveBaseNameForExpr()} in our new algorithm for generating base
   * name.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>{@code $aaaBbb -> AAA_BBB}
   *   <li>{@code $aaa_bbb -> AAA_BBB}
   *   <li>{@code $aaa.bbb -> BBB}
   *   <li>{@code $ij.aaa -> AAA}
   *   <li>{@code aaa.BBB -> BBB}
   *   <li>{@code $aaa.0 -> AAA_0}
   *   <li>{@code $aaa[0] -> AAA_0}
   *   <li>{@code $aaa[0].bbb -> BBB}
   *   <li>{@code length($aaa) -> fallback}
   *   <li>{@code $aaa + 1 -> fallback}
   * </ul>
   *
   * @param exprNode The expr root of the expression to generate the shortest base name for.
   * @param fallbackBaseName The fallback base name to use if the given expression doesn't generate
   *     any base names.
   * @return The generated base name.
   */
  public static String genShortestBaseNameForExpr(ExprNode exprNode, String fallbackBaseName) {
    return Iterables.getFirst(genCandidateBaseNamesForExpr(exprNode), fallbackBaseName);
  }

  /**
   * Generates base names for all the expressions in a list, where for each expression, we use the
   * shortest candidate base name that does not collide with any of the candidate base names
   * generated from other expressions in the list. Two candidate base names are considered to
   * collide if they are identical or if one is a suffix of the other beginning after an underscore
   * character.
   *
   * <p>For example, given the expressions $userGender and $target.gender, the generated base names
   * would be USER_GENDER and TARGET_GENDER. (Even though the shortest candidate base names are
   * USER_GENDER and GENDER, the latter one is not used since it collides with the former one.)
   *
   * <p>Note: We prefer the shorter candidate base names when possible, because the translator
   * usually doesn't care about all the names. E.g. $data.actionTargets[0].personInfo.gender turns
   * into GENDER as opposed to DATA_ACTION_TARGETS_0_PERSON_INFO_GENDER, which is overkill and
   * probably more confusing. Another reason is that refactorings that change higher-level names
   * should not change messages unnecessarily. E.g. a refactoring that changes
   * $data.actionTargets[0].personInfo.gender -> $userData.actionTargets[0].personInfo.gender should
   * not change the placeholder name.
   *
   * @param exprNodes The expr nodes of the expressions to generate noncolliding base names for.
   * @param fallbackBaseName The fallback base name.
   * @param errorReporter For reporting collision errors.
   * @return The list of generated noncolliding base names.
   */
  public static List<String> genNoncollidingBaseNamesForExprs(
      List<ExprNode> exprNodes, String fallbackBaseName, ErrorReporter errorReporter) {

    int numExprs = exprNodes.size();

    // --- Compute candidate base names for each expression. ---
    List<List<String>> candidateBaseNameLists = Lists.newArrayListWithCapacity(numExprs);
    for (ExprNode exprRoot : exprNodes) {
      candidateBaseNameLists.add(genCandidateBaseNamesForExpr(exprRoot));
    }

    // --- Build a multiset of collision strings (if key has > 1 values, then it's a collision). ---
    // Note: We could combine this loop with the previous loop, but it's more readable this way.
    Multimap<String, ExprNode> collisionStrToLongestCandidatesMultimap = HashMultimap.create();
    for (int i = 0; i < numExprs; i++) {
      ExprNode exprRoot = exprNodes.get(i);
      List<String> candidateBaseNameList = candidateBaseNameLists.get(i);
      if (candidateBaseNameList.isEmpty()) {
        continue;
      }
      String longestCandidate = candidateBaseNameList.get(candidateBaseNameList.size() - 1);
      // Add longest candidate as a collision string.
      collisionStrToLongestCandidatesMultimap.put(longestCandidate, exprRoot);
      // Add all suffixes that begin after an underscore char as collision strings.
      for (int j = 0, n = longestCandidate.length(); j < n; j++) {
        if (longestCandidate.charAt(j) == '_') {
          collisionStrToLongestCandidatesMultimap.put(longestCandidate.substring(j + 1), exprRoot);
        }
      }
    }

    // --- Find the shortest noncolliding candidate base name for each expression. ---
    List<String> noncollidingBaseNames = Lists.newArrayListWithCapacity(numExprs);

    OUTER:
    for (int i = 0; i < numExprs; i++) {
      List<String> candidateBaseNameList = candidateBaseNameLists.get(i);

      if (!candidateBaseNameList.isEmpty()) {
        // Has candidates: Use the shortest candidate that doesn't collide.

        for (String candidateBaseName : candidateBaseNameList) {
          if (collisionStrToLongestCandidatesMultimap.get(candidateBaseName).size() == 1) {
            // Found candidate with 1 value in multimap, which means no collision.
            noncollidingBaseNames.add(candidateBaseName);
            continue OUTER;
          }
        }

        // Did not find any candidate with no collision.
        ExprNode exprRoot = exprNodes.get(i);
        String longestCandidate = candidateBaseNameList.get(candidateBaseNameList.size() - 1);
        ExprNode collidingExprRoot = null;
        for (ExprNode er : collisionStrToLongestCandidatesMultimap.get(longestCandidate)) {
          if (er != exprRoot) {
            collidingExprRoot = er;
            break;
          }
        }
        errorReporter.report(
            collidingExprRoot.getSourceLocation(),
            COLLIDING_EXPRESSIONS,
            exprRoot.toSourceString(),
            collidingExprRoot.toSourceString());
        return noncollidingBaseNames;
      } else {
        // No candidates: Use fallback.
        noncollidingBaseNames.add(fallbackBaseName);
      }
    }

    return noncollidingBaseNames;
  }

  /**
   * Private helper for {@code genShortestBaseNameForExpr()} and {@code
   * genNoncollidingBaseNamesForExprs()}.
   *
   * <p>Given an expression that's a data ref or a global, generates the list of all possible base
   * names, from short to long. Shortest contains only the last key. Longest contains up to the
   * first key (unless there are accesses using expressions to compute non-static keys, in which
   * case we cannot generate any more base names). If no base names can be generated for the given
   * expression (i.e. if the expression is not a data ref or global, or the last key is non-static),
   * then returns empty list.
   *
   * <p>For example, given $aaa[0].bbb.cccDdd, generates the list ["CCC_DDD", "BBB_CCC_DDD",
   * "AAA_0_BBB_CCC_DDD"]. One the other hand, given $aaa['xxx'], generates the empty list (because
   * ['xxx'] parses to a DataRefAccessExprNode).
   *
   * @param exprNode The expr root of the expression to generate all candidate base names for.
   * @return The list of all candidate base names, from shortest to longest.
   */
  @VisibleForTesting
  static ImmutableList<String> genCandidateBaseNamesForExpr(ExprNode exprNode) {
    if (exprNode instanceof NullSafeAccessNode) {
      throw new IllegalStateException(
          "Msg placeholders cannot be generated for NullSafeAccessNodes; they must be created"
              + " before the NullSafeAccessPass");
    }
    if (exprNode instanceof VarRefNode || exprNode instanceof DataAccessNode) {
      ImmutableList.Builder<String> baseNames = ImmutableList.builder();
      String baseName = null;

      while (exprNode != null) {
        String nameSegment = null;
        if (exprNode instanceof VarRefNode) {
          nameSegment = ((VarRefNode) exprNode).getNameWithoutLeadingDollar();
          exprNode = null;
        } else if (exprNode instanceof FieldAccessNode) {
          FieldAccessNode fieldAccess = (FieldAccessNode) exprNode;
          nameSegment = fieldAccess.getFieldName();
          exprNode = fieldAccess.getBaseExprChild();
        } else if (exprNode instanceof ItemAccessNode) {
          ItemAccessNode itemAccess = (ItemAccessNode) exprNode;
          exprNode = itemAccess.getBaseExprChild();
          if (itemAccess.getKeyExprChild() instanceof IntegerNode) {
            // Prefix with index, but don't add to baseNames list since it's not a valid ident.
            IntegerNode keyValue = (IntegerNode) itemAccess.getKeyExprChild();
            if (keyValue.getValue() < 0) {
              // Stop if we encounter an invalid key.
              break;
            }
            nameSegment = Long.toString(keyValue.getValue());
            baseName =
                BaseUtils.convertToUpperUnderscore(nameSegment)
                    + ((baseName != null) ? "_" + baseName : "");
            continue;
          } else {
            break; // Stop if we encounter a non-static key
          }
        } else {
          break; // Stop if we encounter an expression that is not representable as a name.
        }

        baseName =
            BaseUtils.convertToUpperUnderscore(nameSegment)
                + ((baseName != null) ? "_" + baseName : "");
        baseNames.add(baseName); // new candidate base name whenever we encounter a key
      }
      return baseNames.build();
    }

    if (exprNode instanceof GlobalNode) {
      String[] globalNameParts = ((GlobalNode) exprNode).getName().split("\\.");

      ImmutableList.Builder<String> baseNames = ImmutableList.builder();
      String baseName = null;
      for (int i = globalNameParts.length - 1; i >= 0; i--) {
        baseName =
            BaseUtils.convertToUpperUnderscore(globalNameParts[i])
                + ((baseName != null) ? "_" + baseName : "");
        baseNames.add(baseName);
      }
      return baseNames.build();
    }

    // We don't handle expressions other than data refs and globals.
    return ImmutableList.of();
  }
}
