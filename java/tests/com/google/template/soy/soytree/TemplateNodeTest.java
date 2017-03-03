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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.error.FormattingErrorReporter;
import com.google.template.soy.exprtree.BooleanNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.soytree.defn.SoyDocParam;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.soytree.defn.TemplateParam.DeclLoc;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for TemplateNode.
 *
 */
@RunWith(JUnit4.class)
public class TemplateNodeTest {

  @Test
  public void testParseSoyDoc() {
    String soyDoc =
        ""
            + "/**\n"
            + " * Test template.\n"
            + " *\n"
            + " * @param foo Foo to print.\n"
            + " * @param? goo\n"
            + " *     Goo to print.\n"
            + " */";
    TemplateNode tn =
        parse(
            "{namespace ns}\n"
                + "/**\n"
                + " * Test template.\n"
                + " *\n"
                + " * @param foo Foo to print.\n"
                + " * @param? goo\n"
                + " *     Goo to print.\n"
                + " */"
                + "{template .boo}{$foo}{$goo}{/template}");

    assertEquals(soyDoc, tn.getSoyDoc());
    assertEquals("Test template.", tn.getSoyDocDesc());
    List<TemplateParam> params = tn.getParams();
    assertEquals(2, params.size());
    SoyDocParam soyDocParam0 = (SoyDocParam) params.get(0);
    assertEquals(DeclLoc.SOY_DOC, soyDocParam0.declLoc());
    assertEquals("foo", soyDocParam0.name());
    assertEquals(true, soyDocParam0.isRequired());
    assertEquals("Foo to print.", soyDocParam0.desc());
    SoyDocParam soyDocParam1 = (SoyDocParam) params.get(1);
    assertEquals(DeclLoc.SOY_DOC, soyDocParam1.declLoc());
    assertEquals("goo", soyDocParam1.name());
    assertEquals(false, soyDocParam1.isRequired());
    assertEquals("Goo to print.", soyDocParam1.desc());
  }

  @Test
  public void testEscapeSoyDoc() {
    TemplateNode tn =
        parse("{namespace ns}\n" + "/**@deprecated */\n" + "{template .boo}{/template}");

    assertEquals("&#64;deprecated", tn.getSoyDocDesc());
  }

  @Test
  public void testParseHeaderDecls() {
    TemplateNode tn =
        parse("{namespace ns}\n" + "/**@param foo */\n" + "{template .boo}{$foo}{/template}");
    List<TemplateParam> params = tn.getParams();
    assertThat(params).hasSize(1);

    SoyDocParam soyDocParam0 = (SoyDocParam) params.get(0);
    assertEquals("foo", soyDocParam0.name());

    assertThat(ImmutableList.copyOf(tn.getAllParams())).hasSize(1);
  }

  @Test
  public void testInvalidParamNames() {
    FormattingErrorReporter errorReporter = new FormattingErrorReporter();
    parse("{namespace ns}\n" + "/**@param ij */\n" + "{template .boo}{/template}", errorReporter);
    assertThat(errorReporter.getErrorMessages())
        .containsExactly("Invalid param name 'ij' ('ij' is for injected data).");

    errorReporter = new FormattingErrorReporter();
    parse(
        "{namespace ns}\n" + "{template .boo}\n" + "{@param ij : int}\n" + "{/template}",
        errorReporter);
    assertThat(errorReporter.getErrorMessages())
        .containsExactly("Invalid param name 'ij' ('ij' is for injected data).");
  }

  @Test
  public void testParamsAlreadyDeclared() {
    FormattingErrorReporter errorReporter = new FormattingErrorReporter();
    parse(
        "{namespace ns}\n"
            + "/**@param foo @param goo @param? foo */\n"
            + "{template .boo}{/template}",
        errorReporter);
    assertThat(errorReporter.getErrorMessages()).containsExactly("Param 'foo' already declared");

    errorReporter = new FormattingErrorReporter();
    parse(
        "{namespace ns}\n"
            + "{template .boo}\n"
            + "{@param goo : null}{@param foo:string}{@param foo : int}\n"
            + "{/template}",
        errorReporter);
    assertThat(errorReporter.getErrorMessages()).containsExactly("Param 'foo' already declared");

    errorReporter = new FormattingErrorReporter();
    parse(
        "{namespace ns}\n"
            + "/** @param foo a soydoc param */\n"
            + "{template .boo}\n"
            + "{@param foo : string}\n"
            + "{/template}",
        errorReporter);
    assertThat(errorReporter.getErrorMessages()).containsExactly("Param 'foo' already declared");
  }

  @Test
  public void testCommandTextErrors() {
    FormattingErrorReporter errorReporter = new FormattingErrorReporter();
    parse("{namespace ns}\n{template autoescape=\"strict\"}{/template}", errorReporter);
    assertThat(errorReporter.getErrorMessages())
        .containsExactly(
            "Soy V2 template names must be relative to the file namespace, i.e. a dot followed by "
                + "an identifier.  Templates with fully qualified names are only allowed in legacy "
                + "templates marked with the deprecatedV1=\"true\" attribute.",
            "parse error at '=': expected }, identifier, or .");

    errorReporter = new FormattingErrorReporter();
    parse("{namespace ns}\n{template .foo autoescape=\"}{/template}", errorReporter);
    assertThat(errorReporter.getErrorMessages())
        .containsExactly(
            "Unexpected end of file.  Did you forget to close an attribute value or a comment?");
    errorReporter = new FormattingErrorReporter();
    parse("{namespace ns}\n{template .foo autoescape=\"false\"}{/template}", errorReporter);
    assertThat(errorReporter.getErrorMessages())
        .containsExactly(
            "Invalid attribute value, expected one of [deprecated-contextual, "
                + "deprecated-noncontextual, strict].");

    // assertion inside no-arg templateBasicNode() is that there is no exception.
    parse("{namespace ns}\n{template .foo autoescape=\n\t\r \"strict\"}{/template}");
  }

  @Test
  public void testValidStrictTemplates() {
    TemplateNode node;

    node =
        parse(
            "{namespace ns autoescape=\"deprecated-noncontextual\"}\n"
                + "{template .boo kind=\"text\" autoescape=\"strict\"}{/template}");
    assertEquals(AutoescapeMode.STRICT, node.getAutoescapeMode());
    assertEquals(ContentKind.TEXT, node.getContentKind());

    node =
        parse(
            "{namespace ns autoescape=\"deprecated-noncontextual\"}\n"
                + "{template .boo kind=\"html\" autoescape=\"strict\"}{/template}");
    assertEquals(AutoescapeMode.STRICT, node.getAutoescapeMode());
    assertEquals(ContentKind.HTML, node.getContentKind());

    // "kind" is optional, defaults to HTML
    node =
        parse(
            "{namespace ns autoescape=\"deprecated-noncontextual\"}\n"
                + "{template .boo autoescape=\"strict\"}{/template}");
    assertEquals(AutoescapeMode.STRICT, node.getAutoescapeMode());
    assertEquals(ContentKind.HTML, node.getContentKind());
  }

  @Test
  public void testInvalidStrictTemplates() {
    FormattingErrorReporter errorReporter = new FormattingErrorReporter();
    parse(
        "{namespace ns autoescape=\"deprecated-noncontextual\"}\n"
            + "{template .boo kind=\"text\"}{/template}",
        errorReporter);
    assertThat(errorReporter.getErrorMessages())
        .containsExactly("kind=\"...\" attribute is only valid with autoescape=\"strict\".");
  }

  @Test
  public void testValidRequiredCss() {
    TemplateNode node;

    node = parse("{namespace ns}\n{template .boo requirecss=\"foo.boo\"}{/template}");
    assertEquals(ImmutableList.<String>of("foo.boo"), node.getRequiredCssNamespaces());

    node = parse("{namespace ns}\n{template .boo requirecss=\"foo, bar\"}{/template}");
    assertEquals(ImmutableList.<String>of("foo", "bar"), node.getRequiredCssNamespaces());

    node = parse("{namespace ns}\n{template .boo requirecss=\"foo.boo, foo.moo\"}{/template}");
    assertEquals(ImmutableList.<String>of("foo.boo", "foo.moo"), node.getRequiredCssNamespaces());

    // Now for deltemplates.
    node =
        parse(
            "{namespace ns}\n"
                + "{deltemplate namespace.boo requirecss=\"foo.boo, moo.hoo\"}{/deltemplate}");
    assertEquals(ImmutableList.<String>of("foo.boo", "moo.hoo"), node.getRequiredCssNamespaces());
  }

  @Test
  public void testValidVariant() {
    // Variant is a string literal: There's no expression and the value is already resolved.

    TemplateDelegateNode node =
        (TemplateDelegateNode)
            parse(
                join(
                    "{namespace ns}",
                    "{deltemplate namespace.boo variant=\"'abc'\"}",
                    "{/deltemplate}"));
    assertEquals("namespace.boo", node.getDelTemplateName());
    assertEquals("abc", node.getDelTemplateVariant());
    assertEquals("abc", node.getDelTemplateKey().variant());

    // Variant is a global, that was not yet resolved.
    node =
        (TemplateDelegateNode)
            parse(
                join(
                    "{namespace ns}",
                    "{deltemplate namespace.boo variant=\"test.GLOBAL_CONSTANT\"}",
                    "{/deltemplate}"));
    assertEquals("namespace.boo", node.getDelTemplateName());
    assertEquals("test.GLOBAL_CONSTANT", node.getDelTemplateVariant());
    assertEquals("test.GLOBAL_CONSTANT", node.getDelTemplateKey().variant());
    // Verify the global expression.
    List<ExprUnion> exprUnions = node.getAllExprUnions();
    assertEquals(1, exprUnions.size());
    ExprUnion exprUnion = exprUnions.get(0);
    assertEquals("test.GLOBAL_CONSTANT", exprUnion.getExprText());
    assertEquals(1, exprUnion.getExpr().numChildren());
    assertTrue(exprUnion.getExpr().getRoot() instanceof GlobalNode);
    // Substitute the global expression.
    exprUnion
        .getExpr()
        .replaceChild(0, new IntegerNode(123, exprUnion.getExpr().getRoot().getSourceLocation()));
    // Check the new values.
    assertEquals("123", node.getDelTemplateVariant());
    assertEquals("123", node.getDelTemplateKey().variant());

    // Resolve a global to a string.
    node =
        (TemplateDelegateNode)
            parse(
                join(
                    "{namespace ns}",
                    "{deltemplate namespace.boo variant=\"test.GLOBAL_CONSTANT\"}",
                    "{/deltemplate}"));
    node.getAllExprUnions()
        .get(0)
        .getExpr()
        .replaceChild(0, new StringNode("variant", node.getSourceLocation()));
    assertEquals("variant", node.getDelTemplateVariant());
    assertEquals("variant", node.getDelTemplateKey().variant());
  }

  @Test
  public void testInvalidVariant() {
    // Try to resolve a global to an invalid type.
    TemplateDelegateNode node =
        (TemplateDelegateNode)
            parse(
                join(
                    "{namespace ns}",
                    "{deltemplate namespace.boo variant=\"test.GLOBAL_CONSTANT\"}",
                    "{/deltemplate}"));
    node.getAllExprUnions()
        .get(0)
        .getExpr()
        .replaceChild(0, new BooleanNode(true, node.getSourceLocation()));
    try {
      node.getDelTemplateVariant();
      fail("An error is expected when an invalid node type is used.");
    } catch (AssertionError e) {
      assertTrue(e.getMessage().contains("Invalid expression for deltemplate"));
    }

    // Try to resolve a global to an invalid string
    node =
        (TemplateDelegateNode)
            parse(
                join(
                    "{namespace ns}",
                    "{deltemplate namespace.boo variant=\"test.GLOBAL_CONSTANT\"}",
                    "{/deltemplate}"));
    node.getAllExprUnions()
        .get(0)
        .getExpr()
        .replaceChild(0, new StringNode("Not and Identifier!", node.getSourceLocation()));
    try {
      node.getDelTemplateVariant();
      fail("An error is expected when a global string value is not an identifier.");
    } catch (SoySyntaxException e) {
      assertTrue(e.getMessage().contains("a string literal is used, value must be an identifier"));
    }
  }

  @Test
  public void testInvalidRequiredCss() {
    FormattingErrorReporter errorReporter = new FormattingErrorReporter();
    parse("{namespace ns}\n{template .boo requirecss=\"\"}{/template}", errorReporter);
    assertThat(errorReporter.getErrorMessages())
        .containsExactly("Invalid required CSS namespace name '', expected an identifier.");

    errorReporter = new FormattingErrorReporter();
    parse("{namespace ns}\n{template .boo requirecss=\"foo boo\"}{/template}", errorReporter);
    assertThat(errorReporter.getErrorMessages())
        .containsExactly("Invalid required CSS namespace name 'foo boo', expected an identifier.");

    errorReporter = new FormattingErrorReporter();
    parse("{namespace ns}\n{template .boo requirecss=\"9vol\"}{/template}", errorReporter);
    assertThat(errorReporter.getErrorMessages())
        .containsExactly("Invalid required CSS namespace name '9vol', expected an identifier.");

    errorReporter = new FormattingErrorReporter();
    parse("{namespace ns}\n{deltemplate foo.boo requirecss=\"5ham\"}{/deltemplate}", errorReporter);
    assertThat(errorReporter.getErrorMessages())
        .containsExactly("Invalid required CSS namespace name '5ham', expected an identifier.");
  }

  @Test
  public void testToSourceString() {
    TemplateNode tn =
        parse(
            join(
                "{namespace ns}",
                "/**",
                " * Test template.",
                " *",
                " * @param foo Foo to print.",
                " * @param goo",
                " *     Goo to print.",
                " */",
                "{template .boo}",
                "  /** Something milky. */",
                "  {@param moo : bool}",
                "  {@param? too : string}",
                "{sp}{sp}{$foo}{$goo}{$moo ? 'moo' : ''}{$too}\n",
                "{/template}"));
    assertEquals(
        ""
            + "/**\n"
            + " * Test template.\n"
            + " *\n"
            + " * @param foo Foo to print.\n"
            + " * @param goo\n"
            + " *     Goo to print.\n"
            + " */\n"
            + "{template .boo}\n"
            + "  {@param moo: bool}  /** Something milky. */\n"
            + "  {@param? too: null|string}\n"
            + "{sp} {$foo}{$goo}{$moo ? 'moo' : ''}{$too}\n"
            + "{/template}\n",
        tn.toSourceString());
  }

  private static String join(String... lines) {
    return Joiner.on("\n").join(lines);
  }

  private static TemplateNode parse(String file) {
    return parse(file, ExplodingErrorReporter.get());
  }

  private static TemplateNode parse(String file, ErrorReporter errorReporter) {
    SoyFileSetNode node =
        SoyFileSetParserBuilder.forFileContents(file)
            .errorReporter(errorReporter)
            .allowUnboundGlobals(true) // for the delvariant tests
            .parse()
            .fileSet();
    // if parsing fails, templates/files will be missing.  just return null in that case.
    if (node.numChildren() > 0) {
      SoyFileNode filenode = node.getChild(0);
      if (filenode.numChildren() > 0) {
        return filenode.getChild(0);
      }
    }
    return null;
  }
}
