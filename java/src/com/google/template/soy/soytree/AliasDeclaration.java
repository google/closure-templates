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

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;

/** An {@code {alias ..}} declaration. */
public final class AliasDeclaration {

  private final String namespace;
  private final String alias;
  private final SourceLocation location;

  public AliasDeclaration(String namespace, String alias, SourceLocation location) {
    checkArgument(BaseUtils.isDottedIdentifier(namespace));
    checkArgument(BaseUtils.isIdentifier(alias));
    this.namespace = namespace;
    this.alias = alias;
    this.location = location;
  }

  public String getNamespace() {
    return namespace;
  }

  public String getAlias() {
    return alias;
  }

  public SourceLocation getLocation() {
    return location;
  }
}
