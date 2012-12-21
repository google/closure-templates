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

package com.google.template.soy.base;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import com.google.common.io.InputSupplier;
import com.google.common.io.Resources;
import com.google.template.soy.internal.base.Pair;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.net.URL;


/**
 * Record for one input Soy file.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 * @author Mike Samuel
 */
public interface SoyFileSupplier {


  /**
   * An opaque identifier that can be compared for equality with other versions from the same
   * resource.
   * <p>
   * Instances are not {@code Comparable} since a version is not necessarily monotonic ; e.g. a
   * cryptographically strong hash function produces a more reliable version identifier than a
   * time-stamp but not one that can be said to be newer or older than any other version.
   */
  public interface Version {

    /**
     * Compares to versions that are equivalent.  Meaningless if applied to versions from a
     * different resource.
     */
    @Override boolean equals(Object o);


    /** A version for stable resources : resources that don't change over the life of a JVM. */
    public static final Version STABLE_VERSION = new Version() {};

  }


  /**
   * Returns a {@link Reader} for the Soy file content and the version of the file read.
   *
   * @throws IOException If there is an error opening the input.
   */
  public Pair<Reader, Version> open() throws IOException;


  /**
   * True if the underlying resource has changed since the given version.
   */
  public boolean hasChangedSince(Version version);


  /**
   * Returns the kind of this input Soy file.
   */
  public SoyFileKind getSoyFileKind();


  /**
   * Returns the file path (used for messages only).
   */
  public String getFilePath();


  /**
   * Container for factory methods for {@link SoyFileSupplier}s.
   *
   * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
   */
  public static final class Factory {


    /**
     * Creates a new {@code SoyFileSupplier} given an {@code InputSupplier} for the file content,
     * as well as the desired file path for messages.
     *
     * @param contentSupplier Supplier of a Reader for the Soy file content.
     * @param soyFileKind The kind of this input Soy file.
     * @param filePath The path to the Soy file (used for messages only).
     */
    public static SoyFileSupplier create(
        InputSupplier<? extends Reader> contentSupplier, SoyFileKind soyFileKind, String filePath) {
      return new StableSoyFileSupplier(contentSupplier, soyFileKind, filePath);
    }


    /**
     * Creates a new {@code SoyFileSupplier} given a {@code File}.
     *
     * @param inputFile The Soy file.
     * @param soyFileKind The kind of this input Soy file.
     */
    public static SoyFileSupplier create(File inputFile, SoyFileKind soyFileKind) {
      return create(
          Files.newReaderSupplier(inputFile, Charsets.UTF_8), soyFileKind, inputFile.getPath());
    }


    /**
     * Creates a new {@code SoyFileSupplier} given a resource {@code URL}, as well as the desired
     * file path for messages.
     *
     * @param inputFileUrl The URL of the Soy file.
     * @param soyFileKind The kind of this input Soy file.
     * @param filePath The path to the Soy file (used for messages only).
     */
    public static SoyFileSupplier create(
        URL inputFileUrl, SoyFileKind soyFileKind, String filePath) {
      return create(
          Resources.newReaderSupplier(inputFileUrl, Charsets.UTF_8), soyFileKind, filePath);
    }


    /**
     * Creates a new {@code SoyFileSupplier} given a resource {@code URL}.
     *
     * <p> Important: This function assumes that the desired file path is returned by
     * {@code inputFileUrl.toString()}. If this is not the case, please use
     * {@link #create(java.net.URL, SoyFileKind, String)} instead.
     *
     * @see #create(java.net.URL, SoyFileKind, String)
     * @param inputFileUrl The URL of the Soy file.
     * @param soyFileKind The kind of this input Soy file.
     */
    public static SoyFileSupplier create(URL inputFileUrl, SoyFileKind soyFileKind) {
      return create(inputFileUrl, soyFileKind, inputFileUrl.toString());
    }


    /**
     * Creates a new {@code SoyFileSupplier} given the file content provided as a string, as well
     * as the desired file path for messages.
     *
     * @param content The Soy file content.
     * @param soyFileKind The kind of this input Soy file.
     * @param filePath The path to the Soy file (used for messages only).
     */
    public static SoyFileSupplier create(
        CharSequence content, SoyFileKind soyFileKind, String filePath) {
      return create(CharStreams.newReaderSupplier(content.toString()), soyFileKind, filePath);
    }


    private Factory() {
      // Not instantiable.
    }

  }

}
