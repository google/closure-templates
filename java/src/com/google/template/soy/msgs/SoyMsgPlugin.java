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

package com.google.template.soy.msgs;

import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.msgs.SoyMsgBundleHandler.OutputFileOptions;

/**
 * Plugin for implementing a specific message file format.
 */
@Immutable
public interface SoyMsgPlugin {

  /**
   * Builds the content of an extracted messages file (source messages to be translated) from a
   * given message bundle object containing messages extracted from source files.
   *
   * @param msgBundle The bundle of messages extracted from source files.
   * @param options The options to use for generating the extracted messages file (e.g. the source
   *     locale/language of the messages). Not all options will apply to all message plugins.
   * @param errorReporter For reporting errors.
   * @return The content of the generated extracted messages file.
   * @throws SoyMsgException If there was an error building the file content.
   */
  CharSequence generateExtractedMsgsFile(
      SoyMsgBundle msgBundle, OutputFileOptions options, ErrorReporter errorReporter);

  /**
   * Parses the content of a translated messages file and builds a message bundle object.
   *
   * @param translatedMsgsFileContent The content of the translated messages file.
   * @return The message bundle object built from the messages file.
   * @throws SoyMsgException If there was an error parsing the file content.
   */
  SoyMsgBundle parseTranslatedMsgsFile(String translatedMsgsFileContent);
}
