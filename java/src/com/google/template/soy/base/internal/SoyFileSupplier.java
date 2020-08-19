/*
 * Copyright 2009 Google Inc.
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

package com.google.template.soy.base.internal;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.io.CharSource;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.net.URL;

/**
 * Record for one input Soy file.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p>TODO(lukes): This should either be a subtype of CharSource or hold a CharSource
 *
 */
public interface SoyFileSupplier {

  /**
   * An opaque identifier that can be compared for equality with other versions from the same
   * resource.
   *
   * <p>Instances are not {@code Comparable} since a version is not necessarily monotonic ; e.g. a
   * cryptographically strong hash function produces a more reliable version identifier than a
   * time-stamp but not one that can be said to be newer or older than any other version.
   */
  interface Version {

    /**
     * Compares to versions that are equivalent. Meaningless if applied to versions from a different
     * resource.
     */
    @Override
    boolean equals(Object o);

    /** A version for stable resources : resources that don't change over the life of a JVM. */
    Version STABLE_VERSION = new Version() {};
  }

  /** View this supplier as a {@link CharSource}. */
  default CharSource asCharSource() {
    return new CharSource() {
      @Override
      public Reader openStream() throws IOException {
        return open();
      }
    };
  }

  /**
   * Returns a {@link Reader} for the Soy file content.
   *
   * @throws IOException If there is an error opening the input.
   */
  Reader open() throws IOException;

  /** Returns the path to the Soy file, used for as a unique map/set key and for messages. */
  String getFilePath();

  /** Returns the version of the Soy file read. */
  Version getVersion();

  /**
   * Container for factory methods for {@link SoyFileSupplier}s.
   *
   * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
   */
  final class Factory {

    /**
     * Creates a new {@code SoyFileSupplier} given a {@code CharSource} for the file content, as
     * well as the desired file path for messages.
     *
     * @param contentSource Source for the Soy file content.
     * @param soyFileKind The kind of this input Soy file.
     * @param filePath The path to the Soy file, used for as a unique map/set key and for messages.
     */
    public static SoyFileSupplier create(CharSource contentSource, String filePath) {
      return new StableSoyFileSupplier(contentSource, filePath);
    }

    /**
     * Creates a new {@code SoyFileSupplier} given a {@code File}.
     *
     * @param inputFile The Soy file.
     * @param soyFileKind The kind of this input Soy file.
     */
    public static SoyFileSupplier create(File inputFile) {
      return create(Files.asCharSource(inputFile, UTF_8), inputFile.getPath());
    }

    /**
     * Creates a new {@code SoyFileSupplier} given a resource {@code URL}, as well as the desired
     * file path for messages.
     *
     * @param inputFileUrl The URL of the Soy file.
     * @param soyFileKind The kind of this input Soy file.
     * @param filePath The path to the Soy file, used for as a unique map/set key and for messages.
     */
    public static SoyFileSupplier create(URL inputFileUrl, String filePath) {
      return create(Resources.asCharSource(inputFileUrl, UTF_8), filePath);
    }

    /**
     * Creates a new {@code SoyFileSupplier} given the file content provided as a string, as well as
     * the desired file path for messages.
     *
     * @param content The Soy file content.
     * @param soyFileKind The kind of this input Soy file.
     * @param filePath The path to the Soy file, used for as a unique map/set key and for messages.
     */
    public static SoyFileSupplier create(CharSequence content, String filePath) {
      return create(CharSource.wrap(content), filePath);
    }

    private Factory() {
      // Not instantiable.
    }
  }
}
