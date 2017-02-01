/*
 * Copyright 2016 Google Inc.
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

/**
 * Represents a JavaScript statement.
 *
 * <p>The general algorithm for formatting an expression is to recursively format its
 * subexpressions, joining them together with operators. That is partly what {@link
 * CodeChunk#getCode()} does. Statements make this more complicated. When a {@link Composite}
 * containing a statement is formatted, the statement must appear in the correct order relative to
 * its siblings, so that it can be executed for its side effects. This class exists to force an
 * underlying code chunk to format all of its statements and expressions, so that the enclosing
 * Composite is correctly formatted.
 */
@AutoValue
abstract class Statement extends CodeChunk {
  abstract CodeChunk underlying();

  static Statement create(CodeChunk underlying) {
    return new AutoValue_Statement(underlying);
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx, boolean moreToCome) {
    underlying().formatAllStatements(ctx, moreToCome);
    ctx.endLine();
  }
  
  @Override
  public void collectRequires(RequiresCollector collector) {
    underlying().collectRequires(collector);
  }
}
