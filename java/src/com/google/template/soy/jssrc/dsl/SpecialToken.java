/*
 * Copyright 2023 Google Inc.
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

import java.util.stream.Stream;

/** A special token, typically whitespace or a comment, that isn't preserved in the AST. */
public abstract class SpecialToken extends CodeChunk {

  @Override
  Stream<? extends CodeChunk> childrenStream() {
    return Stream.empty();
  }

  @Override
  public final Statement asStatement() {
    return Statements.EMPTY.prepend(this);
  }

  @Override
  final void doFormatInitialStatements(FormattingContext ctx) {}

  abstract void doFormatToken(FormattingContext ctx);
}
