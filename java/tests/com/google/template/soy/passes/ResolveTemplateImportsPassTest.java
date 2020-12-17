/*
 * Copyright 2020 Google Inc.
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
package com.google.template.soy.passes;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.template.soy.SoyFileSetParser;
import com.google.template.soy.SoyFileSetParser.CompilationUnitAndKind;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.TemplateMetadataSerializer;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.shared.SoyGeneralOptions;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ResolveTemplateImportsPassTest {
  ErrorReporter errorReporter = ErrorReporter.createForTest();

  @Before
  public void setUp() {
    errorReporter = ErrorReporter.createForTest();
  }

  @Test
  public void testImportFromDepLibrary() throws Exception {
    CompilationUnitAndKind dependencyCompilationUnit =
        parseDep(
            Joiner.on("\n")
                .join("{namespace dep.namespace}", "{template .aTemplate}", " hi!", "{/template}"),
            SourceFilePath.create("foo.soy"));

    parseFileWithDeps(
        createSoyFileSupplier(
            Joiner.on("\n")
                .join(
                    "{namespace my.namespace}",
                    "import {aTemplate} from 'foo.soy';",
                    "{template .mainTemplate}",
                    " hi!",
                    "{/template}"),
            SourceFilePath.create("main.soy")),
        ImmutableList.of(dependencyCompilationUnit));
    assertThat(errorReporter.getErrors()).isEmpty();
  }

  @Test
  public void testImportFromDepLibrary_symbolInvalid() throws Exception {
    CompilationUnitAndKind dependencyCompilationUnit =
        parseDep(
            Joiner.on("\n")
                .join("{namespace dep.namespace}", "{template .aTemplate}", " hi!", "{/template}"),
            SourceFilePath.create("foo.soy"));

    parseFileWithDeps(
        createSoyFileSupplier(
            Joiner.on("\n")
                .join(
                    "{namespace my.namespace}",
                    "import {notATemplate} from 'foo.soy';",
                    "{template .aTemplate}",
                    " hi!",
                    "{/template}"),
            SourceFilePath.create("main.soy")),
        ImmutableList.of(dependencyCompilationUnit));

    assertThat(errorReporter.getErrors()).hasSize(1);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .contains("Unknown symbol notATemplate in foo.soy");
  }

  @Test
  public void testImportFromSameFileSet() throws Exception {
    parseFiles(
        ImmutableList.of(
            createSoyFileSupplier(
                Joiner.on("\n")
                    .join(
                        "{namespace my.namespace}",
                        "import {aTemplate} from 'foo.soy';",
                        "{template .mainTemplate}",
                        " hi!",
                        "{/template}"),
                SourceFilePath.create("main.soy")),
            createSoyFileSupplier(
                Joiner.on("\n")
                    .join(
                        "{namespace dep.namespace}",
                        "{template .aTemplate}",
                        " hi!",
                        "{/template}"),
                SourceFilePath.create("foo.soy"))));

    assertThat(errorReporter.getErrors()).isEmpty();
  }

  @Test
  public void testImportFromSameFileSet_symbolInvalid() throws Exception {
    parseFiles(
        ImmutableList.of(
            createSoyFileSupplier(
                Joiner.on("\n")
                    .join(
                        "{namespace my.namespace}",
                        "import {notATemplate} from 'foo.soy';",
                        "{template .aTemplate}",
                        " hi!",
                        "{/template}"),
                SourceFilePath.create("main.soy")),
            createSoyFileSupplier(
                Joiner.on("\n")
                    .join(
                        "{namespace dep.namespace}",
                        "{template .aTemplate}",
                        " hi!",
                        "{/template}"),
                SourceFilePath.create("foo.soy"))));

    assertThat(errorReporter.getErrors()).hasSize(1);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .contains("Unknown symbol notATemplate in foo.soy");
  }

  @Test
  public void testImport_unknownPath() throws Exception {
    parseFiles(
        ImmutableList.of(
            createSoyFileSupplier(
                Joiner.on("\n")
                    .join(
                        "{namespace my.namespace}",
                        "import {notATemplate} from 'foo.soy';",
                        "{template .aTemplate}",
                        " hi!",
                        "{/template}"),
                SourceFilePath.create("main.soy"))));

    assertThat(errorReporter.getErrors()).hasSize(1);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .contains("Unknown import dep foo.soy");
  }

  private CompilationUnitAndKind parseDep(String fileContents, SourceFilePath fileName) {
    SoyFileSetParser parserForDeps =
        createParserForFiles(ImmutableList.of(createSoyFileSupplier(fileContents, fileName)));
    ParseResult dependencyParseResult = parserForDeps.parse();
    return CompilationUnitAndKind.create(
        SoyFileKind.DEP,
        SourceFilePath.create("foo_unit.soy"),
        TemplateMetadataSerializer.compilationUnitFromFileSet(
            dependencyParseResult.fileSet(), dependencyParseResult.registry()));
  }

  private ParseResult parseFileWithDeps(
      SoyFileSupplier file, Iterable<CompilationUnitAndKind> deps) {
    return parseFilesWithDeps(ImmutableList.of(file), deps);
  }

  private ParseResult parseFilesWithDeps(
      Iterable<SoyFileSupplier> files, Iterable<CompilationUnitAndKind> deps) {
    return createParserForFilesWithDependencies(files, deps).parse();
  }

  private ParseResult parseFiles(Iterable<SoyFileSupplier> files) {
    return createParserForFilesWithDependencies(files, ImmutableList.of()).parse();
  }

  private static SoyFileSupplier createSoyFileSupplier(
      String soyFileContents, SourceFilePath fileName) {
    return SoyFileSupplier.Factory.create(soyFileContents, fileName);
  }

  private SoyFileSetParser createParserForFiles(Iterable<SoyFileSupplier> soyFiles) {
    return createParserForFilesWithDependencies(soyFiles, ImmutableList.of());
  }

  private SoyFileSetParser createParserForFilesWithDependencies(
      Iterable<SoyFileSupplier> soyFiles, Iterable<CompilationUnitAndKind> dependencies) {

    return SoyFileSetParserBuilder.forSuppliers(soyFiles)
        .addCompilationUnits(dependencies)
        .errorReporter(errorReporter)
        .options(new SoyGeneralOptions().setAllowExternalCalls(false))
        .build();
  }
}
