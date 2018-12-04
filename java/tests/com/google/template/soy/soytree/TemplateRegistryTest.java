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
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.error.ErrorReporter;
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
                    SoyFileKind.SRC,
                    "example.soy"))
            .parse()
            .registry();
    assertThatRegistry(registry)
        .containsBasicTemplate("ns.foo")
        .definedAt(new SourceLocation("example.soy", 3, 1, 3, 15));
    assertThatRegistry(registry).doesNotContainBasicTemplate("foo");
    assertThatRegistry(registry)
        .containsDelTemplate("bar.baz")
        .definedAt(new SourceLocation("example.soy", 6, 1, 6, 21));
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
                    SoyFileKind.SRC,
                    "bar.soy"),
                SoyFileSupplier.Factory.create(
                    "{namespace ns2}\n"
                        + "/** Template. */\n"
                        + "{template .foo}\n"
                        + "{/template}\n",
                    SoyFileKind.SRC,
                    "baz.soy"))
            .parse()
            .registry();

    assertThatRegistry(registry)
        .containsBasicTemplate("ns.foo")
        .definedAt(new SourceLocation("bar.soy", 3, 1, 3, 15));
    assertThatRegistry(registry)
        .containsBasicTemplate("ns2.foo")
        .definedAt(new SourceLocation("baz.soy", 3, 1, 3, 15));
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
                    SoyFileKind.SRC,
                    "foo.soy"),
                SoyFileSupplier.Factory.create(
                    "{delpackage foo}\n"
                        + "{namespace ns}\n"
                        + "/** Deltemplate. */\n"
                        + "{deltemplate foo.bar}\n"
                        + "{/deltemplate}",
                    SoyFileKind.SRC,
                    "bar.soy"))
            .parse()
            .registry();

    assertThatRegistry(registry)
        .containsDelTemplate("foo.bar")
        .definedAt(new SourceLocation("foo.soy", 3, 1, 3, 21));
    assertThatRegistry(registry)
        .containsDelTemplate("foo.bar")
        .definedAt(new SourceLocation("bar.soy", 4, 1, 4, 21));
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
        .isEqualTo("Template/element 'ns.foo' already defined at no-path:3:1.");
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
        .isEqualTo("Delegate template 'foo.bar' already has a default defined at no-path:3:1.");
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
        .isEqualTo("Delegate template 'foo.bar' already defined in delpackage foo: no-path:4:1");
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
                    SoyFileKind.SRC,
                    "example.soy"))
            .parse()
            .registry();

    CallBasicNode node =
        new CallBasicNode(
            0,
            SourceLocation.UNKNOWN,
            Identifier.create("ns.foo", SourceLocation.UNKNOWN),
            "ns.foo",
            NO_ATTRS,
            FAIL);
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
                    SoyFileKind.SRC,
                    "example.soy"))
            .parse()
            .registry();
    CallBasicNode node =
        new CallBasicNode(
            0,
            SourceLocation.UNKNOWN,
            Identifier.create("ns.moo", SourceLocation.UNKNOWN),
            "ns.moo",
            NO_ATTRS,
            FAIL);
    assertThat(registry.getCallContentKind(node)).isAbsent();
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
                    SoyFileKind.SRC,
                    "example.soy"))
            .parse()
            .registry();
    CallDelegateNode node =
        new CallDelegateNode(
            0,
            SourceLocation.UNKNOWN,
            Identifier.create("ns.foo", SourceLocation.UNKNOWN),
            NO_ATTRS,
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
                    SoyFileKind.SRC,
                    "example.soy"))
            .parse()
            .registry();
    CallDelegateNode node =
        new CallDelegateNode(
            0,
            SourceLocation.UNKNOWN,
            Identifier.create("ns.moo", SourceLocation.UNKNOWN),
            NO_ATTRS,
            FAIL);
    assertThat(registry.getCallContentKind(node)).isAbsent();
  }

  @Test
  public void testSimpleTransiiveCallees() {

    // aaa -> {bbb, ccc}, bbb -> ddd.
    String fileContent =
        ""
            + "{namespace ns}\n"
            + "\n"
            + "/***/\n"
            + "{template .aaa}\n"
            + "  {call .bbb /} {$ij.boo} {call .ccc /} {$ij.foo}\n"
            + "{/template}\n"
            + "\n"
            + "/***/\n"
            + "{template .bbb}\n"
            + "  {$ij.boo} {$ij.goo} {call .ddd /}\n"
            + "{/template}\n"
            + "\n"
            + "/***/\n"
            + "{template .ccc}\n"
            + "  {$ij.boo} {$ij.moo + $ij.woo}\n"
            + "{/template}\n"
            + "\n"
            + "/***/\n"
            + "{template .ddd}\n"
            + "  {$ij.boo} {$ij.moo} {round($ij.zoo)}\n"
            + "{/template}\n";

    TemplateRegistry templateRegistry =
        SoyFileSetParserBuilder.forFileContents(fileContent).parse().registry();

    TemplateMetadata aaa = templateRegistry.getAllTemplates().get(0);
    TemplateMetadata bbb = templateRegistry.getAllTemplates().get(1);
    TemplateMetadata ccc = templateRegistry.getAllTemplates().get(2);
    TemplateMetadata ddd = templateRegistry.getAllTemplates().get(3);

    assertThat(templateRegistry.getCallGraph(ddd).transitiveCallees()).containsExactly(ddd);
    assertThat(templateRegistry.getCallGraph(ccc).transitiveCallees()).containsExactly(ccc);
    assertThat(templateRegistry.getCallGraph(bbb).transitiveCallees()).containsExactly(bbb, ddd);
    assertThat(templateRegistry.getCallGraph(aaa).transitiveCallees())
        .containsExactly(aaa, bbb, ccc, ddd);
  }

  @Test
  public void testTwoPathsToSameTemplate() {

    // aaa -> {bbb, ccc}, ccc -> bbb.
    String fileContent =
        ""
            + "{namespace ns}\n"
            + "\n"
            + "/***/\n"
            + "{template .aaa}\n"
            + "  {call .bbb /} {$ij.boo} {call .ccc /} {$ij.foo}\n"
            + "{/template}\n"
            + "\n"
            + "/***/\n"
            + "{template .bbb}\n"
            + "  {$ij.boo} {$ij.goo}\n"
            + "{/template}\n"
            + "\n"
            + "/***/\n"
            + "{template .ccc}\n"
            + "  {$ij.boo} {$ij.moo + $ij.woo} {call .bbb /}\n"
            + "{/template}\n";

    TemplateRegistry templateRegistry =
        SoyFileSetParserBuilder.forFileContents(fileContent).parse().registry();

    TemplateMetadata aaa = templateRegistry.getAllTemplates().get(0);
    TemplateMetadata bbb = templateRegistry.getAllTemplates().get(1);
    TemplateMetadata ccc = templateRegistry.getAllTemplates().get(2);

    assertThat(templateRegistry.getCallGraph(bbb).transitiveCallees()).containsExactly(bbb);
    assertThat(templateRegistry.getCallGraph(ccc).transitiveCallees()).containsExactly(ccc, bbb);
    assertThat(templateRegistry.getCallGraph(aaa).transitiveCallees())
        .containsExactly(aaa, bbb, ccc);
  }

  @Test
  public void testSimpleRecursion() {

    // Tests direct recursion (cycle of 1) and indirect recursion with a cycle of 2.

    // aaa -> bbb, bbb -> {bbb, ccc}, ccc -> bbb.
    String fileContent =
        ""
            + "{namespace ns}\n"
            + "\n"
            + "/***/\n"
            + "{template .aaa}\n"
            + "  {call .bbb /} {$ij.boo} {$ij.foo}\n"
            + "{/template}\n"
            + "\n"
            + "/***/\n"
            + "{template .bbb}\n"
            + "  {$ij.boo} {$ij.goo} {call .bbb /} {call .ccc /}\n"
            + "{/template}\n"
            + "\n"
            + "/***/\n"
            + "{template .ccc}\n"
            + "  {$ij.boo} {call .bbb /} {$ij.moo}\n"
            + "{/template}\n";

    TemplateRegistry templateRegistry =
        SoyFileSetParserBuilder.forFileContents(fileContent).parse().registry();

    TemplateMetadata aaa = templateRegistry.getAllTemplates().get(0);
    TemplateMetadata bbb = templateRegistry.getAllTemplates().get(1);
    TemplateMetadata ccc = templateRegistry.getAllTemplates().get(2);

    assertThat(templateRegistry.getCallGraph(ccc).transitiveCallees()).containsExactly(ccc, bbb);
    assertThat(templateRegistry.getCallGraph(bbb).transitiveCallees()).containsExactly(bbb, ccc);
    assertThat(templateRegistry.getCallGraph(aaa).transitiveCallees())
        .containsExactly(aaa, bbb, ccc);
  }

  @Test
  public void testGetTransitiveCallees() {
    // aaa -> {bbb, ccc}, bbb -> ddd.
    String fileContent =
        ""
            + "{namespace ns}\n"
            + "\n"
            + "/***/\n"
            + "{template .bbb}\n"
            + "  {$ij.boo} {$ij.goo} {call .ddd /}\n"
            + "{/template}\n"
            + "\n"
            + "/***/\n"
            + "{template .aaa}\n"
            + "  {call .bbb /} {$ij.boo} {call .ccc /} {$ij.foo}\n"
            + "{/template}\n"
            + "\n"
            + "/***/\n"
            + "{template .ccc}\n"
            + "  {$ij.boo} {$ij.moo + $ij.woo}\n"
            + "{/template}\n"
            + "\n"
            + "/***/\n"
            + "{template .ddd}\n"
            + "  {$ij.boo} {$ij.moo} {round($ij.zoo)}\n"
            + "{/template}\n";

    ParseResult result = SoyFileSetParserBuilder.forFileContents(fileContent).parse();
    TemplateRegistry templateRegistry = result.registry();

    TemplateMetadata bbb = templateRegistry.getAllTemplates().get(0);
    TemplateMetadata aaa = templateRegistry.getAllTemplates().get(1);
    TemplateMetadata ccc = templateRegistry.getAllTemplates().get(2);
    TemplateMetadata ddd = templateRegistry.getAllTemplates().get(3);

    assertThat(templateRegistry.getCallGraph(ddd).transitiveCallees()).containsExactly(ddd);
    assertThat(templateRegistry.getCallGraph(ccc).transitiveCallees()).containsExactly(ccc);
    assertThat(templateRegistry.getCallGraph(bbb).transitiveCallees()).containsExactly(bbb, ddd);
    assertThat(templateRegistry.getCallGraph(aaa).transitiveCallees())
        .containsExactly(aaa, bbb, ccc, ddd);
  }

  @Test
  public void testSmallerRecursiveCycleInLargerRecursiveCycle() {

    // aaa -> {bbb, ccc}, bbb -> aaa, ccc -> bbb.
    String fileContent =
        ""
            + "{namespace ns}\n"
            + "\n"
            + "/***/\n"
            + "{template .aaa}\n"
            + "  {$ij.foo} {$ij.boo} {call .bbb /} {call .ccc /}\n"
            + "{/template}\n"
            + "\n"
            + "/***/\n"
            + "{template .bbb}\n"
            + "  {$ij.goo} {$ij.boo} {call .aaa /}\n"
            + "{/template}\n"
            + "\n"
            + "/***/\n"
            + "{template .ccc}\n"
            + "  {$ij.moo} {$ij.boo} {call .bbb /}\n"
            + "{/template}\n";

    ParseResult result = SoyFileSetParserBuilder.forFileContents(fileContent).parse();
    TemplateRegistry templateRegistry = result.registry();

    TemplateMetadata bbb = templateRegistry.getAllTemplates().get(0);
    TemplateMetadata aaa = templateRegistry.getAllTemplates().get(1);
    TemplateMetadata ccc = templateRegistry.getAllTemplates().get(2);

    assertThat(templateRegistry.getCallGraph(ccc).transitiveCallees())
        .containsExactly(ccc, bbb, aaa);
    assertThat(templateRegistry.getCallGraph(bbb).transitiveCallees())
        .containsExactly(bbb, aaa, ccc);
    assertThat(templateRegistry.getCallGraph(aaa).transitiveCallees())
        .containsExactly(aaa, bbb, ccc);
  }
}
