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

import com.google.common.collect.Maps;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.IncrementingIdGenerator;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.base.internal.SoyFileSupplier.Version;
import com.google.template.soy.internal.base.Pair;
import com.google.template.soy.soytree.SoyFileNode;

import java.util.Map;

import javax.inject.Inject;

/**
 * Cache for the soy tree respecting file versions.
 *
 * <p> This allows for file-granularity caching of the parsed tree, to avoid parsing the same file
 * over and over if the contents have not changed.  This helps the development experience when
 * there are a large number of files, most of which aren't changing during the edit/reflect loop.
 * This does not help in a production startup-compilation setup; instead, this will just use more
 * memory.
 *
 * <p> Please treat the internals as Soy superpackage-private.
 *
 */
public class SoyAstCache {

  /** Cache mapping file path to the result of the last parse. */
  private final Map<String, Pair<SoyFileNode, Version>> cache;

  /** An ID generator to ensure all versions of all files have unique ID's. */
  private final IdGenerator idGenerator;

  @Inject
  public SoyAstCache() {
    cache = Maps.<String, Pair<SoyFileNode, Version>>newHashMap();
    idGenerator = new IncrementingIdGenerator();
  }

  /**
   * Stores a cached version of the AST.
   *
   * <p> Please treat this as superpackage-private for Soy internals.
   *
   * @param supplier The supplier for the particular file to cache.
   * @param version The version of the supplier when it was read.
   * @param soyFileNode The compiled AST at the particular version. The node is defensively copied;
   *     the caller is free to modify it.
   */
  public synchronized void put(
      SoyFileSupplier supplier, Version version, SoyFileNode node) {
    cache.put(getCacheKey(supplier), Pair.of(node.clone(), version));
  }

  /**
   * Retrieves a cached version of this file supplier AST, if any.
   *
   * <p> Please treat this as superpackage-private for Soy internals.
   *
   * @param supplier The supplier for the particular file to cache.
   * @return A fresh copy of the tree that may be modified by the caller, or null if no entry was
   *     found in the cache.
   */
  public synchronized Pair<SoyFileNode, Version> get(SoyFileSupplier supplier) {
    Pair<SoyFileNode, Version> entry = cache.get(getCacheKey(supplier));
    if (entry != null) {
      if (!supplier.hasChangedSince(entry.second)) {
        // Make a defensive copy since the caller might run further passes on it.
        return Pair.of(entry.first.clone(), entry.second);
      } else {
        // Aggressively purge to save memory.
        cache.remove(getCacheKey(supplier));
      }
    }
    return null;
  }

  /**
   * Returns an ID generator that must be used for all files in this cache.
   *
   * <p> If this ID generator is not used, nodes in the cache will have conflicting ID's. It is
   * important to use a manual synchronized block over this cache while using the ID generator
   * since the ID generator is not guaranteed to be thread-safe!
   *
   * <p> Please treat this as superpackage-private for Soy internals.
   */
  public IdGenerator getNodeIdGenerator() {
    return idGenerator;
  }

  private static String getCacheKey(SoyFileSupplier supplier) {
    // NOTE: We're using pathname because:
    // - The client code might re-create SoyFileSuppliers instead of reusing them.
    // - We don't want to prevent SoyFileSuppliers from getting garbage collected.
    return supplier.getFilePath();
  }
}
