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

package com.google.template.soy.jbcsrc;

import static com.google.common.truth.Truth.assertThat;
import static com.google.template.soy.jbcsrc.TemplateTester.asRecord;
import static com.google.template.soy.jbcsrc.TemplateTester.assertThatElementBody;
import static com.google.template.soy.jbcsrc.TemplateTester.assertThatFile;
import static com.google.template.soy.jbcsrc.TemplateTester.assertThatTemplateBody;
import static com.google.template.soy.jbcsrc.TemplateTester.getDefaultContext;
import static com.google.template.soy.jbcsrc.TemplateTester.getDefaultContextWithDebugInfo;
import static org.junit.Assert.fail;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.template.soy.SoyFileSetParser;
import com.google.template.soy.SoyFileSetParser.CompilationUnitAndKind;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.TemplateMetadataSerializer;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.coredirectives.EscapeHtmlDirective;
import com.google.template.soy.css.CssRegistry;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.LoggingAdvisingAppendable.BufferingAppendable;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SanitizedContents;
import com.google.template.soy.data.SoyDict;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueConverterUtility;
import com.google.template.soy.data.internal.BasicParamStore;
import com.google.template.soy.data.internal.ListImpl;
import com.google.template.soy.data.internal.ParamStore;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.jbcsrc.TemplateTester.CompiledTemplateSubject;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.jbcsrc.api.SoySauce;
import com.google.template.soy.jbcsrc.api.SoySauceBuilder;
import com.google.template.soy.jbcsrc.shared.CompiledTemplate;
import com.google.template.soy.jbcsrc.shared.CompiledTemplates;
import com.google.template.soy.jbcsrc.shared.RenderContext;
import com.google.template.soy.jbcsrc.shared.TemplateMetadata;
import com.google.template.soy.plugin.java.restricted.JavaPluginContext;
import com.google.template.soy.plugin.java.restricted.JavaValue;
import com.google.template.soy.plugin.java.restricted.JavaValueFactory;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.shared.SoyGeneralOptions;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** A test for the template compiler, notably {@link BytecodeCompiler} and its collaborators. */
@RunWith(JUnit4.class)
public class BytecodeCompilerTest {
  public static final SoyList EMPTY_LIST = ListImpl.forProviderList(ImmutableList.of());

  @Test
  public void testDelCall_delPackageSelections() throws IOException {
    String soyFileContent1 =
        Joiner.on("\n")
            .join(
                "{namespace ns1}",
                "",
                "/***/",
                "{template .callerTemplate}",
                "  {delcall myApp.myDelegate}",
                "    {param boo: 'aaaaaah' /}",
                "  {/delcall}",
                "{/template}",
                "",
                "/** */",
                "{deltemplate myApp.myDelegate requirecss=\"ns.default\"}", // default
                // implementation
                // (doesn't use $boo)
                "  {@param boo : string}",
                "  default",
                "{/deltemplate}",
                "");

    String soyFileContent2 =
        Joiner.on("\n")
            .join(
                "{delpackage SecretFeature}",
                "{namespace ns2 requirecss=\"ns.foo\"}",
                "",
                "/** */",
                "{deltemplate myApp.myDelegate}", // implementation in SecretFeature
                "  {@param boo : string}",
                "  SecretFeature {$boo}",
                "{/deltemplate}",
                "");

    String soyFileContent3 =
        Joiner.on("\n")
            .join(
                "{delpackage AlternateSecretFeature}",
                "{namespace ns3 requirecss=\"ns.bar\"}",
                "",
                "/** */",
                "{deltemplate myApp.myDelegate}", // implementation in AlternateSecretFeature
                "  {@param boo : string}",
                "  AlternateSecretFeature {call .helper data=\"all\" /}",
                "{/deltemplate}",
                "");

    String soyFileContent4 =
        Joiner.on("\n")
            .join(
                "{namespace ns3}",
                "",
                "/** */",
                "{template .helper}",
                "  {@param boo : string}",
                "  {$boo}",
                "{/template}",
                "");
    SoyFileSetParser parser =
        SoyFileSetParserBuilder.forFileContents(
                soyFileContent1, soyFileContent2, soyFileContent3, soyFileContent4)
            .cssRegistry(
                CssRegistry.create(
                    ImmutableSet.of("ns.bar", "ns.foo", "ns.default"), ImmutableMap.of()))
            .build();
    ParseResult parseResult = parser.parse();
    CompiledTemplates templates =
        BytecodeCompiler.compile(
                parseResult.registry(),
                parseResult.fileSet(),
                ErrorReporter.exploding(),
                parser.soyFileSuppliers(),
                parser.typeRegistry())
            .get();
    CompiledTemplate.Factory factory = templates.getTemplateFactory("ns1.callerTemplate");
    Predicate<String> activePackages = arg -> false;
    assertThat(templates.getAllRequiredCssNamespaces("ns1.callerTemplate", activePackages, false))
        .containsExactly("ns.default");
    assertThat(
            templates.getAllRequiredCssNamespaces(
                "ns1.callerTemplate", arg -> arg.equals("SecretFeature"), false))
        .containsExactly("ns.foo");
    assertThat(
            templates.getAllRequiredCssNamespaces(
                "ns1.callerTemplate", arg -> arg.equals("AlternateSecretFeature"), false))
        .containsExactly("ns.bar");

    assertThat(
            templates.getAllRequiredCssNamespaces(
                "ns1.callerTemplate", arg -> arg.equals("SecretFeature"), false))
        .containsExactly("ns.foo");
    assertThat(
            templates.getAllRequiredCssNamespaces(
                "ns1.callerTemplate", arg -> arg.equals("AlternateSecretFeature"), false))
        .containsExactly("ns.bar");

    assertThat(renderWithContext(factory, getDefaultContext(templates, activePackages)))
        .isEqualTo("default");

    activePackages = "SecretFeature"::equals;
    assertThat(renderWithContext(factory, getDefaultContext(templates, activePackages)))
        .isEqualTo("SecretFeature aaaaaah");

    activePackages = "AlternateSecretFeature"::equals;
    assertThat(renderWithContext(factory, getDefaultContext(templates, activePackages)))
        .isEqualTo("AlternateSecretFeature aaaaaah");

    activePackages = "NonexistentFeature"::equals;
    assertThat(renderWithContext(factory, getDefaultContext(templates, activePackages)))
        .isEqualTo("default");
  }

  @Test
  public void testDebugSoyTemplateInfo() throws IOException {
    String soyFileContent =
        Joiner.on("\n")
            .join(
                "{namespace ns}",
                "",
                "{template .html}",
                "  <div>foo</div>",
                "{/template}",
                "",
                "{template .text kind=\"text\"}",
                "  foo",
                "{/template}",
                "",
                "{template .htmlNoTag}",
                "  foo",
                "{/template}");
    SoyFileSetParser parser =
        SoyFileSetParserBuilder.forFileContents(soyFileContent)
            .addHtmlAttributesForDebugging(true)
            .build();
    ParseResult parseResult = parser.parse();
    CompiledTemplates templates =
        BytecodeCompiler.compile(
                parseResult.registry(),
                parseResult.fileSet(),
                ErrorReporter.exploding(),
                parser.soyFileSuppliers(),
                parser.typeRegistry())
            .get();

    // HTML templates
    CompiledTemplate.Factory factory = templates.getTemplateFactory("ns.html");
    assertThat(renderWithContext(factory, getDefaultContext(templates)))
        .isEqualTo("<div>foo</div>");
    // If debugSoyTemplateInfo is enabled, we should render additional HTML comments.
    assertThat(renderWithContext(factory, getDefaultContextWithDebugInfo(templates)))
        .isEqualTo("<div data-debug-soy=\"ns.html no-path:4\">foo</div>");

    // We should never render these comments for templates with kind="text".
    factory = templates.getTemplateFactory("ns.text");
    assertThat(renderWithContext(factory, getDefaultContext(templates))).isEqualTo("foo");
    assertThat(renderWithContext(factory, getDefaultContextWithDebugInfo(templates)))
        .isEqualTo("foo");

    factory = templates.getTemplateFactory("ns.htmlNoTag");
    assertThat(renderWithContext(factory, getDefaultContext(templates))).isEqualTo("foo");
    assertThat(renderWithContext(factory, getDefaultContextWithDebugInfo(templates)))
        .isEqualTo("foo");
  }

  private static String renderWithContext(CompiledTemplate.Factory factory, RenderContext context)
      throws IOException {
    BufferingAppendable builder = LoggingAdvisingAppendable.buffering();
    assertThat(
            factory
                .create(ParamStore.EMPTY_INSTANCE, ParamStore.EMPTY_INSTANCE)
                .render(builder, context))
        .isEqualTo(RenderResult.done());
    String string = builder.toString();
    return string;
  }

  @Test
  public void testDelCall_delVariant() throws IOException {
    String soyFileContent1 =
        Joiner.on("\n")
            .join(
                "{namespace ns1}",
                "",
                "/***/",
                "{template .callerTemplate}",
                "  {@param variant : string}",
                "  {delcall ns1.del variant=\"$variant\" allowemptydefault=\"true\"/}",
                "{/template}",
                "",
                "/** */",
                "{deltemplate ns1.del variant=\"'v1'\" requirecss=\"ns.foo\"}",
                "  v1",
                "{/deltemplate}",
                "",
                "/** */",
                "{deltemplate ns1.del variant=\"'v2'\" requirecss=\"ns.bar\"}",
                "  v2",
                "{/deltemplate}",
                "");

    CompiledTemplates templates = compileFiles(soyFileContent1);
    assertThat(templates.getAllRequiredCssNamespaces("ns1.callerTemplate", (arg) -> false, false))
        .isEmpty();
    CompiledTemplate.Factory factory = templates.getTemplateFactory("ns1.callerTemplate");
    RenderContext context = getDefaultContext(templates);
    BufferingAppendable builder = LoggingAdvisingAppendable.buffering();
    assertThat(
            factory
                .create(
                    TemplateTester.asRecord(ImmutableMap.of("variant", "v1")),
                    ParamStore.EMPTY_INSTANCE)
                .render(builder, context))
        .isEqualTo(RenderResult.done());
    assertThat(builder.getAndClearBuffer()).isEqualTo("v1");

    assertThat(
            factory
                .create(
                    TemplateTester.asRecord(ImmutableMap.of("variant", "v2")),
                    ParamStore.EMPTY_INSTANCE)
                .render(builder, context))
        .isEqualTo(RenderResult.done());
    assertThat(builder.getAndClearBuffer()).isEqualTo("v2");

    assertThat(
            factory
                .create(
                    TemplateTester.asRecord(ImmutableMap.of("variant", "unknown")),
                    ParamStore.EMPTY_INSTANCE)
                .render(builder, context))
        .isEqualTo(RenderResult.done());
    assertThat(builder.toString()).isEmpty();

    TemplateMetadata templateMetadata = getTemplateMetadata(templates, "ns1.callerTemplate");
    assertThat(templateMetadata.callees()).isEmpty();
    assertThat(templateMetadata.delCallees()).asList().containsExactly("ns1.del");
  }

  @Test
  public void testCallBasicNode() throws IOException {
    CompiledTemplates templates =
        TemplateTester.compileFileWithCss(
            CssRegistry.create(ImmutableSet.of("ns.foo"), ImmutableMap.of()),
            "{namespace ns requirecss=\"ns.foo\"}",
            "",
            "/** */",
            "{template .callerDataAll requirecss=\"ns.bar\"}",
            "  {@param foo : string}",
            "  {call .callee data=\"all\" /}",
            "{/template}",
            "",
            "/** */",
            "{template .callerDataExpr}",
            "  {@param rec : [foo : string]}",
            "  {call .callee data=\"$rec\" /}",
            "{/template}",
            "",
            "/** */",
            "{template .callerParams}",
            "  {@param p1 : string}",
            "  {call .callee}",
            "    {param foo : $p1 /}",
            "    {param boo : 'a' + 1 + 'b' /}",
            "  {/call}",
            "{/template}",
            "",
            "/** */",
            "{template .callerParamsAndData}",
            "  {@param p1 : string}",
            "  {call .callee data=\"all\"}",
            "    {param foo : $p1 /}",
            "  {/call}",
            "{/template}",
            "",
            "/** */",
            "{template .callee}",
            "  {@param foo : string}",
            "  {@param? boo : string}",
            "Foo: {$foo}{\\n}",
            "Boo: {$boo}{\\n}",
            "{/template}",
            "");
    ParamStore params = new BasicParamStore(2);
    params.setField("foo", StringData.forValue("foo"));
    assertThat(render(templates, params, "ns.callerDataAll")).isEqualTo("Foo: foo\nBoo: null\n");
    params.setField("boo", StringData.forValue("boo"));
    assertThat(render(templates, params, "ns.callerDataAll")).isEqualTo("Foo: foo\nBoo: boo\n");

    assertThat(getTemplateMetadata(templates, "ns.callerDataAll").callees())
        .asList()
        .containsExactly("ns.callee");

    params = new BasicParamStore(2);
    params.setField("rec", new BasicParamStore(2).setField("foo", StringData.forValue("foo")));
    assertThat(render(templates, params, "ns.callerDataExpr")).isEqualTo("Foo: foo\nBoo: null\n");
    ((ParamStore) params.getField("rec")).setField("boo", StringData.forValue("boo"));
    assertThat(render(templates, params, "ns.callerDataExpr")).isEqualTo("Foo: foo\nBoo: boo\n");
    assertThat(getTemplateMetadata(templates, "ns.callerDataExpr").callees())
        .asList()
        .containsExactly("ns.callee");

    params = new BasicParamStore(2);
    params.setField("p1", StringData.forValue("foo"));
    assertThat(render(templates, params, "ns.callerParams")).isEqualTo("Foo: foo\nBoo: a1b\n");
    assertThat(getTemplateMetadata(templates, "ns.callerParams").callees())
        .asList()
        .containsExactly("ns.callee");

    params = new BasicParamStore(2);
    params.setField("p1", StringData.forValue("foo"));
    params.setField("boo", StringData.forValue("boo"));
    assertThat(render(templates, params, "ns.callerParamsAndData"))
        .isEqualTo("Foo: foo\nBoo: boo\n");
    assertThat(getTemplateMetadata(templates, "ns.callerParamsAndData").callees())
        .asList()
        .containsExactly("ns.callee");
  }

  @Test
  public void testRequireCss() throws IOException {
    CompiledTemplates templates =
        TemplateTester.compileFileWithCss(
            CssRegistry.create(ImmutableSet.of("ns.foo", "ns.bar"), ImmutableMap.of()),
            "{namespace ns requirecss=\"ns.foo\"}",
            "",
            "/** */",
            "{template .requireCss requirecss=\"ns.bar\"}",
            "{/template}",
            "");
    TemplateMetadata metadata = getTemplateMetadata(templates, "ns.requireCss");
    Predicate<String> activePackages = arg -> false;
    assertThat(metadata.requiredCssNames()).asList().containsExactly("ns.foo", "ns.bar");
    assertThat(templates.getAllRequiredCssNamespaces("ns.requireCss", activePackages, false))
        .containsExactly("ns.foo", "ns.bar");
  }

  @Test
  public void testMsg() throws IOException {
    CompiledTemplates templates =
        TemplateTester.compileFile(
            "{namespace ns}",
            "",
            "{template .msg}",
            "  {msg desc='description'}",
            "    {call .t data='all' /}",
            "  {/msg}",
            "{/template}",
            "",
            "{template .t}",
            "  foobar",
            "{/template}");

    assertThat(render(templates, ParamStore.EMPTY_INSTANCE, "ns.msg")).isEqualTo("foobar");
  }

  private static TemplateMetadata getTemplateMetadata(CompiledTemplates templates, String name) {
    return templates
        .getTemplateFactory(name)
        .getClass()
        .getDeclaringClass()
        .getAnnotation(TemplateMetadata.class);
  }

  private String render(CompiledTemplates templates, SoyRecord params, String name)
      throws IOException {
    CompiledTemplate caller =
        templates.getTemplateFactory(name).create(params, ParamStore.EMPTY_INSTANCE);
    BufferingAppendable builder = LoggingAdvisingAppendable.buffering();
    assertThat(caller.render(builder, getDefaultContext(templates))).isEqualTo(RenderResult.done());
    String output = builder.toString();
    return output;
  }

  @Test
  public void testForRangeNode() {
    // empty loop
    assertThatTemplateBody("{for $i in range(2, 2)}", "  {$i}", "{/for}").rendersAs("");

    assertThatTemplateBody("{for $i in range(10)}", "  {$i}", "{/for}").rendersAs("0123456789");

    assertThatTemplateBody("{for $i in range(2, 10)}", "  {$i}", "{/for}").rendersAs("23456789");

    assertThatTemplateBody("{for $i in range(2, 10, 2)}", "  {$i}", "{/for}").rendersAs("2468");

    assertThatTemplateBody("{for $i in range(0, 10, 65536 * 65536 * 65536)}", "  {$i}", "{/for}")
        .failsToRenderWith(IllegalArgumentException.class);

    assertThatTemplateBody(
            "{for $i in range(65536 * 65536 * 65536, 65536 * 65536 * 65536 + 1)}",
            "  {$i}",
            "{/for}")
        .failsToRenderWith(IllegalArgumentException.class);
  }

  @Test
  public void testForNode() {
    // empty loop
    assertThatTemplateBody("{@param list: list<int>}", "{for $i in $list}", "  {$i}", "{/for}")
        .rendersAs("", ImmutableMap.of("list", EMPTY_LIST));

    assertThatTemplateBody(
            "{@param list: list<int>}",
            "{for $i in $list}",
            "  {$i}",
            "{ifempty}",
            "  empty",
            "{/for}")
        .rendersAs("empty", ImmutableMap.of("list", EMPTY_LIST));

    assertThatTemplateBody("{for $i in [1,2,3,4,5]}", "  {$i}", "{/for}").rendersAs("12345");

    assertThatTemplateBody(
            "{for $i in [1,2,3,4,5]}",
            "  {if isFirst($i)}",
            "    first!{\\n}",
            "  {/if}",
            "  {$i}-{index($i)}{\\n}",
            "  {if isLast($i)}",
            "    last!",
            "  {/if}",
            "{/for}")
        .rendersAs(Joiner.on('\n').join("first!", "1-0", "2-1", "3-2", "4-3", "5-4", "last!"));
  }

  @Test
  public void testForLoop_rangeOverConstant() {
    assertThatTemplateBody("{let $len: 10/}{for $i in range($len)}{$i}{/for}")
        .rendersAs("0123456789");
  }

  @Test
  public void testForNode_mapKeys() {
    assertThatTemplateBody(
            "{@param map : map<string, int>}",
            "{for $key in mapKeys($map)}",
            "  {$key} - {$map[$key]}{if not isLast($key)}{\\n}{/if}",
            "{/for}")
        .rendersAs("a - 1\nb - 2", ImmutableMap.of("map", ImmutableMap.of("a", 1, "b", 2)));
  }

  @Test
  public void testForNode_nullableList() {
    // The compiler should be rejected this :(
    assertThatTemplateBody(
            "{@param map : map<string, list<int>>}",
            "{for $item in $map?['key']}",
            "  {$item}",
            "{/for}")
        .rendersAs(
            "123", ImmutableMap.of("map", ImmutableMap.of("key", ImmutableList.of(1, 2, 3))));
  }

  @Test
  public void testStateNodeNumber() {
    assertThatElementBody("{@state foo: number= 1}", "<a>{$foo}</a>").rendersAs("<a>1</a>");
    assertThatElementBody("{@state foo:= 1}", "<p>{$foo}</p>").rendersAs("<p>1</p>");
  }

  @Test
  public void testStateNodeBoolean() {
    assertThatElementBody("{@state foo:= 1}", "<p>{if $foo}1{else}0{/if}</p>")
        .rendersAs("<p>1</p>");
  }

  @Test
  public void testSwitchNode() {
    assertThatTemplateBody(
            "{switch 1}",
            "  {case 1}",
            "    one",
            "  {case 2}",
            "    two",
            "  {default}",
            "    default",
            "{/switch}")
        .rendersAs("one");

    assertThatTemplateBody(
            "{switch 2}",
            "  {case 1}",
            "    one",
            "  {case 2}",
            "    two",
            "  {default}",
            "    default",
            "{/switch}")
        .rendersAs("two");
    assertThatTemplateBody(
            "{switch 'asdf'}",
            "  {case 1}",
            "    one",
            "  {case 2}",
            "    two",
            "  {default}",
            "    default",
            "{/switch}")
        .rendersAs("default");
  }

  @Test
  public void testNestedSwitch() {
    assertThatTemplateBody(
            "{switch 'a'}",
            "  {case 'a'}",
            "    {switch 1} {case 1} sub {default} sub default {/switch}",
            "  {case 2}",
            "    two",
            "  {default}",
            "    default",
            "{/switch}")
        .rendersAs(" sub ");
  }

  @Test
  public void testIfNode() {
    assertThatTemplateBody("{if true}", "  hello", "{/if}").rendersAs("hello");

    assertThatTemplateBody("{if false}", "  hello", "{/if}").rendersAs("");

    assertThatTemplateBody("{if false}", "  one", "{elseif false}", "  two", "{/if}").rendersAs("");
    assertThatTemplateBody("{if true}", "  one", "{elseif false}", "  two", "{/if}")
        .rendersAs("one");
    assertThatTemplateBody("{if false}", "  one", "{elseif true}", "  two", "{/if}")
        .rendersAs("two");

    assertThatTemplateBody(
            "{if true}", "  one", "{elseif true}", "  two", "{else}", "  three", "{/if}")
        .rendersAs("one");
    assertThatTemplateBody(
            "{if false}", "  one", "{elseif true}", "  two", "{else}", "  three", "{/if}")
        .rendersAs("two");
    assertThatTemplateBody(
            "{if false}", "  one", "{elseif false}", "  two", "{else}", "  three", "{/if}")
        .rendersAs("three");
  }

  @Test
  public void testIfNode_nullableBool() {
    CompiledTemplateSubject tester =
        assertThatTemplateBody(
            "{@param? cond1 : bool}",
            "{@param cond2 : bool}",
            "{if $cond2 or $cond1}",
            "  hello",
            "{else}",
            "  goodbye",
            "{/if}");
    tester.rendersAs("goodbye", ImmutableMap.of("cond2", false));
    tester.rendersAs("hello", ImmutableMap.of("cond2", true));
    tester.rendersAs("goodbye", ImmutableMap.of("cond1", false, "cond2", false));
    tester.rendersAs("hello", ImmutableMap.of("cond1", true, "cond2", false));
  }

  @Test
  public void testPrintNode() {
    assertThatTemplateBody("{1 + 2}").rendersAs("3");
    assertThatTemplateBody("{'asdf'}").rendersAs("asdf");
  }

  @Test
  public void testLogNode() {
    assertThatTemplateBody("{log}", "  hello{sp}", "  {'world'}", "{/log}")
        .logsOutput("hello world");
  }

  @Test
  public void testRawTextNode() {
    assertThatTemplateBody("hello raw text world").rendersAs("hello raw text world");
  }

  @Test
  public void testRawTextNode_largeText() {
    // This string is larger than the max constant pool entry size
    String largeString = Strings.repeat("x", 1 << 17);
    assertThatTemplateBody(largeString).rendersAs(largeString);
    assertThatTemplateBody("{@param foo:?}\n{'" + largeString + "' + $foo}")
        .rendersAs(largeString + "hello", ImmutableMap.of("foo", "hello"));
  }

  @Test
  public void testCssFunction() {
    FakeRenamingMap renamingMap = new FakeRenamingMap(ImmutableMap.of("foo", "bar"));
    assertThatTemplateBody("{css('foo')}").withCssRenamingMap(renamingMap).rendersAs("bar");
    assertThatTemplateBody("{css('foo2')}").withCssRenamingMap(renamingMap).rendersAs("foo2");
    assertThatTemplateBody("{css(1+2, 'foo')}").withCssRenamingMap(renamingMap).rendersAs("3-bar");
    assertThatTemplateBody("{css(1+2, 'foo2')}")
        .withCssRenamingMap(renamingMap)
        .rendersAs("3-foo2");
  }

  @Test
  public void testXidFunction() {
    FakeRenamingMap renamingMap = new FakeRenamingMap(ImmutableMap.of("foo", "bar"));
    assertThatTemplateBody("{xid('foo')}").withXidRenamingMap(renamingMap).rendersAs("bar");
    assertThatTemplateBody("{xid('foo2')}").withXidRenamingMap(renamingMap).rendersAs("foo2_");
  }

  @Test
  public void testCallCustomFunction() {
    SoyJavaFunction plusOneFunction =
        new SoyJavaFunction() {
          @Override
          public Set<Integer> getValidArgsSizes() {
            return ImmutableSet.of(1);
          }

          @Override
          public String getName() {
            return "plusOne";
          }

          @Override
          public SoyValue computeForJava(List<SoyValue> args) {
            return IntegerData.forValue(args.get(0).integerValue() + 1);
          }
        };
    assertThatTemplateBody("{plusOne(1)}").withLegacySoyFunction(plusOneFunction).rendersAs("2");
  }

  @SoyFunctionSignature(
      name = "plusOne",
      value = @Signature(parameterTypes = "int", returnType = "int"))
  private static class PlusOneSourceFunction implements SoyJavaSourceFunction {
    @Override
    public JavaValue applyForJavaSource(
        JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
      return factory.callStaticMethod(
          JavaValueFactory.createMethod(BytecodeCompilerTest.class, "plusOne", int.class),
          args.get(0));
    }
  }

  public static int plusOne(int val) {
    return val + 1;
  }

  @Test
  public void testCallCustomSourceFunction() {
    SoyJavaSourceFunction plusOneFunction = new PlusOneSourceFunction();
    assertThatTemplateBody("{plusOne(1)}").withSoySourceFunction(plusOneFunction).rendersAs("2");
  }

  @Test
  public void testIsNonNull() {
    assertThatTemplateBody("{@param foo : [a : [ b : string]] }", "{isNonnull($foo.a)}")
        .rendersAs(
            "false", ImmutableMap.<String, Object>of("foo", ImmutableMap.<String, String>of()));
  }

  // Tests for a bug in an integration test where unnecessary float unboxing conversions happened.
  @Test
  public void testBoxedIntComparisonFromFunctions() {
    assertThatTemplateBody(
            "{@param list : list<int>}",
            "{for $item in $list}",
            "{if index($item) == ceiling(length($list) / 2) - 1}",
            "  Middle.",
            "{/if}",
            "{/for}",
            "")
        .rendersAs("Middle.", ImmutableMap.of("list", ImmutableList.of(1, 2, 3)));
  }

  @Test
  public void testOptionalListIteration() {
    CompiledTemplateSubject tester =
        assertThatTemplateBody(
            "{@param? list : list<int>}",
            "{if $list}",
            "  {for $item in $list}",
            "    {$item}",
            "  {/for}",
            "{/if}",
            "");
    tester.rendersAs("123", ImmutableMap.of("list", ImmutableList.of(1, 2, 3)));
    tester.rendersAs("");
  }

  @Test
  public void testPrintDirectives() {
    assertThatTemplateBody("{' blah aablahblahblah' |insertWordBreaks:8}")
        .rendersAs(" blah aablahbl<wbr>ahblah");
  }

  @Test
  public void testParam() {
    assertThatTemplateBody("{@param foo : int }", "{$foo + 1}")
        .rendersAs("2", ImmutableMap.of("foo", 1))
        .rendersAs("3", ImmutableMap.of("foo", 2))
        .rendersAs("4", ImmutableMap.of("foo", 3));
  }

  @Test
  public void testParam_headerDocParam() {
    assertThatFile(
            "{namespace ns}",
            "{template .foo}",
            "  {@param foo: ?}  /** A foo */",
            "  {$foo + 1}",
            "{/template}",
            "")
        .rendersAs("2", ImmutableMap.of("foo", 1))
        .rendersAs("3", ImmutableMap.of("foo", 2))
        .rendersAs("4", ImmutableMap.of("foo", 3));
  }

  @Test
  public void testInjectParam() {
    assertThatTemplateBody("{@inject foo : int }", "{$foo + 1}")
        .rendersAs("2", ImmutableMap.of(), ImmutableMap.of("foo", 1))
        .rendersAs("3", ImmutableMap.of(), ImmutableMap.of("foo", 2))
        .rendersAs("4", ImmutableMap.of(), ImmutableMap.of("foo", 3));
  }

  @Test
  public void testDefaultParam() {
    assertThatTemplateBody("{@param default:= 18}", "{$default}")
        .rendersAs("18", ImmutableMap.of())
        .rendersAs("-12", ImmutableMap.of("default", -12));

    // This can't be an ImmutableMap because they don't allow null values.
    Map<String, Object> nullValue = new HashMap<>();
    nullValue.put("nullable", null);
    assertThatTemplateBody("{@param nullable : null|string = 'default'}", "{$nullable}")
        .rendersAs("default", ImmutableMap.of())
        .rendersAs("override", ImmutableMap.of("nullable", "override"))
        .rendersAs("null", nullValue)
        .rendersAs("null", ImmutableMap.of("nullable", NullData.INSTANCE));
  }

  @Test
  public void testDebugger() {
    assertThatTemplateBody("{debugger}").rendersAs("");
  }

  @Test
  public void testParamValidation() throws Exception {
    CompiledTemplates templates =
        TemplateTester.compileTemplateBody("{@param foo : int}", "{$foo ?: -1}");
    CompiledTemplate.Factory singleParam = templates.getTemplateFactory("ns.foo");
    RenderContext context = getDefaultContext(templates);
    BufferingAppendable builder = LoggingAdvisingAppendable.buffering();

    SoyDict params = SoyValueConverterUtility.newDict("foo", IntegerData.forValue(1));
    singleParam.create(params, ParamStore.EMPTY_INSTANCE).render(builder, context);
    assertThat(builder.getAndClearBuffer()).isEqualTo("1");

    singleParam
        .create(ParamStore.EMPTY_INSTANCE, ParamStore.EMPTY_INSTANCE)
        .render(builder, context);
    assertThat(builder.getAndClearBuffer()).isEqualTo("-1");

    templates = TemplateTester.compileTemplateBody("{@inject foo : int}", "{$foo}");
    CompiledTemplate.Factory singleIj = templates.getTemplateFactory("ns.foo");
    context = getDefaultContext(templates);

    params = SoyValueConverterUtility.newDict("foo", IntegerData.forValue(1));
    singleIj.create(ParamStore.EMPTY_INSTANCE, params).render(builder, context);
    assertThat(builder.getAndClearBuffer()).isEqualTo("1");

    singleIj.create(ParamStore.EMPTY_INSTANCE, ParamStore.EMPTY_INSTANCE).render(builder, context);
    assertThat(builder.getAndClearBuffer()).isEqualTo("null");
  }

  @Test
  public void testParamFields() throws Exception {
    CompiledTemplate.Factory multipleParams =
        TemplateTester.compileTemplateBody(
                "{@param foo : string}",
                "{@param baz : string}",
                "{@inject bar : string}",
                "{@param defaultP:= 'orange'}",
                "{$foo + $baz + $bar + $defaultP}")
            .getTemplateFactory("ns.foo");
    SoyDict params =
        SoyValueConverterUtility.newDict(
            "foo", StringData.forValue("foo"),
            "bar", StringData.forValue("bar"),
            "baz", StringData.forValue("baz"));
    CompiledTemplate template = multipleParams.create(params, params);
    assertThat(getField("foo", template)).isEqualTo(StringData.forValue("foo"));
    assertThat(getField("bar", template)).isEqualTo(StringData.forValue("bar"));
    assertThat(getField("baz", template)).isEqualTo(StringData.forValue("baz"));
    assertThat(getField("defaultP", template)).isEqualTo(StringData.forValue("orange"));

    SoyDict overrideParam =
        SoyValueConverterUtility.newDict(
            "foo", StringData.forValue("foo"),
            "bar", StringData.forValue("bar"),
            "baz", StringData.forValue("baz"),
            "defaultP", StringData.forValue("green"));
    template = multipleParams.create(overrideParam, overrideParam);
    assertThat(getField("defaultP", template)).isEqualTo(StringData.forValue("green"));

    TemplateMetadata templateMetadata = template.getClass().getAnnotation(TemplateMetadata.class);
    assertThat(templateMetadata.injectedParams()).asList().containsExactly("bar");
    assertThat(templateMetadata.callees()).isEmpty();
    assertThat(templateMetadata.delCallees()).isEmpty();
  }

  private Object getField(String name, CompiledTemplate template) throws Exception {
    Field declaredField = template.getClass().getDeclaredField(name);
    declaredField.setAccessible(true);
    return declaredField.get(template);
  }

  @Test
  public void testPassHtmlAsNullableString() throws Exception {
    CompiledTemplateSubject subject =
        TemplateTester.assertThatFile(
            "{namespace ns}",
            "{template .foo}",
            "  {@param? content : string}",
            "  {$content ?: 'empty'}",
            "{/template}");
    subject.rendersAs("empty");
    subject.rendersAs(
        "<b>hello</b>", ImmutableMap.of("content", SanitizedContents.constantHtml("<b>hello</b>")));
  }

  @Test
  public void testBasicFunctionality() {
    // make sure we don't break standard reflection access
    CompiledTemplate.Factory factory =
        TemplateTester.compileTemplateBody("hello world").getTemplateFactory("ns.foo");
    assertThat(factory.getClass().getName())
        .isEqualTo("com.google.template.soy.jbcsrc.gen.ns.foo$Factory");
    assertThat(factory.getClass().getSimpleName()).isEqualTo("Factory");

    CompiledTemplate templateInstance =
        factory.create(ParamStore.EMPTY_INSTANCE, ParamStore.EMPTY_INSTANCE);
    Class<? extends CompiledTemplate> templateClass = templateInstance.getClass();
    assertThat(templateClass.getName()).isEqualTo("com.google.template.soy.jbcsrc.gen.ns.foo");
    assertThat(templateClass.getSimpleName()).isEqualTo("foo");

    TemplateMetadata templateMetadata = templateClass.getAnnotation(TemplateMetadata.class);
    assertThat(templateMetadata.contentKind()).isEqualTo("HTML");
    assertThat(templateInstance.kind()).isEqualTo(ContentKind.HTML);
    assertThat(templateMetadata.injectedParams()).isEmpty();
    assertThat(templateMetadata.callees()).isEmpty();
    assertThat(templateMetadata.delCallees()).isEmpty();

    // ensure that the factory is an inner class of the template.
    assertThat(factory.getClass().getEnclosingClass()).isEqualTo(templateClass);
    assertThat(factory.getClass().getDeclaringClass()).isEqualTo(templateClass);

    assertThat(templateClass.getDeclaredClasses()).asList().contains(factory.getClass());
  }

  @Test
  public void testBasicFunctionality_privateTemplate() {
    // make sure you can't access factories for priate tempaltes
    CompiledTemplates templates =
        TemplateTester.compileFile(
            "{namespace ns}{template .foo visibility=\"private\"}hello world{/template}");
    try {
      templates.getTemplateFactory("ns.foo");
      fail();
    } catch (IllegalArgumentException expected) {
    }
    // we can still access metadata
    assertThat(templates.getTemplateContentKind("ns.foo")).isEqualTo(ContentKind.HTML);
  }

  @Test
  public void testRenderMsgStmt() throws Exception {
    assertThatTemplateBody(
            "{@param quota : int}",
            "{@param url : string}",
            "{msg desc=\"msg with placeholders.\"}",
            "  You're currently using {$quota} MB of your quota.{sp}",
            "  <a href=\"{$url}\">Learn more</A>",
            "  <br /><br />",
            "{/msg}",
            "{msg meaning=\"noun\" desc=\"\" hidden=\"true\"}Archive{/msg}",
            "{msg meaning=\"noun\" desc=\"The archive (noun).\"}Archive{/msg}",
            "{msg meaning=\"verb\" desc=\"\"}Archive{/msg}",
            "{msg desc=\"\"}Archive{/msg}",
            "")
        .rendersAs(
            "You're currently using 26 MB of your quota. "
                + "<a href=\"http://foo.com\">Learn more</A>"
                + "<br/><br/>"
                + "ArchiveArchiveArchiveArchive",
            ImmutableMap.of("quota", 26, "url", "http://foo.com"));
  }

  @Test
  public void testMsg_emptyPlaceholder() throws IOException {
    // regression for a bug where this would cause a crash
    assertThatTemplateBody("  {msg desc='...'}{''}{/msg}").rendersAs("");
  }

  @Test
  public void testGenders() {
    CompiledTemplateSubject tester =
        assertThatTemplateBody(
            "{@param userGender : string}",
            "{@param targetName : string}",
            "{@param targetGender : string}",
            "{msg genders=\"$userGender, $targetGender\" desc=\"...\"}",
            "  You replied to {$targetName}.",
            "{/msg}",
            "");
    tester.rendersAs(
        "You replied to bender the offender.",
        ImmutableMap.of(
            "userGender", "male",
            "targetName", "bender the offender",
            "targetGender", "male"));
    tester.rendersAs(
        "You replied to gender bender.",
        ImmutableMap.of(
            "userGender", "male",
            "targetName", "gender bender",
            "targetGender", "female"));
  }

  @Test
  public void testPlurals() {
    CompiledTemplateSubject tester =
        assertThatTemplateBody(
            "{@param items: list<[foo: string]>}",
            "{msg desc=\"...\"}",
            "  {plural length($items)}",
            "      {case 0}Unused plural form",
            "      {case 1}{$items[0].foo}",
            "      {case 2}{$items[1]?.foo}, {$items[0]?.foo}",
            "      {default}{$items[2]?.foo} and some more",
            "   {/plural}",
            "{/msg}",
            "");
    tester.rendersAs(
        "hello", ImmutableMap.of("items", ImmutableList.of(ImmutableMap.of("foo", "hello"))));
  }

  // Tests for a bug where we would overescape deltemplates at the call site when the strict
  // content kind of the deltemplate was unknown at compile time.
  @Test
  public void testDelCallEscaping_separateCompilation() throws IOException {
    String soyFileContent1 =
        Joiner.on("\n")
            .join(
                "{namespace ns}",
                "",
                "{template .callerTemplate}",
                "  {delcall myApp.myDelegate/}",
                "{/template}",
                "");
    SoyFileSetParser parser = SoyFileSetParserBuilder.forFileContents(soyFileContent1).build();
    ParseResult parseResult = parser.parse();
    SoyFileSetNode soyTree = parseResult.fileSet();
    TemplateRegistry templateRegistry = parseResult.registry();
    // apply an escaping directive to the callsite, just like the autoescaper would
    CallDelegateNode cdn =
        SoyTreeUtils.getAllNodesOfType(soyTree.getChild(0), CallDelegateNode.class).get(0);
    cdn.setEscapingDirectives(ImmutableList.of(new EscapeHtmlDirective()));
    CompiledTemplates templates =
        BytecodeCompiler.compile(
                templateRegistry,
                soyTree,
                ErrorReporter.exploding(),
                parser.soyFileSuppliers(),
                parser.typeRegistry())
            .get();
    CompiledTemplate.Factory caller = templates.getTemplateFactory("ns.callerTemplate");
    try {
      renderWithContext(caller, getDefaultContext(templates));
      fail();
    } catch (IllegalArgumentException iae) {
      assertThat(iae)
          .hasMessageThat()
          .isEqualTo(
              "Found no active impl for delegate call to \"myApp.myDelegate\" (and delcall does "
                  + "not set allowemptydefault=\"true\").");
    }
    String soyFileContent2 =
        Joiner.on("\n")
            .join(
                "{namespace ns2}",
                "",
                "{deltemplate myApp.myDelegate}",
                "  <span>Hello</span>",
                "{/deltemplate}",
                "");
    CompiledTemplates templatesWithDeltemplate = compileFiles(soyFileContent2);
    // By passing an alternate context, we ensure the deltemplate selector contains the delegate
    assertThat(renderWithContext(caller, getDefaultContext(templatesWithDeltemplate)))
        .isEqualTo("<span>Hello</span>");
  }

  @Test
  public void testExpressionLineNumbers() throws Exception {
    CompiledTemplates templates =
        TemplateTester.compileFile(
            Joiner.on("\n")
                .join(
                    "{namespace ns}",
                    "",
                    "{template .foo}",
                    "  {@param p1 : ?}",
                    "  {@param p2 : ?}",
                    "  {@param p3 : ?}",
                    "  {@param p4 : ?}",
                    // This is a single expression split across multiple lines
                    "{$p1",
                    " + $p2",
                    " + $p3",
                    " + $p4",
                    "}",
                    "{/template}"));
    assertThat(
            render(
                templates, asRecord(ImmutableMap.of("p1", 1, "p2", 2, "p3", 3, "p4", 4)), "ns.foo"))
        .isEqualTo("10");
    ListenableFuture<?> failed = Futures.immediateFailedFuture(new RuntimeException("boom"));
    // since each parameter is on a different source line, depending on which one is assigned the
    // failed future, the template should show a different line number
    try {
      render(
          templates, asRecord(ImmutableMap.of("p1", failed, "p2", 2, "p3", 3, "p4", 4)), "ns.foo");
      fail();
    } catch (Exception e) {
      assertThat(getTemplateLineNumber("ns.foo", e)).isEqualTo(8);
    }
    try {
      render(
          templates, asRecord(ImmutableMap.of("p1", 1, "p2", failed, "p3", 3, "p4", 4)), "ns.foo");
      fail();
    } catch (Exception e) {
      assertThat(getTemplateLineNumber("ns.foo", e)).isEqualTo(9);
    }
    try {
      render(
          templates, asRecord(ImmutableMap.of("p1", 1, "p2", 2, "p3", failed, "p4", 4)), "ns.foo");
      fail();
    } catch (Exception e) {
      assertThat(getTemplateLineNumber("ns.foo", e)).isEqualTo(10);
    }
    try {
      render(
          templates, asRecord(ImmutableMap.of("p1", 1, "p2", 2, "p3", 3, "p4", failed)), "ns.foo");
      fail();
    } catch (Exception e) {
      assertThat(getTemplateLineNumber("ns.foo", e)).isEqualTo(11);
    }
  }

  // There used to be missing information from the line numbers assigned to the list expressions
  // in foreach loop which would 'blame' the previous statement, causing much confusion.  Make sure
  // it is accurate.
  @Test
  public void testForeachLoopLineNumbers() throws Exception {
    CompiledTemplates templates =
        TemplateTester.compileFile(
            Joiner.on("\n")
                .join(
                    "{namespace ns}",
                    "",
                    "{template .foo}",
                    "  {@param list : ?}",
                    "  {@param? opt : ?}",
                    "{if not $opt}",
                    // failures on the foreach loop used to get assigned the line number of the
                    // if statement.
                    "  {for $foo in $list}",
                    "    {$foo}{if not isLast($foo)}{sp}{/if}",
                    "  {/for}",
                    "{/if}",
                    "{/template}"));
    assertThat(
            render(templates, asRecord(ImmutableMap.of("list", ImmutableList.of(1, 2))), "ns.foo"))
        .isEqualTo("1 2");
    ListenableFuture<?> failed = Futures.immediateFailedFuture(new RuntimeException("boom"));
    // since each parameter is on a different source line, depending on which one is assigned the
    // failed future, the template should show a different line number
    try {
      render(templates, asRecord(ImmutableMap.of("list", failed)), "ns.foo");
      fail();
    } catch (Exception e) {
      assertThat(getTemplateLineNumber("ns.foo", e)).isEqualTo(7);
    }
  }

  /**
   * Regression test for b/77321078. It used to be that we would generate bad map access code if the
   * calculated type was a union.
   *
   * <p>It used to throw a class cast exception because we defaulted to thinking that the ternary
   * expression generated a legacy_object_map.
   */
  @Test
  public void testMapUnion() {
    CompiledTemplateSubject tester =
        assertThatTemplateBody(
            "{@param b : bool}",
            // because one of these is a map<string,int> and the other is a map<string,string>
            // the overall type is a map<string,int>|map<string,string>
            "{($b ? map('a': 1) : map('a': '2'))['a']}",
            "");
    tester.rendersAs("1", ImmutableMap.of("b", true));
    tester.rendersAs("2", ImmutableMap.of("b", false));
  }

  @SuppressWarnings("unused")
  public static int acceptsInt(int x) {
    throw new IllegalStateException("shouldn't call this");
  }

  @SoyFunctionSignature(
      name = "overflow",
      value = @Signature(parameterTypes = "int", returnType = "?"))
  private static final class Overflow implements SoyJavaSourceFunction {
    @Override
    public JavaValue applyForJavaSource(
        JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
      return factory.callStaticMethod(
          JavaValueFactory.createMethod(BytecodeCompilerTest.class, "acceptsInt", int.class),
          args.get(0));
    }
  }

  @Test
  public void testJavaSourceFunction_overflow() {
    long value = Integer.MAX_VALUE + 1L;
    assertThatTemplateBody("{overflow(" + value + ")}")
        .withSoySourceFunction(new Overflow())
        .failsToRenderWithExceptionThat()
        .hasMessageThat()
        .isEqualTo("Casting long to integer results in overflow: " + value);
  }

  private static int getTemplateLineNumber(String templateName, Throwable t) {
    for (StackTraceElement ste : t.getStackTrace()) {
      if (ste.getClassName().endsWith(templateName) && ste.getMethodName().equals("render")) {
        return ste.getLineNumber();
      }
    }
    throw new AssertionError("couldn't template: " + templateName + " in", t);
  }

  private static final class FakeRenamingMap implements SoyCssRenamingMap {
    private final ImmutableMap<String, String> renamingMap;

    FakeRenamingMap(Map<String, String> renamingMap) {
      this.renamingMap = ImmutableMap.copyOf(renamingMap);
    }

    @Nullable
    @Override
    public String get(String key) {
      return renamingMap.get(key);
    }
  }

  private CompiledTemplates compileFiles(String... soyFileContents) {
    SoyFileSetParser parser = SoyFileSetParserBuilder.forFileContents(soyFileContents).build();
    ParseResult parseResult = parser.parse();
    CompiledTemplates templates =
        BytecodeCompiler.compile(
                parseResult.registry(),
                parseResult.fileSet(),
                ErrorReporter.exploding(),
                parser.soyFileSuppliers(),
                parser.typeRegistry())
            .get();
    return templates;
  }

  @Test
  public void testRenderingWithMultipleCompilationSteps() {
    SoyFileSetParser parser1 =
        createParserForFileContents(
            ImmutableList.of(
                Joiner.on("\n")
                    .join(
                        "{namespace loader1}",
                        "{template .publicTemplate1}",
                        "L1T1",
                        "{sp}{call .privateTemplate_ /}",
                        "{sp}{call .publicTemplate2 /}",
                        "{/template}",
                        "",
                        "{template .privateTemplate_ visibility=\"private\"}",
                        "PVT",
                        "{/template}"),
                Joiner.on("\n")
                    .join(
                        "{namespace loader1}",
                        "{template .publicTemplate2}",
                        "L1T2",
                        "{/template}")));
    ParseResult parseResult1 = parser1.parse();
    CompilingClassLoader loader1 = createCompilingClassLoader(parser1, parseResult1);
    CompilationUnitAndKind dependency1 =
        CompilationUnitAndKind.create(
            SoyFileKind.DEP,
            "foo.soy",
            TemplateMetadataSerializer.compilationUnitFromFileSet(
                parseResult1.fileSet(), parseResult1.registry()));

    SoyFileSetParser parser1Recompiled =
        createParserForFileContents(
            ImmutableList.of(
                Joiner.on("\n")
                    .join(
                        "{namespace loader1}",
                        "{template .publicTemplate1}",
                        "L1T1 RECOMPILED",
                        "{/template}")));
    ParseResult parseResult1Recompiled = parser1Recompiled.parse();
    CompilingClassLoader loader1Recompiled =
        createCompilingClassLoader(parser1Recompiled, parseResult1Recompiled);

    SoyFileSetParser parser2 =
        createParserForFileContentsWithDependencies(
            ImmutableList.of(
                Joiner.on("\n")
                    .join(
                        "{namespace loader2}",
                        "{template .publicTemplate}",
                        "L2T",
                        "{sp}{call loader1.publicTemplate1 /}",
                        "{sp}{call loader1.publicTemplate1 /}",
                        "{/template}")),
            ImmutableList.of(dependency1));
    ParseResult parseResult2 = parser2.parse();
    CompilingClassLoader loader2 = createCompilingClassLoader(parser2, parseResult2);

    DelegatingClassLoader delegatingClassLoader1 = new DelegatingClassLoader(loader1, loader2);
    SoySauce sauce = new SoySauceBuilder().withClassLoader(delegatingClassLoader1).build();
    assertThat(sauce.renderTemplate("loader2.publicTemplate").renderHtml().get().toString())
        .isEqualTo("L2T L1T1 PVT L1T2 L1T1 PVT L1T2");

    assertThat(delegatingClassLoader1.loadedClasses()).containsNoDuplicates();
    assertThat(delegatingClassLoader1.loadedClasses().elementSet())
        .containsExactly(
            "com.google.template.soy.jbcsrc.gen.loader1.publicTemplate1",
            "com.google.template.soy.jbcsrc.gen.loader2.publicTemplate");

    DelegatingClassLoader delegatingClassLoader2 =
        new DelegatingClassLoader(loader1Recompiled, loader2);
    SoySauce sauceReloaded = new SoySauceBuilder().withClassLoader(delegatingClassLoader2).build();
    assertThat(sauceReloaded.renderTemplate("loader2.publicTemplate").renderHtml().get().toString())
        .isEqualTo("L2T L1T1 RECOMPILED L1T1 RECOMPILED");

    assertThat(delegatingClassLoader1.loadedClasses()).containsNoDuplicates();
    assertThat(delegatingClassLoader1.loadedClasses().elementSet())
        .containsExactly(
            "com.google.template.soy.jbcsrc.gen.loader1.publicTemplate1",
            "com.google.template.soy.jbcsrc.gen.loader2.publicTemplate");
  }

  @Test
  public void testRenderingWithMultipleCompilationStepsAndDynamicTemplateCalls() {
    SoyFileSetParser parser1 =
        createParserForFileContents(
            ImmutableList.of(
                Joiner.on("\n")
                    .join(
                        "{namespace loader1}",
                        "{template .publicTemplate1}",
                        "L1T1",
                        "{sp}{call .privateTemplate_ /}",
                        "{sp}{call .publicTemplate2 /}",
                        "{/template}",
                        "",
                        "{template .privateTemplate_ visibility=\"private\"}",
                        "PVT",
                        "{/template}"),
                Joiner.on("\n")
                    .join(
                        "{namespace loader1}",
                        "{template .publicTemplate2}",
                        "L1T2",
                        "{/template}")));
    ParseResult parseResult1 = parser1.parse();
    CompilingClassLoader loader1 = createCompilingClassLoader(parser1, parseResult1);
    CompilationUnitAndKind dependency1 =
        CompilationUnitAndKind.create(
            SoyFileKind.DEP,
            "foo.soy",
            TemplateMetadataSerializer.compilationUnitFromFileSet(
                parseResult1.fileSet(), parseResult1.registry()));

    SoyFileSetParser parser1Recompiled =
        createParserForFileContents(
            ImmutableList.of(
                Joiner.on("\n")
                    .join(
                        "{namespace loader1}",
                        "{template .publicTemplate1}",
                        "L1T1 RECOMPILED",
                        "{/template}")));
    ParseResult parseResult1Recompiled = parser1Recompiled.parse();
    CompilingClassLoader loader1Recompiled =
        createCompilingClassLoader(parser1Recompiled, parseResult1Recompiled);

    SoyFileSetParser parser2 =
        createParserForFileContentsWithDependencies(
            ImmutableList.of(
                Joiner.on("\n")
                    .join(
                        "{namespace loader2}",
                        "{template .publicTemplate}",
                        "{@param renderTemplate: bool = true}",
                        "{let $tpl: $renderTemplate ? template(loader1.publicTemplate1) :"
                            + " template(.dummyTemplate) /}",
                        "L2T",
                        "{sp}{call $tpl /}",
                        "{sp}{call $tpl /}",
                        "{/template}",
                        "{template .dummyTemplate visibility=\"private\"}dummy{/template}")),
            ImmutableList.of(dependency1));
    ParseResult parseResult2 = parser2.parse();
    CompilingClassLoader loader2 = createCompilingClassLoader(parser2, parseResult2);

    DelegatingClassLoader delegatingClassLoader1 = new DelegatingClassLoader(loader1, loader2);
    SoySauce sauce = new SoySauceBuilder().withClassLoader(delegatingClassLoader1).build();
    assertThat(sauce.renderTemplate("loader2.publicTemplate").renderHtml().get().toString())
        .isEqualTo("L2T L1T1 PVT L1T2 L1T1 PVT L1T2");

    assertThat(delegatingClassLoader1.loadedClasses()).containsNoDuplicates();
    assertThat(delegatingClassLoader1.loadedClasses().elementSet())
        .containsExactly(
            "com.google.template.soy.jbcsrc.gen.loader1.publicTemplate1",
            "com.google.template.soy.jbcsrc.gen.loader2.publicTemplate");

    DelegatingClassLoader delegatingClassLoader2 =
        new DelegatingClassLoader(loader1Recompiled, loader2);
    SoySauce sauceReloaded = new SoySauceBuilder().withClassLoader(delegatingClassLoader2).build();
    assertThat(sauceReloaded.renderTemplate("loader2.publicTemplate").renderHtml().get().toString())
        .isEqualTo("L2T L1T1 RECOMPILED L1T1 RECOMPILED");

    assertThat(delegatingClassLoader1.loadedClasses()).containsNoDuplicates();
    assertThat(delegatingClassLoader1.loadedClasses().elementSet())
        .containsExactly(
            "com.google.template.soy.jbcsrc.gen.loader1.publicTemplate1",
            "com.google.template.soy.jbcsrc.gen.loader2.publicTemplate");
  }

  private static class DelegatingClassLoader extends ClassLoader {
    private final CompilingClassLoader loader1;
    private final CompilingClassLoader loader2;
    private final HashMultiset<String> loadedClassesTracker;

    DelegatingClassLoader(CompilingClassLoader loader1, CompilingClassLoader loader2) {
      this.loader1 = loader1;
      this.loader2 = loader2;
      this.loadedClassesTracker = HashMultiset.create();
    }

    @Override
    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      Class<?> clazz;
      if (name.startsWith("com.google.template.soy.jbcsrc.gen.loader1.")) {
        clazz = loader1.loadClass(name, resolve);
      } else if (name.startsWith("com.google.template.soy.jbcsrc.gen.loader2.")) {
        clazz = loader2.loadClass(name, resolve);
      } else {
        throw new ClassNotFoundException("Unexpected class to be loaded: " + name);
      }

      loadedClassesTracker.add(name);
      return clazz;
    }

    HashMultiset<String> loadedClasses() {
      return loadedClassesTracker;
    }
  }

  private static SoyFileSetParser createParserForFileContents(Iterable<String> soyFileContents) {
    return createParserForFileContentsWithDependencies(soyFileContents, ImmutableList.of());
  }

  private static SoyFileSetParser createParserForFileContentsWithDependencies(
      Iterable<String> soyFileContents, Iterable<CompilationUnitAndKind> dependencies) {
    return SoyFileSetParserBuilder.forFileContents(Iterables.toArray(soyFileContents, String.class))
        .addCompilationUnits(dependencies)
        .options(new SoyGeneralOptions().setAllowExternalCalls(false))
        .build();
  }

  private static CompilingClassLoader createCompilingClassLoader(
      SoyFileSetParser parser, ParseResult parseResult) {
    return new CompilingClassLoader(
        new CompiledTemplateRegistry(parseResult.registry()),
        parseResult.fileSet(),
        parser.soyFileSuppliers(),
        parser.typeRegistry());
  }
}
