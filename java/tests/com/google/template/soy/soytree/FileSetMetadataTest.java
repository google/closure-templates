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
import static com.google.template.soy.soytree.TemplateRegistrySubject.assertThatRegistry;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.soytree.Metadata.CompilationUnitAndKind;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link FileSetMetadata}. */
@RunWith(JUnit4.class)
public final class FileSetMetadataTest {

  private static final SourceFilePath FILE_PATH = SourceFilePath.forTest("example.soy");

  private static final ImmutableList<CommandTagAttribute> NO_ATTRS = ImmutableList.of();
  private static final ErrorReporter FAIL = ErrorReporter.exploding();

  @Test
  public void testSimple() {
    FileSetMetadata registry =
        SoyFileSetParserBuilder.forSuppliers(
                SoyFileSupplier.Factory.create(
                    "{namespace ns}\n"
                        + "/** Simple template. */\n"
                        + "{template foo}\n"
                        + "{/template}\n"
                        + "/** Simple modifiable. */\n"
                        + "{template baz modifiable='true'}\n"
                        + "{/template}",
                    FILE_PATH))
            .parse()
            .registry();
    assertThatRegistry(registry)
        .containsBasicTemplate("ns.foo")
        .definedAt(new SourceLocation(FILE_PATH, 3, 1, 4, 11));
    assertThatRegistry(registry).doesNotContainBasicTemplate("foo");
    assertThatRegistry(registry)
        .containsDelTemplate("ns.baz")
        .definedAt(new SourceLocation(FILE_PATH, 6, 1, 7, 11));
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
                        + "{template foo}\n"
                        + "{/template}\n"
                        + "/** Simple modifiable. */\n"
                        + "{template baz modifiable='true'}\n"
                        + "{/template}",
                    FILE_PATH))
            .parse();
    CompilationUnitAndKind dependencyCompilationUnit =
        Metadata.CompilationUnitAndKind.create(
            SoyFileKind.DEP,
            TemplateMetadataSerializer.compilationUnitFromFileSet(
                dependencyParseResult.fileSet(), dependencyParseResult.registry()));

    // Now, parse another file with the same name, and feed the previous compilation unit in as a
    // dependency.
    FileSetMetadata registry =
        SoyFileSetParserBuilder.forSuppliers(
                SoyFileSupplier.Factory.create(
                    "{namespace ns}\n"
                        + "/** Simple template. */\n"
                        + "{template foo2}\n"
                        + "{/template}\n"
                        + "/** Simple deltemplate. */\n"
                        + "{template baz2 modifiable='true'}\n"
                        + "{/template}",
                    FILE_PATH))
            .addCompilationUnits(ImmutableList.of(dependencyCompilationUnit))
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
        .containsDelTemplate("ns.baz")
        .definedAt(new SourceLocation(FILE_PATH));
    assertThatRegistry(registry)
        .containsDelTemplate("ns.baz2")
        .definedAt(new SourceLocation(FILE_PATH, 6, 1, 7, 11));
  }

  @Test
  public void testBasicTemplatesWithSameNamesInDifferentFiles() {
    FileSetMetadata registry =
        SoyFileSetParserBuilder.forSuppliers(
                SoyFileSupplier.Factory.create(
                    "{namespace ns}\n"
                        + "/** Template. */\n"
                        + "{template foo}\n"
                        + "{/template}\n",
                    SourceFilePath.forTest("bar.soy")),
                SoyFileSupplier.Factory.create(
                    "{namespace ns2}\n"
                        + "/** Template. */\n"
                        + "{template foo}\n"
                        + "{/template}\n",
                    SourceFilePath.forTest("baz.soy")))
            .parse()
            .registry();

    assertThatRegistry(registry)
        .containsBasicTemplate("ns.foo")
        .definedAt(new SourceLocation(SourceFilePath.forTest("bar.soy"), 3, 1, 4, 11));
    assertThatRegistry(registry)
        .containsBasicTemplate("ns2.foo")
        .definedAt(new SourceLocation(SourceFilePath.forTest("baz.soy"), 3, 1, 4, 11));
  }

  @Test
  public void testModifiableTemplates() {
    FileSetMetadata registry =
        SoyFileSetParserBuilder.forSuppliers(
                SoyFileSupplier.Factory.create(
                    "{namespace ns}\n"
                        + "/** Modifiable. */\n"
                        + "{template foo modifiable='true'}\n"
                        + "{/template}",
                    SourceFilePath.forTest("foo.soy")),
                SoyFileSupplier.Factory.create(
                    "{modname foo}\n"
                        + "{namespace ns2}\n"
                        + "import {foo} from 'foo.soy';\n"
                        + "/** Modifiable. */\n"
                        + "{template bar visibility='private' modifies='foo'}\n"
                        + "{/template}",
                    SourceFilePath.forTest("bar.soy")))
            .parse()
            .registry();

    assertThatRegistry(registry)
        .containsDelTemplate("ns.foo")
        .definedAt(new SourceLocation(SourceFilePath.forTest("foo.soy"), 3, 1, 4, 11));
    assertThatRegistry(registry)
        .containsDelTemplate("ns.foo")
        .definedAt(new SourceLocation(SourceFilePath.forTest("bar.soy"), 5, 1, 6, 11));
  }

  @Test
  public void testDuplicateBasicTemplates() {
    String file =
        "{namespace ns}\n"
            + "/** Foo. */\n"
            + "{template foo}\n"
            + "{/template}\n"
            + "/** Foo. */\n"
            + "{template foo}\n"
            + "{/template}\n";
    ErrorReporter errorReporter = ErrorReporter.create();
    SoyFileSetParserBuilder.forFileContents(file).errorReporter(errorReporter).parse();
    assertThat(errorReporter.getErrors()).hasSize(1);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo("Template name 'foo' conflicts with symbol defined at 3:11-3:13.");
  }

  @Test
  public void testDuplicateBasicTemplates_differentFiles() {
    String file = "{namespace ns}\n" + "/** Foo. */\n" + "{template foo}\n" + "{/template}\n";

    String file2 = "{namespace ns}\n" + "/** Foo. */\n" + "{template foo}\n" + "{/template}\n";

    ErrorReporter errorReporter = ErrorReporter.create();
    SoyFileSetParserBuilder.forFileContents(file, file2).errorReporter(errorReporter).parse();
    assertThat(errorReporter.getErrors()).hasSize(1);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo("Template/element 'ns.foo' already defined at no-path:3:1-4:11.");
  }

  @Test
  public void testDuplicateModifiableTemplates() {
    String file =
        "{namespace ns}\n"
            + "/** Foo. */\n"
            + "{template foo modifiable='true'}\n"
            + "{/template}\n"
            + "/** Foo. */\n"
            + "{template foo modifiable='true'}\n"
            + "{/template}\n";
    ErrorReporter errorReporter = ErrorReporter.create();
    SoyFileSetParserBuilder.forFileContents(file).errorReporter(errorReporter).parse();
    assertThat(errorReporter.getErrors()).hasSize(1);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo("Template name 'foo' conflicts with symbol defined at 3:11-3:13.");
  }

  @Test
  public void testGetMetadata() {
    String fileContents =
        "{namespace ns}\n"
            + "/** Foo. */\n"
            + "{template foo modifiable='true'}\n"
            + "{/template}\n"
            + "/** Foo. */\n"
            + "{template bar}\n"
            + "{/template}\n";

    String file2Contents =
        "{modname foo}"
            + "{namespace ns3}\n"
            + "import {foo} from 'foo.soy';\n"
            + "/** Foo. */\n"
            + "{template fooMod visibility='private' modifies='foo'}\n"
            + "{/template}\n";
    ErrorReporter errorReporter = ErrorReporter.create();
    ParseResult parseResult =
        SoyFileSetParserBuilder.forSuppliers(
                SoyFileSupplier.Factory.create(fileContents, SourceFilePath.forTest("foo.soy")),
                SoyFileSupplier.Factory.create(file2Contents, SourceFilePath.forTest("foo2.soy")))
            .errorReporter(errorReporter)
            .parse();
    FileSetMetadata registry = parseResult.registry();

    TemplateNode firstTemplate = (TemplateNode) parseResult.fileSet().getChild(0).getChild(0);
    TemplateNode secondTemplate = (TemplateNode) parseResult.fileSet().getChild(0).getChild(1);

    // Make sure this returns the metadata for the deltemplate in file #1, not #2.
    assertThat(registry.getTemplate(firstTemplate))
        .isEqualTo(TemplateMetadata.fromTemplate(firstTemplate));

    // Sanity check getMetadata for a regular template.
    assertThat(registry.getTemplate(secondTemplate))
        .isEqualTo(TemplateMetadata.fromTemplate(secondTemplate));
  }

  @Test
  public void testGetAllTemplates() {
    String file1Contents =
        "{namespace ns}\n"
            + "/** Foo. */\n"
            + "{template foo modifiable='true'}\n"
            + "{/template}\n"
            + "/** Foo. */\n"
            + "{template bar}\n"
            + "{/template}\n";

    String file2Contents =
        "{namespace ns2}\n"
            + "/** Foo. */\n"
            + "{template foo modifiable='true'}\n"
            + "{/template}\n"
            + "/** Foo. */\n"
            + "{template bar}\n"
            + "{/template}\n";

    String file3Contents =
        "{modname foo}"
            + "{namespace ns3}\n"
            + "import {foo} from 'foo.soy';\n"
            + "/** Foo. */\n"
            + "{template fooMod visibility='private' modifies='foo'}\n"
            + "{/template}\n";
    ErrorReporter errorReporter = ErrorReporter.create();
    ParseResult parseResult =
        SoyFileSetParserBuilder.forSuppliers(
                SoyFileSupplier.Factory.create(file1Contents, SourceFilePath.forTest("foo.soy")),
                SoyFileSupplier.Factory.create(file2Contents, SourceFilePath.forTest("foo2.soy")),
                SoyFileSupplier.Factory.create(file3Contents, SourceFilePath.forTest("foo3.soy")))
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
        TemplateMetadata.fromTemplate((TemplateNode) file3.getChild(1));

    FileSetMetadata registry = parseResult.registry();
    assertThat(registry.getAllTemplates())
        .containsExactlyElementsIn(
            ImmutableList.of(
                file1Template1, file1Template2, file2Template1, file2Template2, file3Template1));
  }

  @Test
  public void testDuplicateDeltemplatesInSameMod() {
    String file =
        "{modname foo}\n"
            + "{namespace ns}\n"
            + "/** Foo. */\n"
            + "{deltemplate foo.bar}\n"
            + "{/deltemplate}\n"
            + "/** Foo. */\n"
            + "{deltemplate foo.bar}\n"
            + "{/deltemplate}\n";
    ErrorReporter errorReporter = ErrorReporter.create();
    SoyFileSetParserBuilder.forFileContents(file).errorReporter(errorReporter).parse();
    assertThat(errorReporter.getErrors()).hasSize(1);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo(
            "Delegate/Modifies template 'foo.bar' already defined in mod foo: no-path:4:1-5:14");
  }

  @Test
  public void testDuplicateDeltemplatesInSameMod_differentFiles() {
    String file =
        "{modname foo}\n"
            + "{namespace ns}\n"
            + "/** Foo. */\n"
            + "{deltemplate foo.bar}\n"
            + "{/deltemplate}\n";

    String file2 =
        "{modname foo}\n"
            + "{namespace ns2}\n"
            + "/** Foo. */\n"
            + "{deltemplate foo.bar}\n"
            + "{/deltemplate}\n";
    ErrorReporter errorReporter = ErrorReporter.create();
    SoyFileSetParserBuilder.forFileContents(file, file2).errorReporter(errorReporter).parse();
    assertThat(errorReporter.getErrors()).hasSize(1);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo(
            "Delegate/Modifies template 'foo.bar' already defined in mod foo: no-path:4:1-5:14");
  }

  @Test
  public void testDuplicateModifiesTemplatesInSameMod() {
    String file1 = "{namespace nsmodifiable}{template foo modifiable='true'}{/template}";
    String file2 =
        "{modname foo}\n"
            + "{namespace ns}\n"
            + "import {foo} from 'foo.soy';\n"
            + "/** Foo. */\n"
            + "{template fooMod1 visibility='private' modifies='foo'}\n"
            + "{/template}\n"
            + "/** Foo. */\n"
            + "{template fooMod2 visibility='private' modifies='foo'}\n"
            + "{/template}\n";
    ErrorReporter errorReporter = ErrorReporter.create();
    SoyFileSetParserBuilder.forSuppliers(
            SoyFileSupplier.Factory.create(file1, SourceFilePath.forTest("foo.soy")),
            SoyFileSupplier.Factory.create(file2, SourceFilePath.forTest("foo2.soy")))
        .errorReporter(errorReporter)
        .parse();
    assertThat(errorReporter.getErrors()).hasSize(1);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo(
            "Delegate/Modifies template 'nsmodifiable.foo' already defined in mod foo:"
                + " foo2.soy:5:1-6:11");
  }

  @Test
  public void testDuplicateModifiesTemplatesInSameMod_differentFiles() {
    String file1 = "{namespace nsmodifiable}{template foo modifiable='true'}{/template}";
    String file2 =
        "{modname foo}\n"
            + "{namespace ns}\n"
            + "import {foo} from 'foo.soy';\n"
            + "/** Foo. */\n"
            + "{template fooMod1 visibility='private' modifies='foo'}\n"
            + "{/template}\n";
    String file3 =
        "{modname foo}\n"
            + "{namespace ns2}\n"
            + "import {foo} from 'foo.soy';\n"
            + "/** Foo. */\n"
            + "{template fooMod1 visibility='private' modifies='foo'}\n"
            + "{/template}\n";
    ErrorReporter errorReporter = ErrorReporter.create();
    SoyFileSetParserBuilder.forSuppliers(
            SoyFileSupplier.Factory.create(file1, SourceFilePath.forTest("foo.soy")),
            SoyFileSupplier.Factory.create(file2, SourceFilePath.forTest("foo2.soy")),
            SoyFileSupplier.Factory.create(file3, SourceFilePath.forTest("foo3.soy")))
        .errorReporter(errorReporter)
        .parse();
    assertThat(errorReporter.getErrors()).hasSize(1);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo(
            "Delegate/Modifies template 'nsmodifiable.foo' already defined in mod foo:"
                + " foo2.soy:5:1-6:11");
  }

  @Test
  public void testGetCallContentKind_basicTemplate() {
    FileSetMetadata registry =
        SoyFileSetParserBuilder.forSuppliers(
                SoyFileSupplier.Factory.create(
                    "{namespace ns}\n"
                        + "/** Simple template. */\n"
                        + "{template foo kind=\"attributes\"}\n"
                        + "{/template}\n",
                    FILE_PATH))
            .parse()
            .registry();

    CallBasicNode node =
        new CallBasicNode(
            0,
            SourceLocation.UNKNOWN,
            SourceLocation.UNKNOWN,
            TemplateLiteralNode.forVarRef(new VarRefNode("ns.foo", SourceLocation.UNKNOWN, null)),
            NO_ATTRS,
            false,
            FAIL);
    node.getCalleeExpr().setType(registry.getBasicTemplateOrElement("ns.foo").getTemplateType());
    assertThat(Metadata.getCallContentKind(registry, node))
        .hasValue(SanitizedContentKind.ATTRIBUTES);
  }

  @Test
  public void testGetCallContentKind_basicTemplateMissing() {
    FileSetMetadata registry =
        SoyFileSetParserBuilder.forSuppliers(
                SoyFileSupplier.Factory.create(
                    "{namespace ns}\n"
                        + "/** Simple template. */\n"
                        + "{template foo kind=\"attributes\"}\n"
                        + "{/template}\n",
                    FILE_PATH))
            .parse()
            .registry();
    CallBasicNode node =
        new CallBasicNode(
            0,
            SourceLocation.UNKNOWN,
            SourceLocation.UNKNOWN,
            TemplateLiteralNode.forVarRef(new VarRefNode("ns.moo", SourceLocation.UNKNOWN, null)),
            NO_ATTRS,
            false,
            FAIL);
    assertThat(Metadata.getCallContentKind(registry, node)).isEmpty();
  }

  @Test
  public void testGetCallContentKind_modifiableTemplate() {
    FileSetMetadata registry =
        SoyFileSetParserBuilder.forSuppliers(
                SoyFileSupplier.Factory.create(
                    "{namespace ns}\n"
                        + "/** Simple template. */\n"
                        + "{template foo modifiable='true' kind=\"attributes\"}\n"
                        + "{/template}\n",
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
    assertThat(Metadata.getCallContentKind(registry, node))
        .hasValue(SanitizedContentKind.ATTRIBUTES);
  }

  @Test
  public void testGetCallContentKind_modifiableTemplateMissing() {
    FileSetMetadata registry =
        SoyFileSetParserBuilder.forSuppliers(
                SoyFileSupplier.Factory.create(
                    "{namespace ns}\n"
                        + "/** Simple template. */\n"
                        + "{template foo modifiable='true' kind=\"attributes\"}\n"
                        + "{/template}\n",
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
    assertThat(Metadata.getCallContentKind(registry, node)).isEmpty();
  }
}
