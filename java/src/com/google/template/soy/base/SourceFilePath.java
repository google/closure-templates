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
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.CharMatcher;
import com.google.errorprone.annotations.Immutable;

/** Representation of a path in the Soy compiler. */
@Immutable
@AutoValue
public abstract class SourceFilePath implements Comparable<SourceFilePath> {

  public static SourceFilePath create(String path, String realPath) {
    checkArgument(!path.isEmpty());
    checkArgument(!realPath.isEmpty());
    return new AutoValue_SourceFilePath(path, realPath);
  }

  /**
   * Convenience factory for converting {@link SourceLogicalPath} to SourceFilePath. This of course
   * will elide any differences between the logical path and the real path.
   */
  public static SourceFilePath create(SourceLogicalPath path) {
    return create(path.path(), path.path());
  }

  /** Single-arg convenience factory for testing. */
  public static SourceFilePath forTest(String path) {
    return create(path, path);
  }

  SourceFilePath() {}

  /** The logical path. */
  public abstract String path();

  /** The "real" path, e.g. including bin/genfiles prefix. */
  public abstract String realPath();

  @Memoized
  public SourceLogicalPath asLogicalPath() {
    return SourceLogicalPath.create(path());
  }

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

  @Override
  public final String toString() {
    return realPath();
  }
}
