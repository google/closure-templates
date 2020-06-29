/*
 * Copyright 2017 Google Inc.
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

import com.google.common.io.ByteSource;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

/**
 * A SoyJarWriter can populate a jar file with a number of entries ensuring that there is a proper
 * manifest and that all timestamps are stripped from the outputs which helps ensure determinism.
 */
public final class SoyJarFileWriter implements AutoCloseable {

  private final DeterministicJarOutputStream stream;

  public SoyJarFileWriter(OutputStream stream) throws IOException {
    // Use a buffer size of 64K, this is somewhat arbitrary, but is consistent with buffer sizes
    // used elsewhere in the compiler.
    this.stream = new DeterministicJarOutputStream(new BufferedOutputStream(stream, 64 * 1024));
  }

  /** Writes a single entry to the jar. */
  public void writeEntry(String path, ByteSource contents) throws IOException {
    stream.putNextEntry(new ZipEntry(path));
    contents.copyTo(stream);
    stream.closeEntry();
  }

  @Override
  public void close() throws IOException {
    stream.close();
  }

  private static final class DeterministicJarOutputStream extends JarOutputStream {
    DeterministicJarOutputStream(OutputStream outputStream) throws IOException {
      super(outputStream, standardSoyJarManifest());
    }

    @Override
    public void putNextEntry(ZipEntry ze) throws IOException {
      ze.setTime(0); // set an explicit timestamp to zero so we generate deterministic outputs
      super.putNextEntry(ze);
    }
  }

  /** Returns a simple jar manifest. */
  private static Manifest standardSoyJarManifest() {
    Manifest mf = new Manifest();
    mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    mf.getMainAttributes().put(new Attributes.Name("Created-By"), "soy");
    return mf;
  }
}
