/*
 * Copyright 2013 Google Inc.
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

package com.google.template.soy.shared;

import com.google.auto.value.AutoValue;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.IncrementingIdGenerator;
import com.google.template.soy.base.internal.SoyFileSupplier.Version;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;

/**
 * Cache for the soy tree respecting file versions.
 *
 * <p>This allows for file-granularity caching of the parsed tree, to avoid parsing the same file
 * over and over if the contents have not changed. This helps the development experience when there
 * are a large number of files, most of which aren't changing during the edit/reflect loop. This
 * does not help in a production startup-compilation setup; instead, this will just use more memory.
 *
 * <p>Please treat the internals as Soy superpackage-private.
 *
 */
public final class SoyAstCache {
  /** A {@link SoyFileNode} with an associated {@link Version}. */
  @AutoValue
  public abstract static class VersionedFile {
    public static VersionedFile of(SoyFileNode file, Version version) {
      return new AutoValue_SoyAstCache_VersionedFile(file, version);
    }

    VersionedFile() {}

    public abstract SoyFileNode file();

    public abstract Version version();

    /** Make a defensive copy. */
    private VersionedFile copy() {
      return new AutoValue_SoyAstCache_VersionedFile(SoyTreeUtils.cloneNode(file()), version());
    }
  }

  /** Cache mapping file path to the result of the last parse. */
  @GuardedBy("this")
  private final Map<String, VersionedFile> cache = new HashMap<>();

  /** An ID generator to ensure all versions of all files have unique ID's. */
  private final IdGenerator idGenerator = new IncrementingIdGenerator();

  @Inject
  public SoyAstCache() {}

  /**
   * Stores a cached version of the AST.
   *
   * <p>Please treat this as superpackage-private for Soy internals.
   *
   * @param fileName The name of the file.
   * @param versionedFile The compiled AST at the particular version. The node is defensively
   *     copied; the caller is free to modify it.
   */
  public synchronized void put(String fileName, VersionedFile versionedFile) {
    cache.put(fileName, versionedFile.copy());
  }

  /**
   * Retrieves a cached version of this file supplier AST, if any.
   *
   * <p>Please treat this as superpackage-private for Soy internals.
   *
   * @param fileName The name of the file
   * @param version The current file version.
   * @return A fresh copy of the tree that may be modified by the caller, or null if no entry was
   *     found in the cache.
   */
  public synchronized VersionedFile get(String fileName, Version version) {
    VersionedFile entry = cache.get(fileName);
    if (entry != null) {
      if (entry.version().equals(version)) {
        // Make a defensive copy since the caller might run further passes on it.
        return entry.copy();
      } else {
        // Aggressively purge to save memory.
        cache.remove(fileName);
      }
    }
    return null;
  }

  /**
   * Returns an ID generator that must be used for all files in this cache.
   *
   * <p>If this ID generator is not used, nodes in the cache will have conflicting ID's. It is
   * important to use a manual synchronized block over this cache while using the ID generator since
   * the ID generator is not guaranteed to be thread-safe!
   *
   * <p>Please treat this as superpackage-private for Soy internals.
   */
  public IdGenerator getNodeIdGenerator() {
    return idGenerator;
  }
}
