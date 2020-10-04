/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.soytree;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.template.soy.soytree.TemplateRegistrySubject.assertThatRegistry;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.template.soy.SoyFileSetParser.CompilationUnitAndKind;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.TemplateMetadataSerializer;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.shared.SoyGeneralOptions;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link TemplateRegistry}.
 *
 * @author brndn@google.com (Brendan Linn)
 */
@RunWith(JUnit4.class)
public final class TemplateRegistryTest {

  private static final SourceFilePath FILE_PATH = SourceFilePath.create("example.soy");

  private static final ImmutableList<CommandTagAttribute> NO_ATTRS = ImmutableList.of();
  private static final ErrorReporter FAIL = ErrorReporter.exploding();

  @Test
  public void testSimple() {
    TemplateRegistry registry =
        SoyFileSetParserBuilder.forSuppliers(
                SoyFileSupplier.Factory.create(
                    "{namespace ns}\n"
                        + "/** Simple template. */\n"
                        + "{template .foo}\n"
                        + "{/template}\n"
                        + "/** Simple deltemplate. */\n"
                        + "{deltemplate bar.baz}\n"
                        + "{/deltemplate}",
                    FILE_PATH))
            .parse()
            .registry();
    assertThatRegistry(registry)
        .containsBasicTemplate("ns.foo")
        .definedAt(new SourceLocation(FILE_PATH, 3, 1, 4, 11));
    assertThatRegistry(registry).doesNotContainBasicTemplate("foo");
    assertThatRegistry(registry)
        .containsDelTemplate("bar.baz")
        .definedAt(new SourceLocation(FILE_PATH, 6, 1, 7, 14));
    assertThatRegistry(registry).doesNotContainDelTemplate("ns.bar.baz");
  }

  /**
   * Verify that file registries are merged properly when two files have the same name. This is
   * important for cases where dummy names are used and may collide.
   */
  @Test
  public void testFilesWithSameNames() {

    // First, build and parse a file, and turn it into a "compilation unit".
    ParseResult dependencyParseResult =
        SoyFileSetParserBuilder.forSuppliers(
                SoyFileSupplier.Factory.create(
                    "{namespace ns}\n"
                        + "/** Simple template. */\n"
                        + "{template .foo}\n"
                        + "{/template}\n"
                        + "/** Simple deltemplate. */\n"
                        + "{deltemplate bar.baz}\n"
                        + "{/deltemplate}",
                    FILE_PATH))
            .parse();
    CompilationUnitAndKind dependencyCompilationUnit =
        CompilationUnitAndKind.create(
            SoyFileKind.DEP,
            SourceFilePath.create("example_header.soy"),
            TemplateMetadataSerializer.compilationUnitFromFileSet(
                dependencyParseResult.fileSet(), dependencyParseResult.registry()));

    // Now, parse another file with the same name, and feed the previous compilation unit in as a
    // dependency.
    TemplateRegistry registry =
        SoyFileSetParserBuilder.forSuppliers(
                SoyFileSupplier.Factory.create(
                    "{namespace ns}\n"
                        + "/** Simple template. */\n"
                        + "{template .foo2}\n"
                        + "{/template}\n"
                        + "/** Simple deltemplate. */\n"
                        + "{deltemplate bar.baz2}\n"
                        + "{/deltemplate}",
                    FILE_PATH))
            .addCompilationUnits(ImmutableList.of(dependencyCompilationUnit))
            .options(new SoyGeneralOptions().setAllowExternalCalls(false))
            .build()
            .parse()
            .registry();

    // Now, make sure that the final registry was merged properly and all the templates from both
    // files were retained.
    assertThatRegistry(registry)
        .containsBasicTemplate("ns.foo")
        .definedAt(new SourceLocation(FILE_PATH));
    assertThatRegistry(registry)
        .containsBasicTemplate("ns.foo2")
        .definedAt(new SourceLocation(FILE_PATH, 3, 1, 4, 11));
    assertThatRegistry(registry).doesNotContainBasicTemplate("foo");
    assertThatRegistry(registry)
        .containsDelTemplate("bar.baz")
        .definedAt(new SourceLocation(FILE_PATH));
    assertThatRegistry(registry)
        .containsDelTemplate("bar.baz2")
        .definedAt(new SourceLocation(FILE_PATH, 6, 1, 7, 14));
    assertThatRegistry(registry).doesNotContainDelTemplate("ns.bar.baz");
  }

  @Test
  public void testBasicTemplatesWithSameNamesInDifferentFiles() {
    TemplateRegistry registry =
        SoyFileSetParserBuilder.forSuppliers(
                SoyFileSupplier.Factory.create(
                    "{namespace ns}\n"
                        + "/** Template. */\n"
                        + "{template .foo}\n"
                        + "{/template}\n",
                    SourceFilePath.create("bar.soy")),
                SoyFileSupplier.Factory.create(
                    "{namespace ns2}\n"
                        + "/** Template. */\n"
                        + "{template .foo}\n"
                        + "{/template}\n",
                    SourceFilePath.create("baz.soy")))
            .parse()
            .registry();

    assertThatRegistry(registry)
        .containsBasicTemplate("ns.foo")
        .definedAt(new SourceLocation(SourceFilePath.create("bar.soy"), 3, 1, 4, 11));
    assertThatRegistry(registry)
        .containsBasicTemplate("ns2.foo")
        .definedAt(new SourceLocation(SourceFilePath.create("baz.soy"), 3, 1, 4, 11));
  }

  @Test
  public void testDelTemplates() {
    TemplateRegistry registry =
        SoyFileSetParserBuilder.forSuppliers(
                SoyFileSupplier.Factory.create(
                    "{namespace ns}\n"
                        + "/** Deltemplate. */\n"
                        + "{deltemplate foo.bar}\n"
                        + "{/deltemplate}",
                    SourceFilePath.create("foo.soy")),
                SoyFileSupplier.Factory.create(
                    "{delpackage foo}\n"
                        + "{namespace ns}\n"
                        + "/** Deltemplate. */\n"
                        + "{deltemplate foo.bar}\n"
                        + "{/deltemplate}",
                    SourceFilePath.create("bar.soy")))
            .parse()
            .registry();

    assertThatRegistry(registry)
        .containsDelTemplate("foo.bar")
        .definedAt(new SourceLocation(SourceFilePath.create("foo.soy"), 3, 1, 4, 14));
    assertThatRegistry(registry)
        .containsDelTemplate("foo.bar")
        .definedAt(new SourceLocation(SourceFilePath.create("bar.soy"), 4, 1, 5, 14));
  }

  @Test
  public void testDuplicateBasicTemplates() {
    String file =
        "{namespace ns}\n"
            + "/** Foo. */\n"
            + "{template .foo}\n"
            + "{/template}\n"
            + "/** Foo. */\n"
            + "{template .foo}\n"
            + "{/template}\n";
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    SoyFileSetParserBuilder.forFileContents(file).errorReporter(errorReporter).parse();
    assertThat(errorReporter.getErrors()).hasSize(1);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo("Template/element 'ns.foo' already defined at no-path:3:1-4:11.");
  }

  @Test
  public void testDuplicateBasicTemplates_differentFiles() {
    String file = "{namespace ns}\n" + "/** Foo. */\n" + "{template .foo}\n" + "{/template}\n";

    String file2 = "{namespace ns}\n" + "/** Foo. */\n" + "{template .foo}\n" + "{/template}\n";

    ErrorReporter errorReporter = ErrorReporter.createForTest();
    SoyFileSetParserBuilder.forFileContents(file, file2).errorReporter(errorReporter).parse();
    assertThat(errorReporter.getErrors()).hasSize(1);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo("Template/element 'ns.foo' already defined at no-path:3:1-4:11.");
  }

  @Test
  public void testDuplicateDefaultDeltemplates() {
    String file =
        "{namespace ns}\n"
            + "/** Foo. */\n"
            + "{deltemplate foo.bar}\n"
            + "{/deltemplate}\n"
            + "/** Foo. */\n"
            + "{deltemplate foo.bar}\n"
            + "{/deltemplate}\n";
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    SoyFileSetParserBuilder.forFileContents(file).errorReporter(errorReporter).parse();
    assertThat(errorReporter.getErrors()).hasSize(1);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo(
            "Delegate template 'foo.bar' already has a default defined at no-path:3:1-4:14.");
  }

  @Test
  public void testDuplicateDefaultDeltemplates_differentFiles() {
    String file =
        "{namespace ns}\n" + "/** Foo. */\n" + "{deltemplate foo.bar}\n" + "{/deltemplate}\n";

    String file2 =
        "{namespace ns}\n" + "/** Foo. */\n" + "{deltemplate foo.bar}\n" + "{/deltemplate}\n";

    ErrorReporter errorReporter = ErrorReporter.createForTest();
    SoyFileSetParserBuilder.forFileContents(file, file2).errorReporter(errorReporter).parse();
    assertThat(errorReporter.getErrors()).hasSize(1);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo(
            "Delegate template 'foo.bar' already has a default defined at no-path:3:1-4:14.");
  }

  @Test
  public void testDelTemplateHasSameNameAsTemplate() {
    String file =
        "{namespace ns}\n"
            + "/** Foo. */\n"
            + "{deltemplate ns.foo}\n"
            + "{/deltemplate}\n"
            + "/** Foo. */\n"
            + "{template .foo}\n"
            + "{/template}\n";
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    SoyFileSetParserBuilder.forFileContents(file).errorReporter(errorReporter).parse();
    assertThat(errorReporter.getErrors()).hasSize(1);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo(
            "Found deltemplate ns.foo with the same name as a template/element at"
                + " no-path:6:1-7:11.");
  }

  @Test
  public void testDelTemplateHasSameNameAsTemplate_differentFiles() {
    String file =
        "{namespace ns}\n" + "/** Foo. */\n" + "{deltemplate ns.foo}\n" + "{/deltemplate}\n";

    String file2 = "{namespace ns}\n" + "/** Foo. */\n" + "{template .foo}\n" + "{/template}\n";

    ErrorReporter errorReporter = ErrorReporter.createForTest();
    SoyFileSetParserBuilder.forFileContents(file, file2).errorReporter(errorReporter).parse();
    assertThat(errorReporter.getErrors()).hasSize(1);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo(
            "Found deltemplate ns.foo with the same name as a template/element at"
                + " no-path-2:3:1-4:11.");
  }

  @Test
  public void testGetMetadata() {
    String fileContents =
        "{namespace ns}\n"
            + "/** Foo. */\n"
            + "{deltemplate ns.foo}\n"
            + "{/deltemplate}\n"
            + "/** Foo. */\n"
            + "{template .bar}\n"
            + "{/template}\n";

    String file2Contents =
        "{delpackage foo}"
            + "{namespace ns3}\n"
            + "/** Foo. */\n"
            + "{deltemplate ns.foo}\n"
            + "{/deltemplate}\n";
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    ParseResult parseResult =
        SoyFileSetParserBuilder.forFileContents(fileContents, file2Contents)
            .errorReporter(errorReporter)
            .parse();

    TemplateRegistry registry = parseResult.registry();

    TemplateNode firstTemplate = (TemplateNode) parseResult.fileSet().getChild(0).getChild(0);
    TemplateNode secondTemplate = (TemplateNode) parseResult.fileSet().getChild(0).getChild(1);

    // Make sure this returns the metadata for the deltemplate in file #1, not #2.
    assertThat(registry.getMetadata(firstTemplate))
        .isEqualTo(TemplateMetadata.fromTemplate(firstTemplate));

    // Sanity check getMetadata for a regular template.
    assertThat(registry.getMetadata(secondTemplate))
        .isEqualTo(TemplateMetadata.fromTemplate(secondTemplate));
  }

  @Test
  public void testGetAllTemplates() {
    String file1Contents =
        "{namespace ns}\n"
            + "/** Foo. */\n"
            + "{deltemplate ns.foo}\n"
            + "{/deltemplate}\n"
            + "/** Foo. */\n"
            + "{template .bar}\n"
            + "{/template}\n";

    String file2Contents =
        "{namespace ns2}\n"
            + "/** Foo. */\n"
            + "{deltemplate ns2.foo}\n"
            + "{/deltemplate}\n"
            + "/** Foo. */\n"
            + "{template .bar}\n"
            + "{/template}\n";

    String file3Contents =
        "{delpackage foo}"
            + "{namespace ns3}\n"
            + "/** Foo. */\n"
            + "{deltemplate ns.foo}\n"
            + "{/deltemplate}\n";
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    ParseResult parseResult =
        SoyFileSetParserBuilder.forFileContents(file1Contents, file2Contents, file3Contents)
            .errorReporter(errorReporter)
            .parse();

    SoyFileNode file1 = parseResult.fileSet().getChild(0);
    SoyFileNode file2 = parseResult.fileSet().getChild(1);
    SoyFileNode file3 = parseResult.fileSet().getChild(2);

    TemplateMetadata file1Template1 =
        TemplateMetadata.fromTemplate((TemplateNode) file1.getChild(0));
    TemplateMetadata file1Template2 =
        TemplateMetadata.fromTemplate((TemplateNode) file1.getChild(1));

    TemplateMetadata file2Template1 =
        TemplateMetadata.fromTemplate((TemplateNode) file2.getChild(0));
    TemplateMetadata file2Template2 =
        TemplateMetadata.fromTemplate((TemplateNode) file2.getChild(1));

    TemplateMetadata file3Template1 =
        TemplateMetadata.fromTemplate((TemplateNode) file3.getChild(0));

    TemplateRegistry registry = parseResult.registry();
    assertThat(registry.getAllTemplates())
        .containsExactlyElementsIn(
            ImmutableList.of(
                file1Template1, file1Template2, file2Template1, file2Template2, file3Template1));
  }

  @Test
  public void testDuplicateDeltemplatesInSameDelpackage() {
    String file =
        "{delpackage foo}\n"
            + "{namespace ns}\n"
            + "/** Foo. */\n"
            + "{deltemplate foo.bar}\n"
            + "{/deltemplate}\n"
            + "/** Foo. */\n"
            + "{deltemplate foo.bar}\n"
            + "{/deltemplate}\n";
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    SoyFileSetParserBuilder.forFileContents(file).errorReporter(errorReporter).parse();
    assertThat(errorReporter.getErrors()).hasSize(1);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo(
            "Delegate template 'foo.bar' already defined in delpackage foo: no-path:4:1-5:14");
  }

  @Test
  public void testDuplicateDeltemplatesInSameDelpackage_differentFiles() {
    String file =
        "{delpackage foo}\n"
            + "{namespace ns}\n"
            + "/** Foo. */\n"
            + "{deltemplate foo.bar}\n"
            + "{/deltemplate}\n";

    String file2 =
        "{delpackage foo}\n"
            + "{namespace ns}\n"
            + "/** Foo. */\n"
            + "{deltemplate foo.bar}\n"
            + "{/deltemplate}\n";
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    SoyFileSetParserBuilder.forFileContents(file, file2).errorReporter(errorReporter).parse();
    assertThat(errorReporter.getErrors()).hasSize(1);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo(
            "Delegate template 'foo.bar' already defined in delpackage foo: no-path:4:1-5:14");
  }

  @Test
  public void testGetCallContentKind_basicTemplate() {
    TemplateRegistry registry =
        SoyFileSetParserBuilder.forSuppliers(
                SoyFileSupplier.Factory.create(
                    "{namespace ns}\n"
                        + "/** Simple template. */\n"
                        + "{template .foo kind=\"attributes\"}\n"
                        + "{/template}\n",
                    FILE_PATH))
            .parse()
            .registry();

    CallBasicNode node =
        new CallBasicNode(
            0,
            SourceLocation.UNKNOWN,
            SourceLocation.UNKNOWN,
            new TemplateLiteralNode(
                Identifier.create("ns.foo", SourceLocation.UNKNOWN), SourceLocation.UNKNOWN, false),
            NO_ATTRS,
            false,
            FAIL);
    node.getCalleeExpr().setType(registry.getBasicTemplateOrElement("ns.foo").getTemplateType());
    assertThat(registry.getCallContentKind(node)).hasValue(SanitizedContentKind.ATTRIBUTES);
  }

  @Test
  public void testGetCallContentKind_basicTemplateMissing() {
    TemplateRegistry registry =
        SoyFileSetParserBuilder.forSuppliers(
                SoyFileSupplier.Factory.create(
                    "{namespace ns}\n"
                        + "/** Simple template. */\n"
                        + "{template .foo kind=\"attributes\"}\n"
                        + "{/template}\n",
                    FILE_PATH))
            .parse()
            .registry();
    CallBasicNode node =
        new CallBasicNode(
            0,
            SourceLocation.UNKNOWN,
            SourceLocation.UNKNOWN,
            new TemplateLiteralNode(
                Identifier.create("ns.moo", SourceLocation.UNKNOWN), SourceLocation.UNKNOWN, false),
            NO_ATTRS,
            false,
            FAIL);
    assertThat(registry.getCallContentKind(node)).isEmpty();
  }

  @Test
  public void testGetCallContentKind_delTemplate() {
    TemplateRegistry registry =
        SoyFileSetParserBuilder.forSuppliers(
                SoyFileSupplier.Factory.create(
                    "{namespace ns}\n"
                        + "/** Simple template. */\n"
                        + "{deltemplate ns.foo kind=\"attributes\"}\n"
                        + "{/deltemplate}\n",
                    FILE_PATH))
            .parse()
            .registry();
    CallDelegateNode node =
        new CallDelegateNode(
            0,
            SourceLocation.UNKNOWN,
            SourceLocation.UNKNOWN,
            Identifier.create("ns.foo", SourceLocation.UNKNOWN),
            NO_ATTRS,
            false,
            FAIL);
    assertThat(registry.getCallContentKind(node)).hasValue(SanitizedContentKind.ATTRIBUTES);
  }

  @Test
  public void testGetCallContentKind_delTemplateMissing() {
    TemplateRegistry registry =
        SoyFileSetParserBuilder.forSuppliers(
                SoyFileSupplier.Factory.create(
                    "{namespace ns}\n"
                        + "/** Simple template. */\n"
                        + "{deltemplate ns.foo kind=\"attributes\"}\n"
                        + "{/deltemplate}\n",
                    FILE_PATH))
            .parse()
            .registry();
    CallDelegateNode node =
        new CallDelegateNode(
            0,
            SourceLocation.UNKNOWN,
            SourceLocation.UNKNOWN,
            Identifier.create("ns.moo", SourceLocation.UNKNOWN),
            NO_ATTRS,
            false,
            FAIL);
    assertThat(registry.getCallContentKind(node)).isEmpty();
  }
}
