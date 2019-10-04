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

package com.google.template.soy.parseinfo.passes;

import static com.google.common.truth.Truth.assertThat;
import static com.google.template.soy.parseinfo.passes.GenerateParseInfoVisitor.JavaClassNameSource.GENERIC;
import static com.google.template.soy.parseinfo.passes.GenerateParseInfoVisitor.JavaClassNameSource.SOY_FILE_NAME;
import static com.google.template.soy.parseinfo.passes.GenerateParseInfoVisitor.JavaClassNameSource.SOY_NAMESPACE_LAST_PART;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.Descriptors.GenericDescriptor;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.shared.SharedTestUtils;
import com.google.template.soy.shared.internal.gencode.GeneratedFile;
import com.google.template.soy.soytree.NamespaceDeclaration;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.testing.Extendable;
import com.google.template.soy.testing.Extension;
import com.google.template.soy.testing.Foo;
import com.google.template.soy.types.SoyTypeRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for GenerateParseInfoVisitor.
 *
 * <p>Note: Testing of the actual code generation happens in {@code SoyFileSetTest}.
 *
 */
@RunWith(JUnit4.class)
public final class GenerateParseInfoVisitorTest {

  @Test
  public void testJavaClassNameSource() {
    SoyFileNode soyFileNode = forFilePathAndNamespace("BooFoo.soy", "aaa.bbb.cccDdd");
    assertThat(SOY_FILE_NAME.generateBaseClassName(soyFileNode)).isEqualTo("BooFoo");

    soyFileNode = forFilePathAndNamespace("blah/bleh/boo_foo.soy", "aaa.bbb.cccDdd");
    assertThat(SOY_FILE_NAME.generateBaseClassName(soyFileNode)).isEqualTo("BooFoo");

    soyFileNode = forFilePathAndNamespace("boo-FOO.soy", "aaa.bbb.cccDdd");
    assertThat(SOY_FILE_NAME.generateBaseClassName(soyFileNode)).isEqualTo("BooFoo");

    soyFileNode = forFilePathAndNamespace("\\BLAH\\BOO_FOO.SOY", "aaa.bbb.cccDdd");
    assertThat(SOY_FILE_NAME.generateBaseClassName(soyFileNode)).isEqualTo("BooFoo");

    soyFileNode = forFilePathAndNamespace("", "cccDdd");
    assertThat(SOY_NAMESPACE_LAST_PART.generateBaseClassName(soyFileNode)).isEqualTo("CccDdd");

    soyFileNode = forFilePathAndNamespace("", "aaa.bbb.cccDdd");
    assertThat(SOY_NAMESPACE_LAST_PART.generateBaseClassName(soyFileNode)).isEqualTo("CccDdd");

    soyFileNode = forFilePathAndNamespace("", "aaa_bbb.ccc_ddd");
    assertThat(SOY_NAMESPACE_LAST_PART.generateBaseClassName(soyFileNode)).isEqualTo("CccDdd");

    soyFileNode = forFilePathAndNamespace("", "CccDdd");
    assertThat(SOY_NAMESPACE_LAST_PART.generateBaseClassName(soyFileNode)).isEqualTo("CccDdd");

    soyFileNode = forFilePathAndNamespace("", "aaa.bbb.ccc_DDD");
    assertThat(SOY_NAMESPACE_LAST_PART.generateBaseClassName(soyFileNode)).isEqualTo("CccDdd");

    soyFileNode = forFilePathAndNamespace("BooFoo.soy", "aaa.bbb.cccDdd");
    assertThat(GENERIC.generateBaseClassName(soyFileNode)).isEqualTo("File");

    soyFileNode = forFilePathAndNamespace("blah/bleh/boo-foo.soy", "ccc_ddd");
    assertThat(GENERIC.generateBaseClassName(soyFileNode)).isEqualTo("File");
  }

  @Test
  public void testFindsProtoFromMap() {
    String parseInfoContent =
        createParseInfo(
            ImmutableList.of(Foo.getDescriptor()),
            "{@param map: map<string, soy.test.Foo>}",
            "{$map}");

    assertThat(parseInfoContent).contains("com.google.template.soy.testing.Test.getDescriptor()");
  }

  @Test
  public void testFindsProtoFromLegacyObjectMap() {
    String parseInfoContent =
        createParseInfo(
            ImmutableList.of(Foo.getDescriptor()),
            "{@param map: legacy_object_map<string, soy.test.Foo>}",
            "{$map}");

    assertThat(parseInfoContent).contains("com.google.template.soy.testing.Test.getDescriptor()");
  }

  @Test
  public void testFindsProtoEnum() {
    String parseInfoContent =
        createParseInfo(
            ImmutableList.of(Foo.getDescriptor()),
            "{@param enum: soy.test.Foo.InnerEnum}",
            "{$enum}");

    assertThat(parseInfoContent).contains("com.google.template.soy.testing.Test.getDescriptor()");
  }

  @Test
  public void testFindsProtoInit() {
    String parseInfoContent =
        createParseInfo(
            ImmutableList.of(Foo.InnerMessage.getDescriptor()),
            "{@param proto: bool}",
            "{$proto ? soy.test.Foo.InnerMessage(field: 27) : null}");

    assertThat(parseInfoContent).contains("com.google.template.soy.testing.Test.getDescriptor()");
  }

  @Test
  public void testFindsProtoExtension() {
    String parseInfoContent =
        createParseInfo(
            ImmutableList.of(Extendable.getDescriptor(), Extension.getDescriptor()),
            "{@param extendable: soy.test.Extendable}",
            "{$extendable.extension.enumField}");

    assertThat(parseInfoContent).contains("com.google.template.soy.testing.Test.getDescriptor()");
  }

  @Test
  public void testFindsProtoEnumUse() {
    String parseInfoContent =
        createParseInfo(
            ImmutableList.of(Foo.InnerEnum.getDescriptor()), "{soy.test.Foo.InnerEnum.THREE}");

    assertThat(parseInfoContent).contains("com.google.template.soy.testing.Test.getDescriptor()");
  }

  @Test
  public void testFindsVe() {
    String parseInfoContent =
        createParseInfo(
            ImmutableList.of(Foo.getDescriptor()), "{@param ve: ve<soy.test.Foo>}", "{$ve}");

    assertThat(parseInfoContent).contains("com.google.template.soy.testing.Test.getDescriptor()");
  }

  @Test
  public void testDeprecated() {
    String parseInfoContent =
        createParseInfo(ImmutableList.of(), "{@param simple: string}", "{$simple}");

    assertThat(parseInfoContent).contains("@Deprecated");
    assertThat(parseInfoContent)
        .contains("@deprecated Use {@link com.google.gpivtest.NoPathTemplates} instead.");
    assertThat(parseInfoContent)
        .contains(
            "@deprecated Use {@link com.google.gpivtest.NoPathTemplates.BrittleTestTemplate}"
                + " instead.");
  }

  @Test
  public void testNotDeprecated() {
    // Will need to update this test as params builders support more types in
    // InvocationBuilderTypeUtils.
    String parseInfoContent =
        createParseInfo(
            ImmutableList.of(Foo.getDescriptor()),
            "{@param record: [f1:string,f2:[f3:string]]}",
            "{$record.f1}");

    assertThat(parseInfoContent).doesNotContain("@Deprecated");
    assertThat(parseInfoContent).doesNotContain("@deprecated");
  }

  private static SoyFileNode forFilePathAndNamespace(String filePath, String namespace) {
    return new SoyFileNode(
        0,
        filePath,
        new NamespaceDeclaration(
            Identifier.create(namespace, SourceLocation.UNKNOWN),
            ImmutableList.of(),
            ErrorReporter.exploding()),
        new TemplateNode.SoyFileHeaderInfo(namespace));
  }

  private static String createParseInfo(
      ImmutableList<GenericDescriptor> protos, String... templateLines) {
    SoyTypeRegistry typeRegistry = new SoyTypeRegistry.Builder().addDescriptors(protos).build();
    ParseResult parseResult =
        SoyFileSetParserBuilder.forFileContents(
                SharedTestUtils.buildTestSoyFileContent(
                    /* strictHtml= */ true, Joiner.on('\n').join(templateLines)))
            .typeRegistry(typeRegistry)
            .parse();
    TemplateRegistry registry = parseResult.registry();

    ImmutableList<GeneratedFile> parseInfos =
        new GenerateParseInfoVisitor("com.google.gpivtest", "filename", registry, typeRegistry)
            .exec(parseResult.fileSet());

    // Verify that exactly one generated file has the name "NoPathSoyInfo.java", and return it.
    return parseInfos.stream()
        .filter(file -> file.fileName().equals("NoPathSoyInfo.java"))
        .reduce(
            (a, b) -> {
              throw new IllegalArgumentException("Two files with the name: NoPathSoyInfo.java");
            })
        .get()
        .contents();
  }
}
