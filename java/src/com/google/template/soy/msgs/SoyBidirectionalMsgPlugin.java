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

package com.google.template.soy.msgs;

import com.google.template.soy.msgs.SoyMsgBundleHandler.OutputFileOptions;

/**
 * Plugin for implementing a specific message file format. Supports postprocessing of msgs files by
 * having methods to both generate and parse both extracted and translated msgs files.
 *
 */
public interface SoyBidirectionalMsgPlugin extends SoyMsgPlugin {

  // Note: generateExtractedMsgsFile() is defined in SoyMsgPlugin.

  /**
   * Parses the content of an extracted messages file (source messages to be translated) and builds
   * a message bundle object.
   *
   * <p>Currently, this method exists purely for consistency. There's currently no functionality in
   * Soy that uses this method.
   *
   * @param extractedMsgsFileContent The content of the extracted messages file.
   * @return The message bundle object built from the messages file.
   * @throws SoyMsgException If there was an error parsing the file content.
   */
  public SoyMsgBundle parseExtractedMsgsFile(String extractedMsgsFileContent)
      throws SoyMsgException;

  /**
   * Builds the content of a translated msgs file from a given message bundle object.
   *
   * <p>For example, the given message bundle may be the result of postprocessing a message bundle
   * parsed from an original translated msgs file.
   *
   * @param msgBundle The bundle of messages.
   * @param options The options to use for generating the translated msgs file. Not all options will
   *     apply to all message plugins.
   * @return The content of the generated translated msgs file.
   * @throws SoyMsgException If there was an error building the file content.
   */
  public CharSequence generateTranslatedMsgsFile(SoyMsgBundle msgBundle, OutputFileOptions options)
      throws SoyMsgException;

  // Note: parseTranslatedMsgsFile() is defined in SoyMsgPlugin.

}
