/*
 * Copyright 2026 Google Inc.
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

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.soytree.ImportNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.SymbolVar;
import com.google.template.soy.soytree.defn.SymbolVar.SymbolKind;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import com.google.template.soy.testing.publicimport.PublicImportShim;
import com.google.template.soy.types.SoyProtoEnumType;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.SoyTypeRegistryBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests that proto imports follow {@code import public} declarations. */
@RunWith(JUnit4.class)
public final class ProtoImportProcessorTest {

  /**
   * {@code public_import_shim.proto} declares no symbols of its own. It publicly imports {@code
   * public_import_middle.proto}, which in turn publicly imports {@code public_import_target.proto}.
   * Each file uses a different proto package.
   */
  private static final String SHIM_PATH = PublicImportShim.getDescriptor().getName();

  private final ErrorReporter errorReporter = ErrorReporter.create();

  @Test
  public void testSymbolsReachedThroughPublicImportsResolve() {
    SoyFileNode file =
        parse(
            "import {MiddleMessage, PubliclyImportedEnum, PubliclyImportedMessage,"
                + " publiclyImportedExtension} from '"
                + SHIM_PATH
                + "';",
            "{template t}",
            "  {@param message: PubliclyImportedMessage}",
            "  {@param enumValue: PubliclyImportedEnum}",
            "  {@param middle: MiddleMessage}",
            "  {$message.getExtension(publiclyImportedExtension)}",
            "  {$enumValue}",
            "  {$middle.getSomeString()}",
            "{/template}");

    assertThat(errorReporter.getErrors()).isEmpty();
    assertThat(importedSymbolKinds(file))
        .containsExactly(
            "PubliclyImportedMessage", SymbolKind.PROTO_MESSAGE,
            "PubliclyImportedEnum", SymbolKind.PROTO_ENUM,
            "MiddleMessage", SymbolKind.PROTO_MESSAGE,
            "publiclyImportedExtension", SymbolKind.PROTO_EXT);
  }

  @Test
  public void testSymbolsReachedThroughPublicImportsKeepTheirDefiningPackage() {
    SoyFileNode file =
        parse(
            "import {MiddleMessage, PubliclyImportedEnum, PubliclyImportedMessage} from '"
                + SHIM_PATH
                + "';",
            "{template t}",
            "  {@param message: PubliclyImportedMessage}",
            "  {@param enumValue: PubliclyImportedEnum}",
            "  {@param middle: MiddleMessage}",
            "  {$message.getSomeString()}{$enumValue}{$middle.getSomeString()}",
            "{/template}");

    assertThat(errorReporter.getErrors()).isEmpty();
    // The shim file's package is example.publicimport.shim, but the symbols belong to the packages
    // of the files that actually declare them.
    assertThat(paramTypeNames(file))
        .containsExactly(
            "message", "example.publicimport.target.PubliclyImportedMessage",
            "enumValue", "example.publicimport.target.PubliclyImportedEnum",
            "middle", "example.publicimport.middle.MiddleMessage");
  }

  @Test
  public void testNestedSymbolsReachedThroughPublicImportsResolve() {
    SoyFileNode file =
        parse(
            "import {PubliclyImportedExtensionHolder, PubliclyImportedMessage} from '"
                + SHIM_PATH
                + "';",
            "{template t}",
            "  {@param message: PubliclyImportedMessage}",
            "  {@param nested: PubliclyImportedMessage.NestedMessage}",
            "  {@param nestedEnum: PubliclyImportedMessage.NestedEnum}",
            "  {$message.getExtension(PubliclyImportedExtensionHolder.nestedExtension)}",
            "  {$nested.getNestedString()}{$nestedEnum}",
            "{/template}");

    assertThat(errorReporter.getErrors()).isEmpty();
    assertThat(paramTypeNames(file))
        .containsExactly(
            "message", "example.publicimport.target.PubliclyImportedMessage",
            "nested", "example.publicimport.target.PubliclyImportedMessage.NestedMessage",
            "nestedEnum", "example.publicimport.target.PubliclyImportedMessage.NestedEnum");
  }

  @Test
  public void testNonPublicImportsAreNotReExported() {
    SoyFileNode unused =
        parse(
            "import {NotReExportedMessage} from '" + SHIM_PATH + "';",
            "{template t}",
            "  {@param message: NotReExportedMessage}",
            "  {$message.getSomeString()}",
            "{/template}");

    assertThat(errorReporter.getErrors().get(0).message())
        .contains("Unknown symbol NotReExportedMessage in " + SHIM_PATH);
  }

  private static ImmutableMap<String, SymbolKind> importedSymbolKinds(SoyFileNode file) {
    return file.getImports().stream()
        .map(ImportNode::getIdentifiers)
        .flatMap(ImmutableList::stream)
        .collect(toImmutableMap(SymbolVar::name, SymbolVar::getSymbolKind));
  }

  private static ImmutableMap<String, String> paramTypeNames(SoyFileNode file) {
    TemplateNode template = file.getTemplates().get(0);
    return template.getParams().stream()
        .collect(toImmutableMap(TemplateParam::name, p -> protoFullName(p.type())));
  }

  private static String protoFullName(SoyType type) {
    if (type instanceof SoyProtoType protoType) {
      return protoType.getDescriptor().getFullName();
    } else if (type instanceof SoyProtoEnumType enumType) {
      return enumType.getDescriptor().getFullName();
    }
    return String.valueOf(type);
  }

  private SoyFileNode parse(String... lines) {
    SoyTypeRegistry typeRegistry =
        new SoyTypeRegistryBuilder()
            .addDescriptors(SoyFileKind.DEP, ImmutableList.of(PublicImportShim.getDescriptor()))
            .build();
    return SoyFileSetParserBuilder.forFileContents("{namespace ns}\n" + Joiner.on('\n').join(lines))
        .typeRegistry(typeRegistry)
        .errorReporter(errorReporter)
        .parse()
        .fileSet()
        .getChild(0);
  }
}
