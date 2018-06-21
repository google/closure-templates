/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.jbcsrc;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.jbcsrc.restricted.CodeBuilder;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.Statement;
import java.util.List;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/** Utilities for encoding control flow in terms of statements and expressions. */
final class ControlFlow {
  private ControlFlow() {}

  @AutoValue
  abstract static class IfBlock {
    static IfBlock create(Expression cond, Statement block) {
      cond.checkAssignableTo(Type.BOOLEAN_TYPE);
      return new AutoValue_ControlFlow_IfBlock(cond, block);
    }

    abstract Expression condition();

    abstract Statement block();

    /** Returns this single {@code if} as a standalone statement. */
    Statement asStatement() {
      return ifElseChain(ImmutableList.of(this), Optional.<Statement>absent());
    }
  }

  /**
   * Returns a statement that encodes the given sequence of {@link IfBlock if blocks} as an
   * if-elseif-else chain.
   */
  static Statement ifElseChain(final List<IfBlock> ifs, final Optional<Statement> elseBlock) {
    checkArgument(!ifs.isEmpty());
    return new Statement() {
      @Override
      protected void doGen(CodeBuilder adapter) {
        Label end = new Label();
        Label next;
        for (int i = 0; i < ifs.size(); i++) {
          IfBlock curr = ifs.get(i);
          boolean isLastIfBlock = i == ifs.size() - 1;
          if (isLastIfBlock && !elseBlock.isPresent()) {
            next = end;
          } else {
            next = new Label();
          }
          curr.condition().gen(adapter);
          adapter.ifZCmp(Opcodes.IFEQ, next);
          curr.block().gen(adapter);
          if (end != next) {
            adapter.goTo(end);
          }
          adapter.mark(next);
        }
        if (elseBlock.isPresent()) {
          elseBlock.get().gen(adapter);
          adapter.mark(end);
        }
      }
    };
  }
}
