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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.incrementaldomsrc.SoyIncrementalDomSrcOptions;
import org.kohsuke.args4j.Option;

/**
 * Executable for compiling a set of Soy files into corresponding Incremental DOM source files. This
 * generates JavaScript functions for templates with function calls describing the template content.
 * In order to use the generated code, you will need to provide the Incremental DOM library at
 * runtime.
 *
 * @see <a href="http://google.github.io/incremental-dom">docs</a>
 * @see <a href="https://github.com/google/incremental-dom">Github page</a>
 */
public final class SoyToIncrementalDomSrcCompiler extends AbstractSoyCompiler {

  @Option(
      name = "--dependOnCssHeader",
      usage =
          "When this option is used, the generated JS files will have a requirecss annotation for"
              + " the generated GSS header file.")
  private boolean dependOnCssHeader = false;

  @Option(
      name = "--googMsgsAreExternal",
      usage =
          "[Only applicable if --shouldGenerateGoogMsgDefs is true]"
              + " If this option is true, then we generate"
              + " \"var MSG_EXTERNAL_<soyGeneratedMsgId> = goog.getMsg(...);\"."
              + " If this option is false, then we generate"
              + " \"var MSG_UNNAMED_<uniquefier> = goog.getMsg(...);\"."
              + "  [Explanation of true value]"
              + " Set this option to true if your project is having Closure Templates do"
              + " message extraction (e.g. with SoyMsgExtractor) and then having the Closure"
              + " Compiler do translated message insertion."
              + "  [Explanation of false value]"
              + " Set this option to false if your project is having the Closure Compiler do"
              + " all of its localization, i.e. if you want the Closure Compiler to do both"
              + " message extraction and translated message insertion. A significant drawback"
              + " to this setup is that, if your templates are used from both JS and Java, you"
              + " will end up with two separate and possibly different sets of translations"
              + " for your messages.")
  private boolean googMsgsAreExternal = false;

  @Option(name = "--replaceXidNodes", usage = "")
  private boolean replaceXidNodes = false;

  private final PerInputOutputFiles outputFiles =
      new PerInputOutputFiles("idom.soy.js", PerInputOutputFiles.JS_JOINER);

  /**
   * Compiles a set of Soy files into corresponding Incremental DOM source files.
   *
   * @param args Should contain command-line flags and the list of paths to the Soy files.
   */
  public static void main(String[] args) {
    new SoyToIncrementalDomSrcCompiler().runMain(args);
  }

  SoyToIncrementalDomSrcCompiler(PluginLoader loader, SoyInputCache cache) {
    super(loader, cache);
  }

  SoyToIncrementalDomSrcCompiler() {}

  @Override
  protected void validateFlags() {
    outputFiles.validateFlags();
  }

  @Override
  Iterable<?> extraFlagsObjects() {
    return ImmutableList.of(outputFiles);
  }

  @Override
  protected void compile(SoyFileSet.Builder sfsBuilder) {
    SoyFileSet sfs = sfsBuilder.build();
    SoyIncrementalDomSrcOptions options = new SoyIncrementalDomSrcOptions();
    options.setDependOnCssHeader(dependOnCssHeader);
    options.setGoogMsgsAreExternal(googMsgsAreExternal);
    options.setReplaceXidNodes(replaceXidNodes);
    outputFiles.writeFiles(
        srcs, sfs.compileToIncrementalDomSrcInternal(options), /* locale= */ null);
  }
}
