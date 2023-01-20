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
package com.google.template.soy.jssrc.dsl;

import com.google.errorprone.annotations.Immutable;

/**
 * Subclass of {@link CodeChunk} that compile to one or more JavaScript statements.
 *
 * <p>It should be the case that any Statement will start and end in the same lexical scope.
 */
@Immutable
public abstract class Statement extends CodeChunk {

  Statement() {}

  @Override
  final void doFormatInitialStatements(FormattingContext ctx) {
    doFormatStatement(ctx);
  }

  @Override
  public final Statement asStatement() {
    return this;
  }

  /** Appends this statement to the {@link FormattingContext}. */
  abstract void doFormatStatement(FormattingContext ctx);
}
