/*
 * Copyright 2022 Google Inc.
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
import java.util.function.Consumer;

/** Represents a single TS/JS gencode file. */
@AutoValue
@Immutable
public abstract class File extends Statement {

  abstract String fileOverviewComments();

  abstract CodeChunk imports();

  abstract ImmutableList<Statement> children();

  /**
   * Whether to apply {@code FormattingContext.applyTsxLineBreaks()}, which will do things like
   * limit lines to 80 chars, and break lines between curly braces to try to make the gencode more
   * readable.
   */
  abstract boolean useTsxLineBreaks();

  public static File create(
      String fileOverviewComments,
      CodeChunk imports,
      ImmutableList<Statement> children,
      boolean useTsxLineBreaks) {
    return new AutoValue_File(fileOverviewComments, imports, children, useTsxLineBreaks);
  }

  @Override
  public void collectRequires(Consumer<GoogRequire> collector) {
    // todo(add a wrapper type to handle path-based imports as well, and then handle collecting
    // imports for the file here).
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx) {
    if (useTsxLineBreaks()) {
      ctx.enableTsxLineBreaks();
    }
    ctx.append(fileOverviewComments());

    if (!(imports() instanceof StatementList && ((StatementList) imports()).isEmpty())) {
      ctx.appendBlankLine();
    }
    ctx.appendAll(imports());

    for (int i = 0; i < children().size(); i++) {
      Statement child = children().get(i);
      ctx.appendBlankLine();
      if (i > 0) {
        ctx.appendBlankLine();
      }
      ctx.appendAll(child);
    }
    ctx.appendBlankLine();
  }
}
