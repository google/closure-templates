/*
 * Copyright 2023 Google Inc.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SourceLogicalPath;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.base.internal.QuoteStyle;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.soytree.ImportNode;
import com.google.template.soy.soytree.NamespaceDeclaration;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.ImportedVar;
import com.google.template.soy.types.ToggleImportType;
import com.google.template.soy.types.ToggleRegistry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ToggleImportProcessorTest {

  static class ImmutableSetMultimapToggleRegistry implements ToggleRegistry {
    private final ImmutableSetMultimap<SourceLogicalPath, String> filePathToToggleMap;

    public ImmutableSetMultimapToggleRegistry(
        ImmutableSetMultimap<SourceLogicalPath, String> filePathToToggleMap) {
      this.filePathToToggleMap = filePathToToggleMap;
    }

    @Override
    public ImmutableSet<String> getToggles(SourceLogicalPath path) {
      return filePathToToggleMap.get(path);
    }

    @Override
    public ImmutableSet<SourceLogicalPath> getPaths() {
      return filePathToToggleMap.keySet();
    }
  }

  private static final SoyFileNode EMPTY_SOY_FILE_NODE =
      new SoyFileNode(
          0,
          SourceLocation.UNKNOWN,
          new NamespaceDeclaration(
              Identifier.create("namespace", SourceLocation.UNKNOWN),
              ImmutableList.of(),
              ErrorReporter.exploding(),
              SourceLocation.UNKNOWN),
          TemplateNode.SoyFileHeaderInfo.EMPTY,
          ImmutableList.of());

  ErrorReporter errorReporter = ErrorReporter.createForTest();

  @Before
  public void setUp() {
    errorReporter = ErrorReporter.createForTest();
  }

  @Test
  public void testHandlesPaths() {
    ImmutableSetMultimapToggleRegistry registry =
        new ImmutableSetMultimapToggleRegistry(
            ImmutableSetMultimap.of(
                SourceLogicalPath.create("foo.css"), "toggle1",
                SourceLogicalPath.create("bar.css"), "toggle2"));

    ToggleImportProcessor processor = new ToggleImportProcessor(registry, errorReporter);
    assertThat(processor.handlesPath("foo.css")).isTrue();
    assertThat(processor.handlesPath("bar.css")).isTrue();
    assertThat(processor.handlesPath("baz.css")).isFalse();
  }

  @Test
  public void testGetAllPaths() {
    ImmutableSetMultimapToggleRegistry registry =
        new ImmutableSetMultimapToggleRegistry(
            ImmutableSetMultimap.of(
                SourceLogicalPath.create("foo.css"), "toggle1",
                SourceLogicalPath.create("bar.css"), "toggle2"));
    ToggleImportProcessor processor = new ToggleImportProcessor(registry, errorReporter);
    assertThat(processor.getAllPaths()).containsExactly("foo.css", "bar.css");
  }

  @Test
  public void testHandlesImports() {
    ImmutableSetMultimapToggleRegistry registry =
        new ImmutableSetMultimapToggleRegistry(
            ImmutableSetMultimap.of(SourceLogicalPath.create("foo.css"), "toggle1"));

    ImportedVar importedVar = forSymbol("toggle1");
    ImportNode importNode = forPath("foo.css", ImmutableList.of(importedVar));

    ToggleImportProcessor processor = new ToggleImportProcessor(registry, errorReporter);
    processor.handle(EMPTY_SOY_FILE_NODE, ImmutableList.of(importNode));

    assertThat(importNode.getImportType()).isEqualTo(ImportNode.ImportType.TOGGLE);

    assertThat(importedVar.type()).isInstanceOf(ToggleImportType.class);
    ToggleImportType importType = (ToggleImportType) importedVar.type();
    assertThat(importType.getKind()).isEqualTo(ToggleImportType.Kind.TOGGLE_TYPE);
    assertThat(importType.toString()).isEqualTo("toggle1 from foo.css");
  }

  @Test
  /* Test {import toggleNameA as toggleNameB} works correctly. If toggle registry has toggleNameA,
  we should be able to import toggleNameA as toggleNameB but not toggleNameB as toggleName A. */
  public void testHandlesImportsWithAlias() {
    ImmutableSetMultimapToggleRegistry registry =
        new ImmutableSetMultimapToggleRegistry(
            ImmutableSetMultimap.of(SourceLogicalPath.create("foo.css"), "toggle1"));

    ImportedVar importedVarCorrect = withAlias("toggle1", "firstToggle");
    ImportedVar importedVarBad = withAlias("firstToggle", "toggle1");
    ImportNode importNodeCorrect = forPath("foo.css", ImmutableList.of(importedVarCorrect));
    ImportNode importNodeBad = forPath("foo.css", ImmutableList.of(importedVarBad));

    ToggleImportProcessor processor = new ToggleImportProcessor(registry, errorReporter);
    processor.handle(EMPTY_SOY_FILE_NODE, ImmutableList.of(importNodeCorrect));
    assertThat(errorReporter.getErrors()).isEmpty();

    processor.handle(EMPTY_SOY_FILE_NODE, ImmutableList.of(importNodeBad));
    assertThat(errorReporter.getErrors()).hasSize(1);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .contains("Unknown symbol firstToggle in foo.css.");
  }

  @Test
  public void testRejectsModuleImports() {
    ImmutableSetMultimapToggleRegistry registry =
        new ImmutableSetMultimapToggleRegistry(
            ImmutableSetMultimap.of(SourceLogicalPath.create("foo.css"), "toggle1"));
    ImportedVar importedVar = forSymbol(ImportedVar.MODULE_IMPORT);
    ImportNode importNode = forPath("foo.css", ImmutableList.of(importedVar));

    ToggleImportProcessor processor = new ToggleImportProcessor(registry, errorReporter);
    processor.handle(EMPTY_SOY_FILE_NODE, ImmutableList.of(importNode));

    assertThat(errorReporter.getErrors()).hasSize(1);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .contains("Module-level toggle imports are forbidden. Import individual toggles by name.");
  }

  @Test
  public void testRejectsImportingUnknownSymbols() {
    ImmutableSetMultimapToggleRegistry registry =
        new ImmutableSetMultimapToggleRegistry(
            ImmutableSetMultimap.of(SourceLogicalPath.create("foo.css"), "toggle1"));
    ImportedVar importedVarA = forSymbol("unknownToggle");
    ImportNode importNodeA = forPath("foo.css", ImmutableList.of(importedVarA));

    ImportedVar importedVarB = forSymbol("toggle1");
    ImportNode importNodeB = forPath("bar.css", ImmutableList.of(importedVarB));

    ToggleImportProcessor processor = new ToggleImportProcessor(registry, errorReporter);
    processor.handle(EMPTY_SOY_FILE_NODE, ImmutableList.of(importNodeA, importNodeB));

    assertThat(errorReporter.getErrors()).hasSize(2);
    assertThat(errorReporter.getErrors().get(0).message())
        .contains("Unknown symbol unknownToggle in foo.css");
    assertThat(errorReporter.getErrors().get(1).message())
        .contains("Unknown symbol toggle1 in bar.css");
  }

  private ImportedVar forSymbol(String symbol) {
    return new ImportedVar(symbol, null, SourceLocation.UNKNOWN);
  }

  private ImportedVar withAlias(String symbol, String alias) {
    return new ImportedVar(symbol, alias, SourceLocation.UNKNOWN);
  }

  private ImportNode forPath(String path, ImmutableList<ImportedVar> importedVars) {
    return new ImportNode(
        0,
        SourceLocation.UNKNOWN,
        new StringNode(path, QuoteStyle.DOUBLE, SourceLocation.UNKNOWN),
        importedVars);
  }
}
