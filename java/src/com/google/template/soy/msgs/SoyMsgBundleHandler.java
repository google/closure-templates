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

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.template.soy.base.BaseUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.regex.Pattern;


/**
 * Handler for writing {@code SoyMsgBundle}s to file format and for creating {@code SoyMsgBundle}s
 * from files or resources.
 *
 * <p> Uses a {@code SoyMsgPlugin} to do the actual generation of the output data and the actual
 * parsing of the input data. The {@code SoyMsgPlugin} implements the specific message file format.
 *
 * @author Kai Huang
 */
public class SoyMsgBundleHandler {


  /**
   * Options for generating an output message bundle file.
   */
  public static class OutputFileOptions {

    /** The source locale string in the message bundle file. */
    private String sourceLocaleString;

    /** The target locale string in the message bundle file. */
    private String targetLocaleString;

    /**
     * This constructor sets default values for the source locale string and target locale string.
     * The source locale string defaults to "en" and the target locale string defaults to null.
     */
    public OutputFileOptions() {
      sourceLocaleString = "en";
      targetLocaleString = null;
    }

    /**
     * Sets the source locale string for an output message bundle file.
     * @param sourceLocaleString The source locale string.
     */
    public void setSourceLocaleString(String sourceLocaleString) {
      this.sourceLocaleString = sourceLocaleString;
    }

    /**
     * Returns the source locale string.
     */
    public String getSourceLocaleString() {
      return sourceLocaleString;
    }

    /**
     * Sets the target locale string f0or an output message bundle file.
     * @param targetLocaleString The target locale string.
     */
    public void setTargetLocaleString(String targetLocaleString) {
      this.targetLocaleString = targetLocaleString;
    }

    /**
     * Returns the target locale string.
     */
    public String getTargetLocaleString() {
      return targetLocaleString;
    }
  }


  /** For backwards-compatibility checking of file names that start with "en". */
  private static final Pattern FIRST_WORD_IS_EN_PATTERN = Pattern.compile("^en[^A-Za-z].*");


  private final SoyMsgPlugin msgPlugin;


  @Inject
  public SoyMsgBundleHandler(SoyMsgPlugin msgPlugin) {
    this.msgPlugin = msgPlugin;
  }


  /**
   * Converts a message bundle to output file format and writes it to file. Uses the default
   * output file options (source locale "en", no target locale).
   *
   * @param msgBundle The message bundle to write to file.
   * @param outputFile The output file to write to.
   * @throws IOException If there's an error while accessing the file.
   * @throws SoyMsgException If there's an error while processing the messages.
   */
  public void writeToFile(SoyMsgBundle msgBundle, File outputFile)
      throws IOException, SoyMsgException {

    writeToFile(msgBundle, new OutputFileOptions(), outputFile);
  }


  /**
   * Converts a message bundle to output file format and writes it to file.
   *
   * @param msgBundle The message bundle to write to file.
   * @param options The options for generating the output file (some of the options may be needed,
   *     depending on the SoyMsgPlugin used).
   * @param outputFile The output file to write to.
   * @throws IOException If there's an error while accessing the file.
   * @throws SoyMsgException If there's an error while processing the messages.
   */
   public void writeToFile(
      SoyMsgBundle msgBundle, OutputFileOptions options, File outputFile)
      throws IOException, SoyMsgException {

    CharSequence cs = msgPlugin.generateExtractedMsgsFile(msgBundle, options);
    BaseUtils.ensureDirsExistInPath(outputFile.getPath());
    Files.write(cs, outputFile, Charsets.UTF_8);
  }


  /**
   * Reads an input messages file and creates a SoyMsgBundle.
   *
   * @param inputFile The input file to read from.
   * @return The message bundle created from the messages file.
   * @throws IOException If there's an error while accessing the file.
   * @throws SoyMsgException If there's an error while processing the messages.
   */
  public SoyMsgBundle createFromFile(File inputFile) throws IOException, SoyMsgException {

    // TODO: This is for backwards-compatibility. Figure out how to get rid of this.
    // We special-case English locales because they often don't have translated files and falling
    // back to the Soy source should be fine.
    if (!inputFile.exists() && FIRST_WORD_IS_EN_PATTERN.matcher(inputFile.getName()).matches()) {
      return SoyMsgBundle.EMPTY;
    }

    try {
      String inputFileContent = Files.toString(inputFile, Charsets.UTF_8);
      return msgPlugin.parseTranslatedMsgsFile(inputFileContent);

    } catch (SoyMsgException sme) {
      sme.setFileOrResourceName(inputFile.toString());
      throw sme;
    }
  }


  /**
   * Reads an input messages resource and creates a SoyMsgBundle.
   *
   * @param inputResource The resource to read from.
   * @return The message bundle created from the messages resource.
   * @throws IOException If there's an error while accessing the resource.
   * @throws SoyMsgException If there's an error while processing the messages.
   */
  public SoyMsgBundle createFromResource(URL inputResource) throws IOException, SoyMsgException {

    try {
      String inputFileContent = Resources.toString(inputResource, Charsets.UTF_8);
      return msgPlugin.parseTranslatedMsgsFile(inputFileContent);

    } catch (SoyMsgException sme) {
      sme.setFileOrResourceName(inputResource.toString());
      throw sme;
    }
  }

}
