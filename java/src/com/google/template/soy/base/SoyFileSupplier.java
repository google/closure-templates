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

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.net.URL;


/**
 * Record for one input Soy file. Contains an {@link InputSupplier} to supply a {@code Reader} for
 * the file content, and also the file path.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class SoyFileSupplier implements InputSupplier<Reader> {


  /** Supplier of a Reader for the Soy file content. */
  private final InputSupplier<? extends Reader> contentSupplier;

  /** The path to the Soy file (used for messages only). */
  private final String path;


  /**
   * Creates a new {@code SoyFileSupplier} given an {@code InputSupplier} for the file content, as
   * well as the desired file path for messages.
   *
   * @param contentSupplier Supplier of a Reader for the Soy file content.
   * @param filePath The path to the Soy file (used for messages only).
   */
  public SoyFileSupplier(InputSupplier<? extends Reader> contentSupplier, String filePath) {
    this.contentSupplier = contentSupplier;
    this.path = filePath;
  }


  /**
   * Creates a new {@code SoyFileSupplier} given a {@code File}.
   *
   * @param inputFile The Soy file.
   */
  public SoyFileSupplier(File inputFile) {
    this(Files.newReaderSupplier(inputFile, Charsets.UTF_8), inputFile.getPath());
  }


  /**
   * Creates a new {@code SoyFileSupplier} given a resource {@code URL}, as well as the desired file
   * path for messages.
   *
   * @param inputFileUrl The URL of the Soy file.
   * @param filePath The path to the Soy file (used for messages only).
   */
  public SoyFileSupplier(URL inputFileUrl, String filePath) {
    this(Resources.newReaderSupplier(inputFileUrl, Charsets.UTF_8), filePath);
  }


  /**
   * Creates a new {@code SoyFileSupplier} given a resource {@code URL}.
   *
   * <p> Important: This function assumes that the desired file path is returned by
   * {@code inputFileUrl.toString()}. If this is not the case, please use
   * {@link #SoyFileSupplier(URL, String)} instead.
   *
   * @see #SoyFileSupplier(URL, String)
   * @param inputFileUrl The URL of the Soy file.
   */
  public SoyFileSupplier(URL inputFileUrl) {
    this(Resources.newReaderSupplier(inputFileUrl, Charsets.UTF_8), inputFileUrl.toString());
  }


  /**
   * Creates a new {@code SoyFileSupplier} given the file content provided as a string, as well as
   * the desired file path for messages.
   *
   * @param content The Soy file content.
   * @param filePath The path to the Soy file (used for messages only).
   */
  public SoyFileSupplier(CharSequence content, String filePath) {
    this(CharStreams.newReaderSupplier(content.toString()), filePath);
  }


  /**
   * Returns a {@code Reader} for the Soy file content.
   * @throws IOException If there is an error obtaining the {@code Reader}.
   */
  @Override public Reader getInput() throws IOException {
    return contentSupplier.getInput();
  }


  /** Returns the file path (used for messages only). */
  public String getPath() {
    return path;
  }

}
