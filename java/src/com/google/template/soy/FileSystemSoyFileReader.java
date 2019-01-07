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

import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import java.io.File;
import java.io.FileNotFoundException;

/** Reads files from the file system. */
final class FileSystemSoyFileReader implements SoyCompilerFileReader {
  static final FileSystemSoyFileReader INSTANCE = new FileSystemSoyFileReader();

  private FileSystemSoyFileReader() {}

  @Override
  public ByteSource read(File f) throws FileNotFoundException {
    if (!f.exists()) {
      throw new FileNotFoundException(f.getPath());
    }
    return Files.asByteSource(f);
  }
}
