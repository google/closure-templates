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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.base.internal.QuoteStyle;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.ExprRootNode;
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
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    parse("{namespace ns}\n" + "/**@param ij */\n" + "{template .boo}{/template}", errorReporter);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo("Invalid param name 'ij' ('ij' is for injected data).");

    errorReporter = ErrorReporter.createForTest();
    parse(
        "{namespace ns}\n" + "{template .boo}\n" + "{@param ij : int}\n" + "{/template}",
        errorReporter);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo("Invalid param name 'ij' ('ij' is for injected data).");
  }

  @Test
  public void testParamsAlreadyDeclared() {
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    parse(
        "{namespace ns}\n"
            + "/**@param foo @param goo @param? foo */\n"
            + "{template .boo}{/template}",
        errorReporter);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo("Param 'foo' already declared.");

    errorReporter = ErrorReporter.createForTest();
    parse(
        "{namespace ns}\n"
            + "{template .boo}\n"
            + "{@param goo : null}{@param foo:string}{@param foo : int}\n"
            + "{/template}",
        errorReporter);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo("Param 'foo' already declared.");

    errorReporter = ErrorReporter.createForTest();
    parse(
        "{namespace ns}\n"
            + "/**\n"
            + " * @param foo a soydoc param \n"
            + " * @param foo a soydoc param \n"
            + "*/\n"
            + "{template .boo}\n"
            + "{/template}",
        errorReporter);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo("Param 'foo' already declared.");
  }

  @Test
  public void testCommandTextErrors() {
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    parse("{namespace ns}\n{template requirecss=\"strict\"}{/template}", errorReporter);
    assertThat(errorReporter.getErrors()).hasSize(2);
    assertThat(errorReporter.getErrors().get(0).message())
        .isEqualTo(
            "Template name 'requirecss' must be relative to the file namespace, i.e. a dot "
                + "followed by an identifier.");

    assertThat(errorReporter.getErrors().get(1).message())
        .isEqualTo("parse error at '=': expected }, identifier, or .");

    errorReporter = ErrorReporter.createForTest();
    parse("{namespace ns}\n{template .foo autoescape=\"}{/template}", errorReporter);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo(
            "Unexpected end of file.  Did you forget to close an attribute value or a comment?");
    errorReporter = ErrorReporter.createForTest();
    parse("{namespace ns}\n{template .foo autoescape=\"false\"}{/template}", errorReporter);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo("Invalid value for attribute 'autoescape', expected deprecated-contextual.");

    // assertion inside no-arg templateBasicNode() is that there is no exception.
    parse("{namespace ns}\n{template .foo autoescape=\n\t\r \"deprecated-contextual\"}{/template}");
  }

  @Test
  public void testValidStrictTemplates() {
    // "kind" is optional, defaults to HTML
    TemplateNode node = parse("{namespace ns}\n" + "{template .boo}{/template}");
    assertEquals(AutoescapeMode.STRICT, node.getAutoescapeMode());
    assertEquals(SanitizedContentKind.HTML, node.getContentKind());
  }

  @Test
  public void testInvalidStrictTemplates() {
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    parse(
        "{namespace ns}\n"
            + "{template .boo autoescape=\"deprecated-contextual\" kind=\"text\"}{/template}",
        errorReporter);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo("kind=\"...\" attribute is only valid with autoescape=\"strict\".");
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
    List<ExprRootNode> exprs = node.getExprList();
    assertEquals(1, exprs.size());
    ExprRootNode expr = exprs.get(0);
    assertEquals("test.GLOBAL_CONSTANT", expr.toSourceString());
    assertEquals(1, expr.numChildren());
    assertTrue(expr.getRoot() instanceof GlobalNode);
    // Substitute the global expression.
    expr.replaceChild(0, new IntegerNode(123, expr.getRoot().getSourceLocation()));
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
    node.getExprList()
        .get(0)
        .replaceChild(0, new StringNode("variant", QuoteStyle.SINGLE, node.getSourceLocation()));
    assertEquals("variant", node.getDelTemplateVariant());
    assertEquals("variant", node.getDelTemplateKey().variant());
  }

  @Test
  public void testInvalidRequiredCss() {
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    parse("{namespace ns}\n{template .boo requirecss=\"\"}{/template}", errorReporter);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo("Invalid required CSS namespace name '', expected an identifier.");

    errorReporter = ErrorReporter.createForTest();
    parse("{namespace ns}\n{template .boo requirecss=\"foo boo\"}{/template}", errorReporter);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo("Invalid required CSS namespace name 'foo boo', expected an identifier.");

    errorReporter = ErrorReporter.createForTest();
    parse("{namespace ns}\n{template .boo requirecss=\"9vol\"}{/template}", errorReporter);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo("Invalid required CSS namespace name '9vol', expected an identifier.");

    errorReporter = ErrorReporter.createForTest();
    parse("{namespace ns}\n{deltemplate foo.boo requirecss=\"5ham\"}{/deltemplate}", errorReporter);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo("Invalid required CSS namespace name '5ham', expected an identifier.");
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
                "{template .boo}",
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
            "{template .boo}",
            "  {@param foo: ?}  /** Foo to print. */",
            "  {@param goo: ?}  /** Goo to print. */",
            "  {@param moo: bool}  /** Something milky. */",
            "  {@param? too: null|string}",
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
