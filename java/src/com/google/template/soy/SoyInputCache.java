/*
 * Copyright 2018 Google Inc.
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

package com.google.template.soy;

import java.io.File;
import java.io.IOException;

/** A simple cache interface for reading soy compiler inputs. */
interface SoyInputCache {
  /** Default implementation that does no caching. */
  SoyInputCache DEFAULT =
      new SoyInputCache() {
        @Override
        public <T> T read(File file, CacheLoader<T> loader, SoyCompilerFileReader reader)
            throws IOException {
          T value = loader.read(file, reader, this);
          // everything is always immediately evicted
          loader.onEvict(value);
          return value;
        }

        @Override
        public void declareDependency(File file, File dependency) {}
      };

  /** A Reader can read a file as a structured object. */
  interface CacheLoader<T> {
    /** Reads an object from the file using the given file reader. */
    T read(File file, SoyCompilerFileReader fileReader, SoyInputCache cache) throws IOException;

    /**
     * Called when the item is removed from the cache.
     *
     * <p>The default implementation does nothing. This can be used to manage 'closeable' resources.
     *
     * @param item The item being evicted
     * @throws IOException if closing the object requires closing files or other resources.
     */
    default void onEvict(T item) throws IOException {}
  }

  /**
   * Read the file from the cache using the reader to interpret.
   *
   * <p>There is no guarantee for how long or even if the file will be cached.
   *
   * @param file The file to read
   * @param loader The strategy for interpreting the file contents. Callers should ensure to always
   *     pass the same instance.
   * @param reader The reader to use to open the file
   * @return the result of reaeding the file, possibly from a cache.
   */
  <T> T read(File file, CacheLoader<T> loader, SoyCompilerFileReader reader) throws IOException;

  /**
   * Declares that one file depends on another. Therefore if {@code dependency} changes then {@code
   * file} should be evicted.
   */
  void declareDependency(File file, File dependency);
}
