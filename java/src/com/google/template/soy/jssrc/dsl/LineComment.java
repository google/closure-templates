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
import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.Immutable;

/** Represents a "//..." line comment. */
@AutoValue
@Immutable
public abstract class LineComment extends SpecialToken {

  abstract String source();

  public static LineComment create(String comment) {
    Preconditions.checkArgument(!comment.contains("\n"));
    if (!comment.startsWith("//")) {
      comment = "// " + comment;
    }
    return new AutoValue_LineComment(comment);
  }

  public String content() {
    if (source().startsWith("// ")) {
      return source().substring(3);
    }
    return source().substring(2);
  }

  @Override
  void doFormatToken(FormattingContext ctx) {
    ctx.append(source());
    ctx.endLine();
  }
}
