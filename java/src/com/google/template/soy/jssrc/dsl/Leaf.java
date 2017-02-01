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
import com.google.common.collect.ImmutableList;
import com.google.template.soy.jssrc.dsl.CodeChunk.RequiresCollector;
import com.google.template.soy.jssrc.restricted.JsExpr;

/**
 * Holds {@link JsExpr expressions}. These chunks can always be represented as single expressions
 * (namely, the {@link #value} from which they are built).
 */
@AutoValue
abstract class Leaf extends CodeChunk.WithValue {
  abstract JsExpr value();
  
  abstract ImmutableList<String> requires();

  static Leaf create(String text) {
    return create(new JsExpr(text, Integer.MAX_VALUE));
  }

  static Leaf create(JsExpr value) {
    return new AutoValue_Leaf(value, ImmutableList.<String>of());
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx, boolean moreToCome) {
    // nothing to do
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx, OutputContext outputContext) {
    ctx.append(value().getText());
  }

  @Override
  public boolean isRepresentableAsSingleExpression() {
    return true;
  }

  @Override
  public JsExpr singleExprOrName() {
    return value();
  }
  
  @Override
  public void collectRequires(RequiresCollector collector) {
    for (String require : requires()) {
      collector.add(require);
    }
  }
}
