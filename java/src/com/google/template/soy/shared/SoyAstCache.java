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

import com.google.template.soy.base.internal.SoyFileSupplier.Version;
import com.google.template.soy.soytree.SoyFileNode;
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
  private static final class VersionedFile {
    final SoyFileNode file;

    final Version version;

    VersionedFile(SoyFileNode file, Version version) {
      this.file = file;
      this.version = version;
    }
  }

  /** Cache mapping file path to the result of the last parse. */
  @GuardedBy("this")
  private final Map<String, VersionedFile> cache = new HashMap<>();

  @Inject
  public SoyAstCache() {}

  /**
   * Stores a cached version of the AST.
   *
   * <p>Please treat this as superpackage-private for Soy internals.
   *
   * @param fileName The name of the file.
   * @param version The version of the file
   * @param file The parsed file. Caution this is stored as is, callers should take care to make
   *     defensive copies.
   */
  public synchronized void put(String fileName, Version version, SoyFileNode file) {
    cache.put(fileName, new VersionedFile(file, version));
  }

  /**
   * Retrieves a cached version of this file supplier AST, if any.
   *
   * <p>Please treat this as superpackage-private for Soy internals.
   *
   * @param fileName The name of the file
   * @param version The current file version.
   * @return The stored version of the tree. Callers should take care to make copies to avoid
   *     corrupting data in the cache.
   */
  public synchronized SoyFileNode get(String fileName, Version version) {
    VersionedFile entry = cache.get(fileName);
    if (entry != null) {
      if (entry.version.equals(version)) {
        return entry.file;
      } else {
        // Aggressively purge to save memory.
        cache.remove(fileName);
      }
    }
    return null;
  }

  /**
   * Evicts a file from the cache, normally this is not necessary but it can be used to limit memory
   * consumption.
   */
  public synchronized boolean evict(String fileName) {
    VersionedFile entry = cache.remove(fileName);
    return entry != null;
  }
}
