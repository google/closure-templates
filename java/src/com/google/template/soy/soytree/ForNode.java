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
import com.google.common.collect.Lists;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.exprparse.ExprParseUtils;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.SoyNode.ConditionalBlockNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.LocalVarBlockNode;
import com.google.template.soy.soytree.SoyNode.LoopNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyNode.StatementNode;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Node representing a 'for' statement.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class ForNode extends AbstractBlockCommandNode
    implements StandaloneNode, StatementNode, ConditionalBlockNode, LoopNode, ExprHolderNode,
    LocalVarBlockNode {


  /** Regex pattern for the command text. */
  // 2 capturing groups: local var name, arguments to range()
  private static final Pattern COMMAND_TEXT_PATTERN =
      Pattern.compile("( [$] \\w+ ) \\s+ in \\s+ range[(] \\s* (.*) \\s* [)]",
                      Pattern.COMMENTS | Pattern.DOTALL);


  /** The local (loop) variable name. */
  private final String varName;

  /** The texts of the individual range args (sort of canonicalized). */
  private final ImmutableList<String> rangeArgTexts;

  /** The parsed range args. */
  private final ImmutableList<ExprRootNode<?>> rangeArgs;


  /**
   * @param id The id for this node.
   * @param commandText The command text.
   * @throws SoySyntaxException If a syntax error is found.
   */
  public ForNode(int id, String commandText) throws SoySyntaxException {
    super(id, "for", commandText);

    Matcher matcher = COMMAND_TEXT_PATTERN.matcher(commandText);
    if (!matcher.matches()) {
      throw SoySyntaxException.createWithoutMetaInfo(
          "Invalid 'for' command text \"" + commandText + "\".");
    }

    varName = ExprParseUtils.parseVarNameElseThrowSoySyntaxException(
        matcher.group(1), "Invalid variable name in 'for' command text \"" + commandText + "\".");

    List<ExprRootNode<?>> tempRangeArgs = ExprParseUtils.parseExprListElseThrowSoySyntaxException(
        matcher.group(2),
        "Invalid range specification in 'for' command text \"" + commandText + "\".");
    if (tempRangeArgs.size() > 3) {
      throw SoySyntaxException.createWithoutMetaInfo(
          "Invalid range specification in 'for' command text \"" + commandText + "\".");
    }
    rangeArgs = ImmutableList.copyOf(tempRangeArgs);

    List<String> tempRangeArgTexts = Lists.newArrayList();
    for (ExprNode rangeArg : rangeArgs) {
      tempRangeArgTexts.add(rangeArg.toSourceString());
    }
    rangeArgTexts = ImmutableList.copyOf(tempRangeArgTexts);
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected ForNode(ForNode orig) {
    super(orig);
    this.varName = orig.varName;
    this.rangeArgTexts = orig.rangeArgTexts;  // safe to reuse (immutable)
    List<ExprRootNode<?>> tempRangeArgs =
        Lists.newArrayListWithCapacity(orig.rangeArgs.size());
    for (ExprRootNode<?> origRangeArg : orig.rangeArgs) {
      tempRangeArgs.add(origRangeArg.clone());
    }
    this.rangeArgs = ImmutableList.copyOf(tempRangeArgs);
  }


  @Override public Kind getKind() {
    return Kind.FOR_NODE;
  }


  @Override public String getVarName() {
    return varName;
  }


  /** Returns the texts of the individual range args (sort of canonicalized). */
  public List<String> getRangeArgTexts() {
    return rangeArgTexts;
  }


  /** Returns the parsed range args. */
  public List<ExprRootNode<?>> getRangeArgs() {
    return rangeArgs;
  }


  @Override public List<ExprUnion> getAllExprUnions() {
    return ExprUnion.createList(rangeArgs);
  }


  @Override public BlockNode getParent() {
    return (BlockNode) super.getParent();
  }


  @Override public ForNode clone() {
    return new ForNode(this);
  }

}
