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
import com.google.errorprone.annotations.Immutable;

/** Represents a JavaScript return statement. */
@AutoValue
@Immutable
abstract class Return extends CodeChunk {

  abstract CodeChunk.WithValue value();

  static Return create(CodeChunk.WithValue value) {
    return new AutoValue_Return(value);
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx) {
    ctx.appendInitialStatements(value())
        .append("return ")
        .appendOutputExpression(value())
        .append(';');
  }
  
  @Override
  public void collectRequires(RequiresCollector collector) {
    value().collectRequires(collector);
  }
}
