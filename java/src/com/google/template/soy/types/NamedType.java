/*
 * Copyright 2024 Google Inc.
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
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.soytree.SoyTypeP;

/** A type that is a reference to a Soy `{type}` command. */
@AutoValue
public abstract class NamedType extends ComputedType {

  public static NamedType create(
      String name, String namespace, SourceFilePath sourceFilePath, SoyType type) {
    NamedType rv = new AutoValue_NamedType(name, namespace, sourceFilePath);
    rv.type = Preconditions.checkNotNull(type);
    return rv;
  }

  // Prevents type from affecting equals/hashCode. Allows you to look up interned type without
  // knowing the resolve type. However, may cause you to intern a non-resolved type. Only tests
  // should depend on this behavior.
  private SoyType type;

  public abstract String getName();

  public abstract String getNamespace();

  public abstract SourceFilePath getSourceFilePath();

  public SoyType getType() {
    return type;
  }

  public String getFqn() {
    return getNamespace() + "." + getName();
  }

  @Override
  public final String toString() {
    return getName();
  }

  @Override
  protected void doToProto(SoyTypeP.Builder builder) {
    builder.getNamedBuilder().setName(getName()).setNamespace(getNamespace());
  }

  @Override
  public SoyType getEffectiveType() {
    return getType().getEffectiveType();
  }
}
