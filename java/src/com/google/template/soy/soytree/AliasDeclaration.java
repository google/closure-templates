/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.soytree;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.base.internal.Identifier.Type;

/** An {@code {alias ..}} declaration. */
@AutoValue
@Immutable
public abstract class AliasDeclaration {

  public static AliasDeclaration create(Identifier namespace, Identifier alias) {
    checkArgument(namespace.type() != Type.DOT_IDENT);
    checkArgument(alias.type() == Type.SINGLE_IDENT);
    return new AutoValue_AliasDeclaration(namespace, alias);
  }

  public abstract Identifier namespace();

  /** The alias itself (either following `as` or the last word in the aliased identifier) */
  public abstract Identifier alias();
}
