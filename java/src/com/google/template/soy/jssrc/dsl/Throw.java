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
import com.google.errorprone.annotations.Immutable;

/** Represents a JavaScript throw statement. */
@AutoValue
@Immutable
abstract class Throw extends Statement {

  abstract Expression value();

  static Throw create(Expression value) {
    return new AutoValue_Throw(value);
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx) {
    ctx.appendInitialStatements(value())
        .append("throw ")
        .appendOutputExpression(value())
        .append(';');
  }

  @Override
  public void collectRequires(RequiresCollector collector) {
    value().collectRequires(collector);
  }
}
