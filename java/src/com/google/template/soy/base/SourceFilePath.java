/*
 * Copyright 2020 Google Inc.
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
package com.google.template.soy.base;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import com.google.common.base.CharMatcher;
import com.google.errorprone.annotations.Immutable;

/** Representation of a path in the Soy compiler. */
@Immutable
@AutoValue
public abstract class SourceFilePath implements Comparable<SourceFilePath> {
  // TODO(b/162524005): Add support for different 'logical/import paths' vs 'real paths'. Consider
  // modelling file types, there is a limited number and recording it might be useful
  // Consider modeling paths that are purely synthetic (such as the ones we use for plugins).

  public static SourceFilePath create(String path) {
    checkArgument(!path.isEmpty());
    return new AutoValue_SourceFilePath(path);
  }

  SourceFilePath() {}

  public abstract String path();

  public final String fileName() {
    String path = path();
    int lastSlashIndex = CharMatcher.anyOf("/\\").lastIndexIn(path);
    if (lastSlashIndex != -1 && lastSlashIndex != path.length() - 1) {
      return path.substring(lastSlashIndex + 1);
    }
    return path;
  }

  @Override
  public int compareTo(SourceFilePath o) {
    return this.path().compareTo(o.path());
  }
}
