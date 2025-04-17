/*
 * Copyright 2025 Google Inc.
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
import com.google.errorprone.annotations.Immutable;
import java.util.stream.Stream;

@AutoValue
@Immutable
abstract class While extends Statement {

  abstract Expression condition();

  abstract Statement body();

  static While create(Expression condition, Statement body) {
    return new AutoValue_While(condition, body);
  }

  @Override
  Stream<? extends CodeChunk> childrenStream() {
    return Stream.of(condition(), body());
  }

  @Override
  void doFormatStatement(FormattingContext ctx) {
    ctx.appendInitialStatements(condition());

    ctx.append("while (").appendOutputExpression(condition()).append(")");
    ctx.appendAllIntoBlock(body());
    ctx.endLine();
  }
}
