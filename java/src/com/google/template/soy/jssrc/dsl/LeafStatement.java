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
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.jssrc.restricted.JsExpr;
import java.util.function.Consumer;

/**
 * Despite the name, {@link JsExpr} instances don't have to contain valid JavaScript expressions.
 * This class holds such instances.
 */
@AutoValue
@Immutable
abstract class LeafStatement extends Statement {
  abstract String value();
  abstract ImmutableSet<GoogRequire> requires();

  static LeafStatement create(String value, Iterable<GoogRequire> requires) {
    // This hackery is to work around extra newlines and semi colons added by JsCodeBuilder
    // TODO(b/35203585): this (and leafstatement) should go away when jscodebuilder does
    while (value.endsWith("\n")) {
      value = value.substring(0, value.length() - 1);
    }
    if (!value.isEmpty() && !value.endsWith("}") && !value.endsWith(";")) {
      value += ';';
    }
    return new AutoValue_LeafStatement(value, ImmutableSet.copyOf(requires));
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx) {
    if (value().length() == 0) {
      return;
    }
    // split and call append for each line to trigger correct indenting logic
    for (String line : Splitter.on('\n').split(value())) {
      ctx.append(line).endLine();
    }
  }

  @Override
  public void collectRequires(Consumer<GoogRequire> collector) {
    for (GoogRequire require : requires()) {
      collector.accept(require);
    }
  }
}
