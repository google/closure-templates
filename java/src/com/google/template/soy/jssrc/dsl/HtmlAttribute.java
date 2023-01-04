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
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import java.util.function.Consumer;

/** Represents an Html attribute. */
@AutoValue
@Immutable
public abstract class HtmlAttribute extends Statement {

  abstract ImmutableList<Statement> children();

  public static HtmlAttribute create(ImmutableList<Statement> children) {
    return new AutoValue_HtmlAttribute(children);
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx) {
    ctx.append(children().get(0).getCode());
    if (children().size() > 1) {
      ctx.append("=");
      for (Statement attribute : children().subList(1, children().size())) {
        ctx.append(attribute.getCode());
      }
    }
  }

  @Override
  public void collectRequires(Consumer<GoogRequire> collector) {
    children().forEach(c -> c.collectRequires(collector));
  }
}
