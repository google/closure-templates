/*
 * Copyright 2018 Google Inc.
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

import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;

/**
 * Calculates the substitution info for {@code msg} nodes. This calculates the placeholder names and
 * placeholder equivalency.
 *
 * <p>All passes which add placeholders must run prior to this.
 */
@RunAfter({
  InsertMsgPlaceholderNodesPass.class,
})
final class CalculateMsgSubstitutionInfoPass implements CompilerFilePass {
  private final ErrorReporter errorReporter;

  CalculateMsgSubstitutionInfoPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (MsgNode msg : SoyTreeUtils.getAllNodesOfType(file, MsgNode.class)) {
      msg.calculateSubstitutionInfo(errorReporter);
    }
  }
}
