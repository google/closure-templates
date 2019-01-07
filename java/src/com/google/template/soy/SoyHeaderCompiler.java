/*
 * Copyright 2018 Google Inc.
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

import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.soytree.CompilationUnit;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;
import org.kohsuke.args4j.Option;

/**
 * Executable for compiling a set of Soy files into CompilationUnit proto.
 *
 * <p>A compilation unit represents all the extracted TemplateMetadata instances for a single
 * library. This serves as an intermediate build artifact that later compilations can use in
 * preference to parsing dependencies. This improves overall compiler performance by making builds
 * more cacheable.
 */
final class SoyHeaderCompiler extends AbstractSoyCompiler {
  @Option(
      name = "--output",
      required = true,
      usage =
          "[Required] The file name of the output file to be written.  Each compiler"
              + " invocation will produce exactly one file containing all the TemplateMetadata")
  private File output;

  SoyHeaderCompiler(PluginLoader loader, SoyInputCache cache) {
    super(loader, cache);
  }

  SoyHeaderCompiler() {}

  @Override
  protected void compile(SoyFileSet.Builder sfsBuilder) throws IOException {
    ParseResult result = sfsBuilder.build().compileMinimallyForHeaders();
    CompilationUnit unit =
        TemplateMetadataSerializer.compilationUnitFromFileSet(result.fileSet(), result.registry());
    // some small tests revealed about a 5x compression ratio.  This is likely due to template names
    // sharing common prefixes and repeated parameter names and types.
    try (OutputStream os =
        new GZIPOutputStream(new FileOutputStream(output), /* buffer size */ 64 * 1024)) {
      unit.writeTo(os);
    }
  }
}
