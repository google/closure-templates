/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.jssrc.internal;

import com.google.template.soy.base.BaseUtils;

import java.io.File;

import javax.annotation.Nullable;


/**
 * Shared utilities specific to the JS Src backend.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class JsSrcUtils {


  /**
   * Builds a specific file path given a path format and the info needed for replacing placeholders.
   *
   * @param filePathFormat The format string defining how to build the file path.
   * @param locale The locale for the file path, or null if not applicable.
   * @param inputFilePath Only applicable if you need to replace the placeholders {INPUT_DIRECTORY},
   *     {INPUT_FILE_NAME}, and {INPUT_FILE_NAME_NO_EXT} (otherwise pass null). This is the full
   *     path of the input file (including the input path prefix).
   * @param inputPathPrefix The input path prefix, or empty string if none.
   * @return The output file path corresponding to the given input file path.
   */
  public static String buildFilePath(String filePathFormat, @Nullable String locale,
                                     @Nullable String inputFilePath, String inputPathPrefix) {

    String path = filePathFormat;

    if (locale != null) {
      path = path.replace("{LOCALE}", locale);
      path = path.replace("{LOCALE_LOWER_CASE}", locale.toLowerCase().replace('-', '_'));
    }

    path = path.replace("{INPUT_PREFIX}", inputPathPrefix);

    if (inputFilePath != null) {
      // Remove the prefix (if any) from the input file path.
      inputFilePath = inputFilePath.substring(inputPathPrefix.length());

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

    return path;
  }


  /**
   * Builds a version of the given string that has literal Unicode Format characters (Unicode
   * category "Cf") changed to valid JavaScript Unicode escapes (i.e. &92;u####). If the provided
   * string doesn't have any Unicode Format characters, then the same string is returned.
   *
   * @param str The string to escape.
   * @return A version of the given string that has literal Unicode Format characters (Unicode
   * category "Cf") changed to valid JavaScript Unicode escapes (i.e. &92;u####).
   */
  public static String escapeUnicodeFormatChars(String str) {

    int len = str.length();

    // Do a quick check first, because most strings do not contain Unicode format characters.
    boolean hasFormatChar = false;
    for (int i = 0; i < len; i++) {
      if (Character.getType(str.charAt(i)) == Character.FORMAT) {
        hasFormatChar = true;
        break;
      }
    }
    if (!hasFormatChar) {
      return str;
    }

    // Now we actually need to build a new string.
    StringBuilder out = new StringBuilder(len * 4 / 3);
    int codePoint;
    for (int i = 0; i < len; i += Character.charCount(codePoint)) {
      codePoint = str.codePointAt(i);
      if (Character.getType(codePoint) == Character.FORMAT) {
        BaseUtils.appendHexEscape(out, codePoint);
      } else {
        out.appendCodePoint(codePoint);
      }
    }
    return out.toString();
  }

}
