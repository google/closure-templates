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

/**
 * Executable for compiling a set of Soy files into a set of files with mods that replace SoyJS with
 * IDOM
 */
public final class SoyToIncrementalDomInteropSrcCompiler extends AbstractSoyCompiler {

  private final PerInputOutputFiles outputFiles =
      new PerInputOutputFiles("useidom.js", PerInputOutputFiles.JS_JOINER);

  /**
   * @param args Should contain command-line flags and the list of paths to the Soy files.
   */
  public static void main(String[] args) {
    new SoyToIncrementalDomInteropSrcCompiler().runMain(args);
  }

  SoyToIncrementalDomInteropSrcCompiler(PluginLoader loader, SoyInputCache cache) {
    super(loader, cache);
  }

  SoyToIncrementalDomInteropSrcCompiler() {}

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
    outputFiles.writeFiles(
        srcs, sfs.compileToIncrementalDomInteropSrcInternal(), /* locale= */ null);
  }
}
