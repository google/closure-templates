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

import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.Immutable;
import java.util.stream.Stream;

/** Represents a statement wrapped in a try block, with a no-op catch block. */
@AutoValue
@Immutable
public abstract class TryCatch extends Statement {

  abstract Statement body();

  public static TryCatch create(Statement body) {
    return new AutoValue_TryCatch(body);
  }

  @Override
  void doFormatStatement(FormattingContext ctx) {
    ctx.append("try ");
    ctx.enterBlock();
    ctx.appendAll(body());
    ctx.close();
    ctx.append(" catch {}");
    ctx.endLine();
  }

  @Override
  Stream<? extends CodeChunk> childrenStream() {
    return Stream.of(body());
  }
}
