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

import java.util.stream.Stream;

/** Represents a JavaScript {@code debugger;} statement. */
final class Debugger extends Statement {
  static final Debugger INSTANCE = new Debugger();

  private Debugger() {}

  @Override
  void doFormatStatement(FormattingContext ctx) {
    ctx.append("debugger;").endLine();
  }

  @Override
  Stream<? extends CodeChunk> childrenStream() {
    return Stream.empty();
  }
}
