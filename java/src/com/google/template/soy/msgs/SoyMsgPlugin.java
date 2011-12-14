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

import com.google.template.soy.msgs.SoyMsgBundleHandler.OutputFileOptions;


/**
 * Plugin for implementing a specific message file format.
 *
 */
public interface SoyMsgPlugin {


  /**
   * Builds the content of an output message file (one that will be sent for translation) from a
   * given message bundle object containing messages extracted from source files.
   *
   * @param msgBundle The bundle of messages extracted from source files.
   * @param options The options to use for generating the output message file (e.g. the source
   *     locale/language of the messages). Not all options will apply to all message plugins.
   * @return The content of the generated output file.
   * @throws SoyMsgException If there was an error building the file content.
   */
  public CharSequence generateExtractedMsgsFile(SoyMsgBundle msgBundle, OutputFileOptions options)
      throws SoyMsgException;


  /**
   * Parses the content of an input message file (one that has been translated) and builds a
   * message bundle object.
   *
   * @param inputFileContent The content of the translated message file.
   * @return The message bundle object built from the message file.
   * @throws SoyMsgException If there was an error parsing the file content.
   */
  public SoyMsgBundle parseTranslatedMsgsFile(String inputFileContent)
      throws SoyMsgException;

}
