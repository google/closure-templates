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
import com.google.common.collect.ImmutableSet;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/** Represents a variable declaration. */
@AutoValue
@Immutable
public abstract class Declaration extends CodeChunk {

  abstract String varName();

  abstract CodeChunk.WithValue rhs();

  @Nullable
  abstract String closureCompilerTypeExpression();
  
  abstract ImmutableSet<GoogRequire> googRequires();

  static Declaration create(String varName, CodeChunk.WithValue rhs) {
    return new AutoValue_Declaration(varName, rhs, null, ImmutableSet.<GoogRequire>of());
  }

  static Declaration create(
      String varName,
      CodeChunk.WithValue rhs,
      @Nullable String closureCompilerTypeExpression,
      Iterable<GoogRequire> googRequires) {
    return new AutoValue_Declaration(
        varName, rhs, closureCompilerTypeExpression, ImmutableSet.copyOf(googRequires));
  }

  /** Returns a {@link CodeChunk.WithValue} representing a reference to this declared variable. */
  public CodeChunk.WithValue ref() {
    return VariableReference.of(this);
  }

  /**
   * {@link CodeChunk#getCode} serializes both the chunk's initial statements and its output
   * expression. When a declaration is the only chunk being serialized, this leads to a redundant
   * trailing expression: <code>
   *   var $$tmp = blah;
   *   $$tmp
   * </code> Override the superclass implementation to omit the trailing expression.
   */
  @Override
  String getCode(int startingIndent) {
    FormattingContext ctx = new FormattingContext(startingIndent);
    ctx.appendInitialStatements(this);
    return ctx.toString();
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx) {
    ctx.appendInitialStatements(rhs());
    if (closureCompilerTypeExpression() != null) {
      ctx.append("/** @type {").append(closureCompilerTypeExpression()).append("} */").endLine();
    }
    ctx.append("var ")
        .append(varName())
        .append(" = ")
        .appendOutputExpression(rhs())
        .append(";")
        .endLine();
  }

  @Override
  public void collectRequires(RequiresCollector collector) {
    for (GoogRequire require : googRequires()) {
      collector.add(require);
    }
    rhs().collectRequires(collector);
  }
}
