/*
 * Copyright 2008 Google Inc.
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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.css.CssRegistry;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for TemplateNode.
 */
@RunWith(JUnit4.class)
public class TemplateNodeTest {

  @Test
  public void testParseSoyDoc() {
    String soyDoc = "/**\n * Test template.\n */";
    TemplateNode tn =
        parse(
            "{namespace ns}\n"
                + "/**\n"
                + " * Test template.\n"
                + " */"
                + "{template boo}{/template}");

    assertEquals(soyDoc, tn.getSoyDoc());
    assertEquals("Test template.", tn.getSoyDocDesc());
  }

  @Test
  public void testEscapeSoyDoc() {
    TemplateNode tn =
        parse("{namespace ns}\n" + "/**@deprecated */\n" + "{template boo}{/template}");

    assertEquals("&#64;deprecated", tn.getSoyDocDesc());
  }

  @Test
  public void testInvalidParamNames() {
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    parse(
        "{namespace ns}\n" + "{template boo}\n" + "{@param ij : int}\n" + "{/template}",
        errorReporter);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo("Invalid param name 'ij' ('ij' is for injected data).");
  }

  @Test
  public void testParamsAlreadyDeclared() {
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    parse(
        "{namespace ns}\n"
            + "{template boo}\n"
            + "{@param goo : null}{@param foo:string}{@param foo : int}\n"
            + "{/template}",
        errorReporter);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo("'foo' already declared.");
  }

  @Test
  public void testCommandTextErrors() {
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    parse("{namespace ns}\n{template requirecss=\"strict\"}{/template}", errorReporter);
    assertThat(errorReporter.getErrors()).hasSize(1);

    assertThat(errorReporter.getErrors().get(0).message())
        .isEqualTo("parse error at '=': expected }, identifier, or .");

    errorReporter = ErrorReporter.createForTest();
    parse("{namespace ns}\n{template foo autoescape=\"}{/template}", errorReporter);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo(
            "Unexpected end of file.  Did you forget to close an attribute value or a comment?");
    errorReporter = ErrorReporter.createForTest();
    parse(
        "{namespace ns}\n{template foo autoescape=\"deprecated-contextual\"}{/template}",
        errorReporter);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo(
            "Unsupported attribute 'autoescape' for 'template' tag, expected one of [visibility, "
                + "modifiable, modifies, legacydeltemplatenamespace, variant, usevarianttype, "
                + "kind, requirecss, cssbase, stricthtml, whitespace, component].");
  }

  @Test
  public void testValidStrictTemplates() {
    // "kind" is optional, defaults to HTML
    TemplateNode node = parse("{namespace ns}\n" + "{template boo}{/template}");
    assertEquals(SanitizedContentKind.HTML, node.getContentKind());
  }

  @Test
  public void testValidRequiredCss() {
    TemplateNode node;

    node = parse("{namespace ns}\n{template boo requirecss=\"foo.boo\"}{/template}");
    assertEquals(ImmutableList.of("foo.boo"), node.getRequiredCssNamespaces());

    node = parse("{namespace ns}\n{template boo requirecss=\"foo, bar\"}{/template}");
    assertEquals(ImmutableList.of("foo", "bar"), node.getRequiredCssNamespaces());

    node = parse("{namespace ns}\n{template boo requirecss=\"foo.boo, foo.moo\"}{/template}");
    assertEquals(ImmutableList.of("foo.boo", "foo.moo"), node.getRequiredCssNamespaces());
  }

  @Test
  public void testValidIsComponent() {
    TemplateNode node;
    node = parse("{namespace ns}\n{template boo component=\"true\"}{/template}");
    assertTrue(node.getComponent());

    node = parse("{namespace ns}\n{template boo}{/template}");
    assertFalse(node.getComponent());
  }

  @Test
  public void testIsComponentErrors() {
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    parse("{namespace ns}\n{template boo component=\"false\"}{/template}", errorReporter);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo("'component=\"false\"' is the default, no need to set it.");

    errorReporter = ErrorReporter.createForTest();
    parse("{namespace ns}\n{template boo component=\"test\"}{/template}", errorReporter);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo("Invalid value for attribute 'component', expected true.");
  }

  @Test
  public void testInvalidRequiredCss() {
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    parse("{namespace ns}\n{template boo requirecss=\"\"}{/template}", errorReporter);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo("Invalid required CSS namespace name '', expected an identifier.");

    errorReporter = ErrorReporter.createForTest();
    parse("{namespace ns}\n{template boo requirecss=\"foo boo\"}{/template}", errorReporter);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo("Invalid required CSS namespace name 'foo boo', expected an identifier.");

    errorReporter = ErrorReporter.createForTest();
    parse("{namespace ns}\n{template boo requirecss=\"9vol\"}{/template}", errorReporter);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo("Invalid required CSS namespace name '9vol', expected an identifier.");
  }

  @Test
  public void testToSourceString() {
    TemplateNode tn =
        parse(
            join(
                "{namespace ns}",
                "/**",
                " * Test template.",
                " */",
                "{template boo}",
                "  /** Foo to print. */",
                "  {@param foo : ?}",
                "  /** Goo to print. */",
                "  {@param goo : ?}",
                "  /** Something milky. */",
                "  {@param moo : bool}",
                "  {@param? too : string}",
                "{sp}{sp}{$foo}{$goo}{$moo ? 'moo' : ''}{$too}\n",
                "{/template}"));
    assertEquals(
        join(
            "/**",
            " * Test template.",
            " */",
            "{template boo}",
            "  {@param foo: ?}  /** Foo to print. */",
            "  {@param goo: ?}  /** Goo to print. */",
            "  {@param moo: bool}  /** Something milky. */",
            "  {@param? too: string}",
            "{sp} {$foo}{$goo}{$moo ? 'moo' : ''}{$too}",
            "{/template}\n"),
        tn.toSourceString());
  }

  private static String join(String... lines) {
    return Joiner.on("\n").join(lines);
  }

  private static TemplateNode parse(String file) {
    return parse(file, ErrorReporter.exploding());
  }

  private static TemplateNode parse(String file, ErrorReporter errorReporter) {
    SoyFileSetNode node =
        SoyFileSetParserBuilder.forFileContents(file)
            .errorReporter(errorReporter)
            .allowUnboundGlobals(true) // for the delvariant tests
            .cssRegistry(
                CssRegistry.create(
                    ImmutableSet.of("foo", "bar", "foo.boo", "foo.moo"), ImmutableMap.of()))
            .parse()
            .fileSet();
    // if parsing fails, templates/files will be missing.  just return null in that case.
    if (node.numChildren() > 0) {
      SoyFileNode filenode = node.getChild(0);
      if (filenode.numChildren() > 0) {
        return (TemplateNode) filenode.getChild(0);
      }
    }
    return null;
  }
}
