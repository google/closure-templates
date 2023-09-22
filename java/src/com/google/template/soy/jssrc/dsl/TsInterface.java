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
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.stream.Stream;

/** Represents a TS interface declaration. */
@AutoValue
public abstract class TsInterface extends Statement {

  abstract String name();

  abstract ImmutableList<ParamDecl> properties();

  abstract ImmutableMap<String, JsDoc> docs();

  abstract boolean isExported();

  public static TsInterface create(String name, List<ParamDecl> properties) {
    return new AutoValue_TsInterface(
        name, ImmutableList.copyOf(properties), ImmutableMap.of(), false);
  }

  public static TsInterface create(
      String name,
      List<ParamDecl> properties,
      ImmutableMap<String, JsDoc> docs,
      boolean isExported) {
    return new AutoValue_TsInterface(name, ImmutableList.copyOf(properties), docs, isExported);
  }

  public boolean hasDoc() {
    return !docs().isEmpty();
  }

  @Override
  void doFormatStatement(FormattingContext ctx) {
    if (isExported()) {
      ctx.append("export ");
    }
    ctx.append(String.format("interface %s ", name()));
    ctx.enterBlock();
    for (ParamDecl prop : properties()) {
      if (docs().containsKey(prop.name())) {
        ctx.appendAll(docs().get(prop.name()));
        ctx.endLine();
      }
      ctx.append(String.format("%s%s", prop.name(), prop.optional() ? "?" : ""));
      if (prop.type() != null) {
        ctx.append(": ");
        ctx.appendOutputExpression(prop.type());
      }
      ctx.noBreak().append(";");
      ctx.endLine();
    }
    ctx.close();
    ctx.endLine();
  }

  @Override
  Stream<? extends CodeChunk> childrenStream() {
    return properties().stream();
  }
}
