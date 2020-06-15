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
public final class SoyToIncrementalDomSrcCompiler extends AbstractSoyCompiler {

  private final PerInputOutputFiles outputFiles =
      new PerInputOutputFiles("idom.soy.js", PerInputOutputFiles.JS_JOINER);
  /**
   * Compiles a set of Soy files into corresponding Incremental DOM source files.
   *
   * @param args Should contain command-line flags and the list of paths to the Soy files.
   * @throws IOException If there are problems reading the input files or writing the output file.
   */
  public static void main(final String[] args) throws IOException {
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
  protected void compile(SoyFileSet.Builder sfsBuilder) throws IOException {
    SoyFileSet sfs = sfsBuilder.build();
    outputFiles.writeFiles(
        srcs,
        sfs.compileToIncrementalDomSrcInternal(new SoyIncrementalDomSrcOptions()),
        /*locale=*/ null);
  }
}
