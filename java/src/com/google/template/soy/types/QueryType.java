/*
 * Copyright 2026 Google Inc.
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

package com.google.template.soy.types;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.SoyTypeP;

/** The query type: `typeof $expr`. */
@AutoValue
public abstract class QueryType extends ComputedType {

  public static QueryType create(ExprRootNode query) {
    return new AutoValue_QueryType(query);
  }

  public abstract ExprRootNode query();

  @Override
  public SoyType getEffectiveType() {
    return Preconditions.checkNotNull(query().getType());
  }

  @Override
  public boolean isComputed() {
    return query().getType() != null;
  }

  @Override
  public final String toString() {
    return "typeof " + query().toSourceString();
  }

  @Override
  protected void doToProto(SoyTypeP.Builder builder) {
    getEffectiveType().doToProto(builder);
  }
}
