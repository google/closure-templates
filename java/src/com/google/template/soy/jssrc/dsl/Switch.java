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

package com.google.template.soy.jssrc.dsl;


import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import javax.annotation.Nullable;

/** Represents a {@code switch} statement. */
@AutoValue
@Immutable
abstract class Switch extends Statement {
  abstract Expression switchOn();

  abstract ImmutableList<CaseClause> caseClauses();

  @Nullable
  abstract CodeChunk defaultCaseBody();

  static Switch create(
      Expression switchOn,
      ImmutableList<CaseClause> caseClauses,
      @Nullable CodeChunk defaultCaseBody) {
    return new AutoValue_Switch(switchOn, caseClauses, defaultCaseBody);
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx) {
    // Append the initial statements for the switch expression and all the case labels.
    ctx.appendInitialStatements(switchOn());
    for (CaseClause caseClause : caseClauses()) {
      for (Expression caseLabel : caseClause.caseLabels) {
        ctx.appendInitialStatements(caseLabel);
      }
    }

    // Append the output expressions for the switch expression and case labels,
    // together with the complete of for all the bodies.
    ctx.append("switch (").appendOutputExpression(switchOn()).append(") ");
    try (FormattingContext ignored = ctx.enterBlock()) {
      for (CaseClause caseClause : caseClauses()) {
        for (int i = 0; i < caseClause.caseLabels.size(); ++i) {
          ctx.append("case ").appendOutputExpression(caseClause.caseLabels.get(i)).append(":");
          // The last case label in this clause will have its line ended by enterCaseBody below.
          if (i < caseClause.caseLabels.size() - 1) {
            ctx.endLine();
          }
        }
        try (FormattingContext ignored2 = ctx.enterCaseBody()) {
          ctx.appendAll(caseClause.caseBody).endLine().append("break;").endLine();
        }
        ctx.endLine();
      }
      if (defaultCaseBody() != null) {
        ctx.append("default:");
        try (FormattingContext ignored2 = ctx.enterCaseBody()) {
          ctx.appendAll(defaultCaseBody());
        }
      }
    }
  }

  @Override
  public void collectRequires(RequiresCollector collector) {
    switchOn().collectRequires(collector);
    for (CaseClause caseClause : caseClauses()) {
      for (Expression caseLabel : caseClause.caseLabels) {
        caseLabel.collectRequires(collector);
      }
      caseClause.caseBody.collectRequires(collector);
    }
    if (defaultCaseBody() != null) {
      defaultCaseBody().collectRequires(collector);
    }
  }

  /**
   * Represents a single clause of a {@code switch} statement: one or more {@code case} labels
   * followed by a body.
   */
  @Immutable
  static final class CaseClause {
    private final ImmutableList<Expression> caseLabels;
    private final Statement caseBody;

    CaseClause(ImmutableList<Expression> caseLabels, Statement caseBody) {
      this.caseLabels = caseLabels;
      this.caseBody = caseBody;
    }
  }
}
