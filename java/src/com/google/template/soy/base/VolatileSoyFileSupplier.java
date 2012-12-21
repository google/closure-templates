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
import com.google.template.soy.internal.base.Pair;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;


/**
 * Record for one input Soy file whose content should be considered prone to change without
 * warning.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Mike Samuel
 */
public class VolatileSoyFileSupplier extends AbstractSoyFileSupplier {


  /** The file to read. */
  private final File file;


  /**
   * Creates a Soy file supplier whose content is backed by the given file which is prone to change
   * without warning.
   *
   * @param file The underlying file to read.
   * @param soyFileKind The kind of this input Soy file.
   */
  public VolatileSoyFileSupplier(File file, SoyFileKind soyFileKind) {
    super(soyFileKind, file.getPath());
    this.file = file;
  }


  @Override
  public boolean hasChangedSince(Version version) {
    if (!(version instanceof VolatileFileVersion)) {
      return true;
    }
    return file.lastModified() != ((VolatileFileVersion) version).lastModified;
  }


  @Override
  public Pair<Reader, Version> open() throws IOException {
    long lastModified = file.lastModified();
    return Pair.<Reader, Version>of(
        new BufferedReader(new InputStreamReader(new FileInputStream(file), Charsets.UTF_8)),
        new VolatileFileVersion(lastModified));
  }


  /**
   * A file version based on {@link File#lastModified}.
   * Like last modified
   */
  private static final class VolatileFileVersion implements Version {

    final long lastModified;


    VolatileFileVersion(long lastModified) {
      this.lastModified = lastModified;
    }


    @Override
    public boolean equals(Object other) {
      return other instanceof VolatileFileVersion &&
          lastModified == ((VolatileFileVersion) other).lastModified;
    }


    @Override
    public String toString() {
      return String.valueOf(lastModified);
    }

  }

}
