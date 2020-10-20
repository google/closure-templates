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

package com.google.template.soy.shared.internal;

import java.io.File;
import javax.annotation.Nullable;

/**
 * Private shared utils for main entry point classes (e.g. JsSrcMain) or classes with a main()
 * method.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class MainEntryPointUtils {

  private MainEntryPointUtils() {}

  /**
   * Builds a specific file path given a path format and the info needed for replacing placeholders.
   *
   * @param filePathFormat The format string defining how to build the file path.
   * @param locale The locale for the file path, or null if not applicable.
   * @param inputFilePath Only applicable if you need to replace the placeholders {INPUT_DIRECTORY},
   *     {INPUT_FILE_NAME}, and {INPUT_FILE_NAME_NO_EXT} (otherwise pass null). This is the full
   *     path of the input file (including the input path prefix).
   * @return The output file path corresponding to the given input file path.
   */
  public static String buildFilePath(
      String filePathFormat, @Nullable String locale, @Nullable String inputFilePath) {

    String path = filePathFormat;

    if (locale != null) {
      path = path.replace("{LOCALE}", locale);
      path = path.replace("{LOCALE_LOWER_CASE}", locale.toLowerCase().replace('-', '_'));
    }

    if (inputFilePath != null) {

      // Compute directory and file name.
      int lastSlashIndex = inputFilePath.lastIndexOf(File.separatorChar);
      String directory = inputFilePath.substring(0, lastSlashIndex + 1);
      String fileName = inputFilePath.substring(lastSlashIndex + 1);

      // Compute file name without extension.
      int lastDotIndex = fileName.lastIndexOf('.');
      if (lastDotIndex == -1) {
        lastDotIndex = fileName.length();
      }
      String fileNameNoExt = fileName.substring(0, lastDotIndex);

      // Substitute placeholders.
      path = path.replace("{INPUT_DIRECTORY}", directory);
      path = path.replace("{INPUT_FILE_NAME}", fileName);
      path = path.replace("{INPUT_FILE_NAME_NO_EXT}", fileNameNoExt);
    }

    // Remove redundant /'s if any placeholder representing a directory was empty.
    path = path.replace("//", "/");

    return path;
  }
}
