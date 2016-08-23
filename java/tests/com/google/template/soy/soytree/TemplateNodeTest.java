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
import static com.google.template.soy.types.SoyTypes.makeNullable;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.error.FormattingErrorReporter;
import com.google.template.soy.exprparse.SoyParsingContext;
import com.google.template.soy.exprtree.BooleanNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.soytree.TemplateNode.SoyFileHeaderInfo;
import com.google.template.soy.soytree.TemplateNodeBuilder.DeclInfo;
import com.google.template.soy.soytree.TemplateNodeBuilder.DeclInfo.OptionalStatus;
import com.google.template.soy.soytree.TemplateNodeBuilder.DeclInfo.Type;
import com.google.template.soy.soytree.defn.HeaderParam;
import com.google.template.soy.soytree.defn.SoyDocParam;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.soytree.defn.TemplateParam.DeclLoc;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.aggregate.ListType;
import com.google.template.soy.types.primitive.IntType;
import com.google.template.soy.types.primitive.StringType;

import junit.framework.TestCase;

import java.util.List;

/**
 * Unit tests for TemplateNode.
 *
 */
public class TemplateNodeTest extends TestCase {

  private static final SoyFileHeaderInfo SIMPLE_FILE_HEADER_INFO = new SoyFileHeaderInfo("testNs");
  private static final SoyTypeRegistry TYPE_REGISTRY = new SoyTypeRegistry();
  private static final ErrorReporter FAIL = ExplodingErrorReporter.get();

  public void testParseSoyDoc() {
    String soyDoc = "" +
        "/**\n" +
        " * Test template.\n" +
        " *\n" +
        " * @param foo Foo to print.\n" +
        " * @param? goo\n" +
        " *     Goo to print.\n" +
        " */";
    TemplateNode tn = new TemplateBasicNodeBuilder(
        SIMPLE_FILE_HEADER_INFO, SourceLocation.UNKNOWN, FAIL)
        .setId(0)
        .setCmdText(".boo")
        .setSoyDoc(soyDoc)
        .build();

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

  public void testEscapeSoyDoc() {
    String soyDoc =
        "/**\n" +
        " * @deprecated\n" +
        " */";
    TemplateNode tn = new TemplateBasicNodeBuilder(
        SIMPLE_FILE_HEADER_INFO, SourceLocation.UNKNOWN, FAIL)
        .setId(0)
        .setCmdText(".boo")
        .setSoyDoc(soyDoc)
        .build();

    assertEquals("&#64;deprecated", tn.getSoyDocDesc());
  }

  public void testParseHeaderDecls() {
    TemplateNode tn = templateBasicNode()
        .setId(0)
        .setCmdText(".boo")
        .setSoyDoc("/** @param foo */")
        .setHeaderDecls(
            new DeclInfo(
                Type.PARAM,
                OptionalStatus.REQUIRED,
                "goo", "list<int>",
                null /* soyDoc */,
                SourceLocation.UNKNOWN),
            new DeclInfo(
                Type.PARAM,
                OptionalStatus.REQUIRED,
                "moo", "string",
                "Something milky.",
                SourceLocation.UNKNOWN),
            new DeclInfo(
                Type.PARAM,
                OptionalStatus.OPTIONAL,
                "boo", "string",
                "Something scary.",
                SourceLocation.UNKNOWN),
            new DeclInfo(
                Type.INJECTED_PARAM,
                OptionalStatus.REQUIRED,
                "zoo", "string",
                "Something else.",
                SourceLocation.UNKNOWN))
        .build();

    List<TemplateParam> params = tn.getParams();
    assertEquals(4, params.size());

    SoyDocParam soyDocParam0 = (SoyDocParam) params.get(0);
    assertEquals("foo", soyDocParam0.name());

    HeaderParam headerParam1 = (HeaderParam) params.get(1);
    assertEquals("goo", headerParam1.name());
    assertEquals("list<int>", headerParam1.typeSrc());
    assertEquals(ListType.of(IntType.getInstance()), headerParam1.type());
    assertTrue(headerParam1.isRequired());
    assertFalse(headerParam1.isInjected());
    assertEquals(null, headerParam1.desc());

    HeaderParam headerParam2 = (HeaderParam) params.get(2);
    assertEquals("moo", headerParam2.name());
    assertEquals("string", headerParam2.typeSrc());
    assertEquals(StringType.getInstance(), headerParam2.type());
    assertTrue(headerParam2.isRequired());
    assertFalse(headerParam2.isInjected());
    assertEquals("Something milky.", headerParam2.desc());

    HeaderParam headerParam3 = (HeaderParam) params.get(3);
    assertEquals("boo", headerParam3.name());
    assertEquals("string", headerParam3.typeSrc());
    assertEquals(makeNullable(StringType.getInstance()), headerParam3.type());
    assertFalse(headerParam3.isRequired());
    assertFalse(headerParam3.isInjected());
    assertEquals("Something scary.", headerParam3.desc());

    params = tn.getInjectedParams();
    assertEquals(1, params.size());

    HeaderParam injectedParam = (HeaderParam) params.get(0);
    assertEquals("zoo", injectedParam.name());
    assertEquals("string", injectedParam.typeSrc());
    assertEquals(StringType.getInstance(), injectedParam.type());
    assertTrue(injectedParam.isRequired());
    assertTrue(injectedParam.isInjected());
    assertEquals("Something else.", injectedParam.desc());

    assertEquals(5, ImmutableList.copyOf(tn.getAllParams()).size());
  }

  public void testInvalidParamNames() {
    FormattingErrorReporter errorReporter = new FormattingErrorReporter();

    errorReporter = new FormattingErrorReporter();
    templateBasicNode(errorReporter)
        .setId(0).setCmdText(".boo")
        .setSoyDoc("/** @param ij */")
        .build();
    assertThat(errorReporter.getErrorMessages()).hasSize(1);
    assertThat(errorReporter.getErrorMessages().get(0)).contains(
        "Invalid param name 'ij' ('ij' is for injected data).");

    errorReporter = new FormattingErrorReporter();
    templateBasicNode(errorReporter)
        .setId(0)
        .setCmdText(".boo")
        .setHeaderDecls(
            new DeclInfo(
                Type.PARAM,
                OptionalStatus.REQUIRED,
                "ij", "int",
                null /* soyDoc */,
                SourceLocation.UNKNOWN))
        .build();
    assertThat(errorReporter.getErrorMessages()).hasSize(1);
    assertThat(errorReporter.getErrorMessages().get(0)).contains(
        "Invalid param name 'ij' ('ij' is for injected data).");
  }


  public void testParamsAlreadyDeclared() {
    FormattingErrorReporter errorReporter = new FormattingErrorReporter();
    templateBasicNode(errorReporter)
        .setId(0)
        .setCmdText(".boo")
        .setSoyDoc("/** @param foo @param goo @param? foo */")
        .build();
    assertThat(errorReporter.getErrorMessages()).hasSize(1);
    assertThat(errorReporter.getErrorMessages().get(0)).contains("Param 'foo' already declared");

    errorReporter = new FormattingErrorReporter();
    templateBasicNode(errorReporter)
        .setId(0)
        .setCmdText(".boo")
        .setHeaderDecls(
            new DeclInfo(
                Type.PARAM,
                OptionalStatus.REQUIRED,
                "goo", "null",
                "Something slimy.",
                SourceLocation.UNKNOWN),
            new DeclInfo(
                Type.PARAM,
                OptionalStatus.REQUIRED,
                "foo", "string",
                "Something random.",
                SourceLocation.UNKNOWN),
            new DeclInfo(
                Type.PARAM,
                OptionalStatus.REQUIRED,
                "foo", "int",
                null /* soyDoc */,
                SourceLocation.UNKNOWN))
        .build();
    assertThat(errorReporter.getErrorMessages()).hasSize(1);
    assertThat(errorReporter.getErrorMessages().get(0)).contains("Param 'foo' already declared");

    errorReporter = new FormattingErrorReporter();
    templateBasicNode(errorReporter)
        .setId(0)
        .setCmdText(".boo")
        .setSoyDoc("/** @param? foo Something. */")
        .setHeaderDecls(
            new DeclInfo(
                Type.PARAM,
                OptionalStatus.REQUIRED,
                "foo", "string",
                "Something else.",
                SourceLocation.UNKNOWN))
        .build();
    assertThat(errorReporter.getErrorMessages()).hasSize(1);
    assertThat(errorReporter.getErrorMessages().get(0)).contains("Param 'foo' already declared");
  }

  public void testCommandTextErrors() {
    FormattingErrorReporter errorReporter = new FormattingErrorReporter();
    new TemplateBasicNodeBuilder(
            SIMPLE_FILE_HEADER_INFO, SourceLocation.UNKNOWN, errorReporter, TYPE_REGISTRY)
        .setId(0)
        .setCmdText("autoescape=\"deprecated-noncontextual\"")
        .setSoyDoc("/***/")
        .build();
    assertThat(errorReporter.getErrorMessages()).containsExactly("Missing template name.");

    errorReporter = new FormattingErrorReporter();
    templateBasicNode(errorReporter)
        .setId(0)
        .setCmdText(".foo autoescape=\"strict")
        .setSoyDoc("/***/")
        .build();
    assertThat(errorReporter.getErrorMessages())
        .containsExactly("Malformed attributes in 'template' command text (autoescape=\"strict).");

    errorReporter = new FormattingErrorReporter();
    templateBasicNode(errorReporter)
        .setId(0)
        .setCmdText(".foo autoescape=\"false\"")
        .setSoyDoc("/***/")
        .build();
    assertThat(errorReporter.getErrorMessages())
        .containsExactly(
            "Invalid value for attribute 'autoescape' in 'template' command text "
                + "(autoescape=\"false\"). Valid values are "
                + "[deprecated-noncontextual, deprecated-contextual, strict].");

    // assertion inside no-arg templateBasicNode() is that there is no exception.
    templateBasicNode()
        .setId(0)
        .setCmdText(".foo autoescape =\n\t\r \"strict\"")
        .setSoyDoc("/***/")
        .build();
  }

  public void testValidStrictTemplates() {
    TemplateNode node;

    node = templateBasicNode()
        .setId(0)
        .setCmdText(".boo kind=\"text\" autoescape=\"strict\"")
        .setSoyDoc("/** Strict template. */").build();
    assertEquals(AutoescapeMode.STRICT, node.getAutoescapeMode());
    assertEquals(ContentKind.TEXT, node.getContentKind());

    node = templateBasicNode()
        .setId(0)
        .setCmdText(".boo autoescape=\"strict\" kind=\"html\"")
        .setSoyDoc("/** Strict template. */").build();
    assertEquals(AutoescapeMode.STRICT, node.getAutoescapeMode());
    assertEquals(ContentKind.HTML, node.getContentKind());

    // "kind" is optional, defaults to HTML
    node = templateBasicNode()
        .setId(0)
        .setCmdText(".boo autoescape=\"strict\"").setSoyDoc("/** Strict template. */")
        .build();
    assertEquals(AutoescapeMode.STRICT, node.getAutoescapeMode());
    assertEquals(ContentKind.HTML, node.getContentKind());
  }

  public void testInvalidStrictTemplates() {
    FormattingErrorReporter errorReporter = new FormattingErrorReporter();
      templateBasicNode(errorReporter)
          .setId(0)
          .setCmdText(".boo kind=\"text\"")
          .setSoyDoc("/** Strict template. */")
          .build();
    assertThat(errorReporter.getErrorMessages()).hasSize(1);
    assertThat(errorReporter.getErrorMessages().get(0)).contains(
        "kind=\"...\" attribute is only valid with autoescape=\"strict\".");
  }

  public void testValidRequiredCss() {
    TemplateNode node;

    node = templateBasicNode()
        .setId(0)
        .setCmdText(".boo requirecss=\"foo.boo\"")
        .setSoyDoc("/** Boo. */")
        .build();
    assertEquals(ImmutableList.<String>of("foo.boo"), node.getRequiredCssNamespaces());

    node = templateBasicNode()
        .setId(0)
        .setCmdText(".boo requirecss=\"foo, bar\"")
        .setSoyDoc("/** Boo. */")
        .build();
    assertEquals(ImmutableList.<String>of("foo", "bar"), node.getRequiredCssNamespaces());

    node = templateBasicNode()
        .setId(0)
        .setCmdText(".boo requirecss=\"foo.boo, foo.moo\"")
        .setSoyDoc("/** Boo. */").build();
    assertEquals(ImmutableList.<String>of("foo.boo", "foo.moo"), node.getRequiredCssNamespaces());

    // Now for deltemplates.
    node = templateDelegateNode()
        .setId(0)
        .setCmdText("namespace.boo requirecss=\"foo.boo , moo.hoo\"")
        .setSoyDoc("/** Boo. */").build();
    assertEquals(ImmutableList.<String>of("foo.boo", "moo.hoo"), node.getRequiredCssNamespaces());
  }

  public void testValidVariant() {
    // Variant is a string literal: There's no expression and the value is already resolved.
    TemplateDelegateNode node =
        templateDelegateNode()
            .setId(0)
            .setCmdText("namespace.boo variant=\"'abc'\"")
            .setSoyDoc("/** Boo. */").build();
    assertEquals("namespace.boo", node.getDelTemplateName());
    assertEquals("abc", node.getDelTemplateVariant());
    assertEquals("abc", node.getDelTemplateKey().variant());

    // Variant is a global, that was not yet resolved.
    node = templateDelegateNode()
        .setId(0)
        .setCmdText("namespace.boo variant=\"test.GLOBAL_CONSTANT\"")
        .setSoyDoc("/** Boo. */")
        .build();
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
    exprUnion.getExpr().replaceChild(
        0,
        new IntegerNode(123, exprUnion.getExpr().getRoot().getSourceLocation()));
    // Check the new values.
    assertEquals("123", node.getDelTemplateVariant());
    assertEquals("123", node.getDelTemplateKey().variant());

    // Resolve a global to a string.
    node = templateDelegateNode()
        .setId(0)
        .setCmdText("namespace.boo variant=\"test.GLOBAL_CONSTANT\"")
        .setSoyDoc("/** Boo. */")
        .build();
    node.getAllExprUnions()
        .get(0)
        .getExpr()
        .replaceChild(0, new StringNode("variant", node.getSourceLocation()));
    assertEquals("variant", node.getDelTemplateVariant());
    assertEquals("variant", node.getDelTemplateKey().variant());
  }

  public void testInvalidVariant() {
    // Try to resolve a global to an invalid type.
    TemplateDelegateNode node =
        templateDelegateNode()
            .setId(0)
            .setCmdText("namespace.boo variant=\"test.GLOBAL_CONSTANT\"")
            .setSoyDoc("/** Boo. */")
            .build();
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
    node = templateDelegateNode()
        .setId(0)
        .setCmdText("namespace.boo variant=\"test.GLOBAL_CONSTANT\"")
        .setSoyDoc("/** Boo. */")
        .build();
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

  public void testInvalidRequiredCss() {
    try {
      templateBasicNode()
          .setId(0)
          .setCmdText(".boo requirecss=\"\"")
          .setSoyDoc("/** Boo. */")
          .build();
      fail("Should be a syntax error");
    } catch (SoySyntaxException sse) {
      assertTrue(sse.getMessage(),
          sse.getMessage().contains("Invalid required CSS namespace name \"\"."));
    }

    try {
      templateBasicNode()
          .setId(0)
          .setCmdText(".boo requirecss=\"foo boo\"")
          .setSoyDoc("/** Boo. */")
          .build();
      fail("Should be a syntax error");
    } catch (SoySyntaxException sse) {
      assertTrue(sse.getMessage(),
          sse.getMessage().contains("Invalid required CSS namespace name \"foo boo\"."));
    }

    try {
      templateBasicNode()
          .setId(0)
          .setCmdText(".boo requirecss=\"9vol\"")
          .setSoyDoc("/** Boo. */")
          .build();
      fail("Should be a syntax error");
    } catch (SoySyntaxException sse) {
      assertTrue(sse.getMessage(),
          sse.getMessage().contains("Invalid required CSS namespace name \"9vol\"."));
    }

    // Now for deltemplates.
    try {
      templateDelegateNode()
          .setId(0)
          .setCmdText("namespace.boo requirecss=\"5ham\"")
          .setSoyDoc("/** Boo. */")
          .build();
      fail("Should be a syntax error");
    } catch (SoySyntaxException sse) {
      assertTrue(sse.getMessage(),
          sse.getMessage().contains("Invalid required CSS namespace name \"5ham\"."));
    }
  }


  public void testNamespaceRelativeTemplateNameButNoNamespaceDecl() {
    try {
      new TemplateBasicNodeBuilder(
          new SoyFileHeaderInfo(null /* namespace */),
          SourceLocation.UNKNOWN,
          FAIL,
          TYPE_REGISTRY)
          .setId(0)
          .setCmdText(".foo")
          .build();
      fail();
    } catch (SoySyntaxException e) {
      assertThat(e).hasMessage(
          "Template has namespace-relative name, but file has no namespace declaration.");
    }
  }

  public void testToSourceString() {
    SoyParsingContext boom = SoyParsingContext.exploding();
    TemplateNode tn = templateBasicNode()
        .setId(0)
        .setCmdText(".boo")
        .setSoyDoc("" +
            "/**\n" +
            " * Test template.\n" +
            " *\n" +
            " * @param foo Foo to print.\n" +
            " * @param goo\n" +
            " *     Goo to print.\n" +
            " */")
        .setHeaderDecls(
            new DeclInfo(
                Type.PARAM,
                OptionalStatus.REQUIRED,
                "moo", "bool",
                "Something milky.",
                SourceLocation.UNKNOWN),
            new DeclInfo(
                Type.PARAM,
                OptionalStatus.REQUIRED,
                "too", "string|null",
                null /* soyDoc */,
                SourceLocation.UNKNOWN))
        .build();
    tn.addChild(new RawTextNode(0, "  ", SourceLocation.UNKNOWN));  // 2 spaces
    tn.addChild(
        new PrintNode.Builder(0, true /* isImplicit */, SourceLocation.UNKNOWN)
            .exprText("$foo")
            .build(boom));
    tn.addChild(
        new PrintNode.Builder(0, true /* isImplicit */, SourceLocation.UNKNOWN)
            .exprText("$goo")
            .build(boom));
    tn.addChild(new RawTextNode(0, "  ", SourceLocation.UNKNOWN));  // 2 spaces

    assertEquals("" +
            "/**\n" +
            " * Test template.\n" +
            " *\n" +
            " * @param foo Foo to print.\n" +
            " * @param goo\n" +
            " *     Goo to print.\n" +
            " */\n" +
            "{template .boo}\n" +
            "  {@param moo: bool}  /** Something milky. */\n" +
            "  {@param? too: string|null}\n" +
            "{sp} {$foo}{$goo} {sp}\n" +
            "{/template}\n",
        tn.toSourceString());
  }

  private static TemplateBasicNodeBuilder templateBasicNode() {
    return templateBasicNode(FAIL);
  }

  private static TemplateBasicNodeBuilder templateBasicNode(ErrorReporter errorReporter) {
    return new TemplateBasicNodeBuilder(
        SIMPLE_FILE_HEADER_INFO, SourceLocation.UNKNOWN, errorReporter, TYPE_REGISTRY);
  }

  private static TemplateDelegateNodeBuilder templateDelegateNode() {
    return new TemplateDelegateNodeBuilder(
        SIMPLE_FILE_HEADER_INFO, SourceLocation.UNKNOWN, FAIL, TYPE_REGISTRY);
  }
}
