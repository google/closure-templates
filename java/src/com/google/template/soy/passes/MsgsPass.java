/*
 * Copyright 2017 Google Inc.
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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;

/**
 * Passes that fix up msg nodes.
 *
 * <p>After parsing we need to do things like
 *
 * <ul>
 *   <li>handle the {@code genders} attribute by expanding a {@code select}
 *   <li>create placeholder nodes for html tags (which means we need to run after HtmlRewritePass)
 *   <li>handle the {@code remainder} function
 * </ul>
 *
 * <p>After all that the msgs are in a state where we can generate code or extract them. So we
 * calculate the substituion map, which is a set of data for assigning names to placeholders and
 * picking representative placeholders when expressions occur multiple times.
 */
final class MsgsPass extends CompilerFilePass {

  private final ErrorReporter errorReporter;
  private final InsertMsgPlaceholderNodesPass insertMsgPlaceholderNodesPass;
  private final RewriteRemaindersPass rewriteRemainders;
  private final CheckNonEmptyMsgNodesPass checkNonEmptyPass;

  MsgsPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
    this.rewriteRemainders = new RewriteRemaindersPass(errorReporter);
    this.insertMsgPlaceholderNodesPass = new InsertMsgPlaceholderNodesPass(errorReporter);
    this.checkNonEmptyPass = new CheckNonEmptyMsgNodesPass(errorReporter);
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    ImmutableList<MsgNode> allMsgs = SoyTreeUtils.getAllNodesOfType(file, MsgNode.class);
    for (MsgNode msg : allMsgs) {
      msg.ensureSubstUnitInfoHasNotBeenAccessed();
    }
    insertMsgPlaceholderNodesPass.run(file, nodeIdGen);
    new RewriteGenderMsgsVisitor(nodeIdGen, errorReporter).exec(file);
    rewriteRemainders.run(file, nodeIdGen);
    for (MsgNode msg : allMsgs) {
      msg.calculateSubstitutionInfo(errorReporter);
    }
    checkNonEmptyPass.run(file, nodeIdGen);
  }
}
