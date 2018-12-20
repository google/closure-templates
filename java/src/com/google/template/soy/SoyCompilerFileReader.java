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
import java.io.File;
import java.io.FileNotFoundException;

/** Provides a way for the compiler to read files. */
public interface SoyCompilerFileReader {
  /**
   * Reads the file with the given path and returns the contents.
   *
   * @throws FileNotFoundException if the file doesn't exist
   */
  ByteSource read(File path) throws FileNotFoundException;
}
