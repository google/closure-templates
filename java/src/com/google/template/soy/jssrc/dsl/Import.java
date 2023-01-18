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
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSortedSet;
import com.google.errorprone.annotations.Immutable;
import java.util.function.Consumer;

/** Represents an {@code import} statement. */
@AutoValue
@Immutable
abstract class Import extends Statement {

  static final String STAR = "*";

  abstract ImmutableSortedSet<String> symbols();

  abstract String path();

  static Import moduleImport(String alias, String path) {
    return new AutoValue_Import(ImmutableSortedSet.of(STAR + " as " + alias), path);
  }

  static Import symbolImport(String symbol, String path) {
    return new AutoValue_Import(ImmutableSortedSet.of(symbol), path);
  }

  static Import symbolImport(String symbol, String alias, String path) {
    if (symbol.equals(alias)) {
      return symbolImport(symbol, path);
    }
    return new AutoValue_Import(ImmutableSortedSet.of(symbol + " as " + alias), path);
  }

  static Import symbolImport(Iterable<String> symbols, String path) {
    return new AutoValue_Import(ImmutableSortedSet.copyOf(symbols), path);
  }

  boolean isModuleImport() {
    return symbols().size() == 1 && symbols().iterator().next().startsWith(STAR);
  }

  @Override
  void doFormatStatement(FormattingContext ctx) {
    StringBuilder singleLine = new StringBuilder();
    singleLine.append("import ");

    boolean isModule = isModuleImport();
    if (!isModule) {
      singleLine.append("{");
    }
    Joiner.on(", ").appendTo(singleLine, symbols());
    if (!isModule) {
      singleLine.append("}");
    }
    singleLine.append(" from '").append(path()).append("';");
    ctx.append(singleLine.toString());
    ctx.endLine();
  }

  @Override
  public void collectRequires(Consumer<GoogRequire> collector) {}
}
