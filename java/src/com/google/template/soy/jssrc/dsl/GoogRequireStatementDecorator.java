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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;

/** A delegating {@link CodeChunk} that record the symbols that the delegate requires. */
@AutoValue
abstract class GoogRequireStatementDecorator extends CodeChunk {
  static GoogRequireStatementDecorator create(
      LeafStatement delegate, Iterable<GoogRequire> requires) {
    // This check is to account for a limitation in formatOutputExpr
    ImmutableSet<GoogRequire> copy = ImmutableSet.copyOf(requires);
    checkArgument(!copy.isEmpty(), "expected at least one require, got none");
    return new AutoValue_GoogRequireStatementDecorator(delegate, copy);
  }

  abstract LeafStatement underlying();

  abstract ImmutableSet<GoogRequire> requires();

  @Override
  public void collectRequires(RequiresCollector collector) {
    for (GoogRequire require : requires()) {
      collector.add(require);
    }
    underlying().collectRequires(collector);
  }

  // we need to delegate here but the code chunk api is antagonistic to delegation so we suppress
  // the error prone check
  @SuppressWarnings("ForOverride")
  @Override
  void doFormatInitialStatements(FormattingContext ctx, boolean moreToCome) {
    underlying().doFormatInitialStatements(ctx, moreToCome);
  }
}
