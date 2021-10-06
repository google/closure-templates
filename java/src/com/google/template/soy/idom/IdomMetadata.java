/*
 * Copyright 2021 Google Inc.
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

package com.google.template.soy.idom;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.idom.IdomMetadataP.Kind;
import org.jspecify.nullness.Nullable;

/**
 * Contains metadata needed to check errors when using idom. This is a java wrapper of IdomMetadataP
 * to make it easier to use, especially regarding storing locations.
 */
@AutoValue
public abstract class IdomMetadata {
  public abstract Kind kind();

  public abstract @Nullable String name();

  public abstract @Nullable SourceLocation location();

  public abstract ImmutableList<IdomMetadata> children();

  public abstract Builder toBuilder();

  /** Builder for IdomMetadata. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder kind(Kind kind);

    public abstract Builder name(String name);

    public abstract Builder location(SourceLocation location);

    public abstract ImmutableList.Builder<IdomMetadata> childrenBuilder();

    public final Builder addChild(IdomMetadata child) {
      childrenBuilder().add(child);
      return this;
    }

    public final Builder addChildren(Iterable<IdomMetadata> children) {
      childrenBuilder().addAll(children);
      return this;
    }

    public abstract IdomMetadata build();
  }

  public static Builder newBuilder(Kind kind) {
    return new AutoValue_IdomMetadata.Builder().kind(kind);
  }
}
