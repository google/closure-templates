/*
 * Copyright 2016 Google Inc.
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

import com.google.common.annotations.Beta;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.jssrc.SoyJsSrcOptions;

import org.kohsuke.args4j.Option;

import java.io.IOException;


/**
 * Executable for compiling a set of Soy files into corresponding Incremental DOM source files. This
 * generates JavaScript functions for templates with function calls describing the template content.
 * In order to use the generated code, you will need to provide the Incremental DOM library at
 * runtime.
 *
 * @see <a href="http://google.github.io/incremental-dom">docs</a>
 * @see <a href="https://github.com/google/incremental-dom">Github page</a>
 */
@Beta
public final class SoyToIncrementalDomSrcCompiler extends AbstractSoyCompiler {

  @Option(
    name = "--outputPathFormat",
    required = true,
    usage =
        "[Required] A format string that specifies how to build the path to each"
            + " output file. If not generating localized JS, then there will be one output"
            + " JS file (UTF-8) for each input Soy file. If generating localized JS, then"
            + " there will be one output JS file for each combination of input Soy file and"
            + " locale. The format string can include literal characters as well as the"
            + " placeholders {INPUT_PREFIX}, {INPUT_DIRECTORY}, {INPUT_FILE_NAME},"
            + " {INPUT_FILE_NAME_NO_EXT}, {LOCALE}, {LOCALE_LOWER_CASE}. Note"
            + " {LOCALE_LOWER_CASE} also turns dash into underscore, e.g. pt-BR becomes"
            + " pt_br."
  )
  private String outputPathFormat;

  @Option(
    name = "--shouldDeclareTopLevelNamespaces",
    usage =
        "[Only applicable when generating regular JS code to define namespaces (i.e."
            + " not generating goog.provide/goog.require).] When this option is set to"
            + " false, each generated JS file will not attempt to declare the top-level"
            + " name in its namespace, instead assuming the top-level name is already"
            + " declared in the global scope. E.g. for namespace aaa.bbb, the code will not"
            + " attempt to declare aaa, but will still define aaa.bbb if it's not already"
            + " defined."
  )
  private boolean shouldDeclareTopLevelNamespaces = false;

  @Option(
    name = "--shouldGenerateGoogModules",
    usage =
        "[Whether we should generate code to declare goog.modules]"
  )
  private boolean shouldGenerateGoogModules = true;

  @Option(
    name = "--useGoogIsRtlForBidiGlobalDir",
    usage =
        "[Only applicable if both --shouldGenerateGoogMsgDefs and"
            +
            " --shouldProvideRequireSoyNamespaces"
            +
            " is true]"
            + " Whether to determine the bidi global direction at template runtime by"
            + " evaluating goog.i18n.bidi.IS_RTL. Do not combine with --bidiGlobalDir."
  )
  private boolean useGoogIsRtlForBidiGlobalDir = true;

   /**
   * Compiles a set of Soy files into corresponding Incremental DOM source files.
   *
   * @param args Should contain command-line flags and the list of paths to the Soy files.
   * @throws IOException If there are problems reading the input files or writing the output file.
   * @throws SoySyntaxException If a syntax error is detected.
   */
  public static void main(final String[] args) throws IOException, SoySyntaxException {
    new SoyToIncrementalDomSrcCompiler().runMain(args);
  }

  private SoyToIncrementalDomSrcCompiler() {}
  @Override
  void compile(SoyFileSet.Builder sfsBuilder) throws IOException {
    sfsBuilder.setAllowExternalCalls(false);
    SoyFileSet sfs = sfsBuilder.build();
    // Create SoyJsSrcOptions.
    SoyJsSrcOptions jsSrcOptions = new SoyJsSrcOptions();
    jsSrcOptions.setShouldAllowDeprecatedSyntax(false);
    jsSrcOptions.setShouldProvideRequireSoyNamespaces(false);
    jsSrcOptions.setShouldProvideRequireJsFunctions(false);
    jsSrcOptions.setShouldProvideBothSoyNamespacesAndJsFunctions(false);
    jsSrcOptions.setShouldDeclareTopLevelNamespaces(shouldDeclareTopLevelNamespaces);
    jsSrcOptions.setShouldGenerateJsdoc(true);
    jsSrcOptions.setShouldGenerateGoogModules(shouldGenerateGoogModules);
    jsSrcOptions.setShouldGenerateGoogMsgDefs(true);
    jsSrcOptions.setGoogMsgsAreExternal(true);
    jsSrcOptions.setBidiGlobalDir(0);
    jsSrcOptions.setUseGoogIsRtlForBidiGlobalDir(useGoogIsRtlForBidiGlobalDir);
    sfs.compileToIncrementalDomSrcFiles(outputPathFormat, jsSrcOptions);
  }
}
