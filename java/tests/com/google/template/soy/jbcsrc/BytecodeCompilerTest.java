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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.template.soy.jbcsrc.TemplateTester.asParams;
import static com.google.template.soy.jbcsrc.TemplateTester.assertThatElementBody;
import static com.google.template.soy.jbcsrc.TemplateTester.assertThatFile;
import static com.google.template.soy.jbcsrc.TemplateTester.assertThatTemplateBody;
import static com.google.template.soy.jbcsrc.TemplateTester.getDefaultContext;
import static com.google.template.soy.jbcsrc.TemplateTester.getDefaultContextWithDebugInfo;
import static org.junit.Assert.fail;

import com.google.common.base.Joiner;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.template.soy.SoyFileSetParser;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.css.CssRegistry;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.LoggingAdvisingAppendable.BufferingAppendable;
import com.google.template.soy.data.RecordProperty;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SanitizedContents;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.SoyValueConverterUtility;
import com.google.template.soy.data.internal.ListImpl;
import com.google.template.soy.data.internal.ParamStore;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.jbcsrc.TemplateTester.CompiledTemplateSubject;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.jbcsrc.api.SoySauce;
import com.google.template.soy.jbcsrc.api.SoySauceBuilder;
import com.google.template.soy.jbcsrc.shared.CompiledTemplate;
import com.google.template.soy.jbcsrc.shared.CompiledTemplates;
import com.google.template.soy.jbcsrc.shared.Names;
import com.google.template.soy.jbcsrc.shared.RenderContext;
import com.google.template.soy.jbcsrc.shared.TemplateMetadata;
import com.google.template.soy.plugin.java.restricted.JavaPluginContext;
import com.google.template.soy.plugin.java.restricted.JavaValue;
import com.google.template.soy.plugin.java.restricted.JavaValueFactory;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.soytree.Metadata.CompilationUnitAndKind;
import com.google.template.soy.soytree.TemplateMetadataSerializer;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
  public void testDelCall_modSelections() throws IOException {
    SoyFileSupplier soyFileContent1 =
        SoyFileSupplier.Factory.create(
            Joiner.on("\n")
                .join(
                    "{namespace ns1}",
                    "",
                    "/***/",
                    "{template callerTemplate}",
                    "  {call myDelegate}",
                    "    {param boo: 'aaaaaah' /}",
                    "  {/call}",
                    "{/template}",
                    "",
                    "/** */",
                    "{template myDelegate requirecss=\"ns.default\" modifiable='true'}",
                    // default implementation
                    // (doesn't use $boo)
                    "  {@param boo : string}",
                    "  default",
                    "{/template}",
                    ""),
            SourceFilePath.create("ns1.soy"));

    SoyFileSupplier soyFileContent2 =
        SoyFileSupplier.Factory.create(
            Joiner.on("\n")
                .join(
                    "{modname SecretFeature}",
                    "{namespace ns2 requirecss=\"ns.foo\"}",
                    "import {myDelegate} from 'ns1.soy';",
                    "",
                    "/** */",
                    // implementation in SecretFeature
                    "{template myDelegateMod visibility='private' modifies='myDelegate'}",
                    "  {@param boo : string}",
                    "  SecretFeature {$boo}",
                    "{/template}",
                    ""),
            SourceFilePath.create("ns2-dp.soy"));

    SoyFileSupplier soyFileContent3 =
        SoyFileSupplier.Factory.create(
            Joiner.on("\n")
                .join(
                    "{modname AlternateSecretFeature}",
                    "{namespace ns3 requirecss=\"ns.bar\"}",
                    "import {helper} from 'ns4.soy';",
                    "import {myDelegate} from 'ns1.soy';",
                    "",
                    "/** */",
                    // implementation in AlternateSecretFeature
                    "{template myDelegateMod  visibility='private' modifies='myDelegate'}",
                    "  {@param boo : string}",
                    "  AlternateSecretFeature {call helper data=\"all\" /}",
                    "{/template}",
                    ""),
            SourceFilePath.create("ns3-dp.soy"));

    SoyFileSupplier soyFileContent4 =
        SoyFileSupplier.Factory.create(
            Joiner.on("\n")
                .join(
                    "{namespace ns4}",
                    "",
                    "/** */",
                    "{template helper}",
                    "  {@param boo : string}",
                    "  {$boo}",
                    "{/template}",
                    ""),
            SourceFilePath.create("ns4.soy"));
    SoyFileSetParser parser =
        SoyFileSetParserBuilder.forSuppliers(
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
    CompiledTemplate template = templates.getTemplate("ns1.callerTemplate");
    Predicate<String> activePackages = arg -> false;
    assertThat(templates.getAllRequiredCssNamespaces("ns1.callerTemplate", activePackages, false))
        .containsExactly("ns.default");
    assertThat(
            templates.getAllRequiredCssNamespaces(
                "ns1.callerTemplate", arg -> arg.equals("SecretFeature"), false))
        .containsExactly("ns.foo", "ns.default");
    assertThat(
            templates.getAllRequiredCssNamespaces(
                "ns1.callerTemplate", arg -> arg.equals("AlternateSecretFeature"), false))
        .containsExactly("ns.bar", "ns.default");

    assertThat(renderWithContext(template, getDefaultContext(templates, activePackages)))
        .isEqualTo("default");

    activePackages = "SecretFeature"::equals;
    assertThat(renderWithContext(template, getDefaultContext(templates, activePackages)))
        .isEqualTo("SecretFeature aaaaaah");

    activePackages = "AlternateSecretFeature"::equals;
    assertThat(renderWithContext(template, getDefaultContext(templates, activePackages)))
        .isEqualTo("AlternateSecretFeature aaaaaah");

    activePackages = "NonexistentFeature"::equals;
    assertThat(renderWithContext(template, getDefaultContext(templates, activePackages)))
        .isEqualTo("default");
  }

  @Test
  public void testDebugSoyTemplateInfo() throws IOException {
    String soyFileContent =
        Joiner.on("\n")
            .join(
                "{namespace ns}",
                "",
                "{template html}",
                "  <div>foo</div>",
                "{/template}",
                "",
                "{template text kind=\"text\"}",
                "  foo",
                "{/template}",
                "",
                "{template htmlNoTag}",
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
    CompiledTemplate template = templates.getTemplate("ns.html");
    assertThat(renderWithContext(template, getDefaultContext(templates)))
        .isEqualTo("<div>foo</div>");
    // If debugSoyTemplateInfo is enabled, we should render additional HTML comments.
    assertThat(renderWithContext(template, getDefaultContextWithDebugInfo(templates)))
        .isEqualTo("<div data-debug-soy=\"ns.html no-path:4\">foo</div>");

    // We should never render these comments for templates with kind="text".
    template = templates.getTemplate("ns.text");
    assertThat(renderWithContext(template, getDefaultContext(templates))).isEqualTo("foo");
    assertThat(renderWithContext(template, getDefaultContextWithDebugInfo(templates)))
        .isEqualTo("foo");

    template = templates.getTemplate("ns.htmlNoTag");
    assertThat(renderWithContext(template, getDefaultContext(templates))).isEqualTo("foo");
    assertThat(renderWithContext(template, getDefaultContextWithDebugInfo(templates)))
        .isEqualTo("foo");
  }

  private static String renderWithContext(CompiledTemplate template, RenderContext context)
      throws IOException {
    BufferingAppendable builder = LoggingAdvisingAppendable.buffering();
    assertThat(
            template.render(ParamStore.EMPTY_INSTANCE, ParamStore.EMPTY_INSTANCE, builder, context))
        .isEqualTo(RenderResult.done());
    return builder.toString();
  }

  @Test
  public void testCallBasicNode() throws IOException {
    CompiledTemplates templates =
        TemplateTester.compileFileWithCss(
            CssRegistry.create(ImmutableSet.of("ns.foo", "ns.bar"), ImmutableMap.of()),
            "{namespace ns requirecss=\"ns.foo\"}",
            "",
            "{template callerDataAll requirecss=\"ns.bar\"}",
            "  {@param foo : string}",
            "  {call callee data=\"all\" /}",
            "{/template}",
            "",
            "{template callerDataExpr}",
            "  {@param rec : [foo : string]}",
            "  {call callee data=\"$rec\" /}",
            "{/template}",
            "",
            "{template callerParams}",
            "  {@param p1 : string}",
            "  {call callee}",
            "    {param foo : $p1 /}",
            "    {param boo : 'a' + 1 + 'b' /}",
            "  {/call}",
            "{/template}",
            "",
            "{template callerParamsAndData}",
            "  {@param p1 : string}",
            "  {call callee data=\"all\"}",
            "    {param foo : $p1 /}",
            "  {/call}",
            "{/template}",
            "",
            "{template callee}",
            "  {@param foo : string}",
            "  {@param? boo : string|null}",
            "Foo: {$foo}{\\n}",
            "Boo: {$boo}{\\n}",
            "{/template}",
            "");
    ParamStore params =
        new ParamStore(2).setField(RecordProperty.get("foo"), StringData.forValue("foo"));
    assertThat(render(templates, params, "ns.callerDataAll"))
        .isEqualTo("Foo: foo\nBoo: undefined\n");
    params.setField(RecordProperty.get("boo"), StringData.forValue("boo"));
    assertThat(render(templates, params, "ns.callerDataAll")).isEqualTo("Foo: foo\nBoo: boo\n");

    assertThat(getTemplateMetadata(templates, "ns.callerDataAll").callees())
        .asList()
        .containsExactly("ns.callee");

    params =
        new ParamStore(2)
            .setField(
                RecordProperty.get("rec"),
                SoyValueConverter.INSTANCE.convert(ImmutableMap.of("foo", "foo")));
    assertThat(render(templates, params, "ns.callerDataExpr"))
        .isEqualTo("Foo: foo\nBoo: undefined\n");
    params.setField(
        RecordProperty.get("rec"),
        SoyValueConverter.INSTANCE.convert(ImmutableMap.of("foo", "foo", "boo", "boo")));
    assertThat(render(templates, params, "ns.callerDataExpr")).isEqualTo("Foo: foo\nBoo: boo\n");
    assertThat(getTemplateMetadata(templates, "ns.callerDataExpr").callees())
        .asList()
        .containsExactly("ns.callee");

    params = new ParamStore(2);
    params.setField(RecordProperty.get("p1"), StringData.forValue("foo"));
    assertThat(render(templates, params, "ns.callerParams")).isEqualTo("Foo: foo\nBoo: a1b\n");
    assertThat(getTemplateMetadata(templates, "ns.callerParams").callees())
        .asList()
        .containsExactly("ns.callee");

    params =
        new ParamStore(2)
            .setField(RecordProperty.get("p1"), StringData.forValue("foo"))
            .setField(RecordProperty.get("boo"), StringData.forValue("boo"));
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
            "{template requireCss requirecss=\"ns.bar\"}",
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
            "{template msg}",
            "  {msg desc='description'}",
            "    {call t data='all' /}",
            "  {/msg}",
            "{/template}",
            "",
            "{template t}",
            "  foobar",
            "{/template}");

    assertThat(render(templates, ParamStore.EMPTY_INSTANCE, "ns.msg")).isEqualTo("foobar");
  }

  // Regression test for a bug where we would generate extra detach states in message placeholders
  // because a node copy caused our TemplateAnalysis queries to fail.
  @Test
  public void testMsgPlaceholdersUsesTemplateAnalysisOnPlaceholders() throws Exception {
    CompiledTemplates templates =
        TemplateTester.compileFile(
            "{namespace ns}",
            "",
            "{template msg kind='text'}",
            "  {@param name:string}",
            "  {msg desc='...'}",
            "    <a href='/'>Hello {$name + '' phname='FOO'}</a>",
            "  {/msg}",
            "{/template}");
    Class<?> templateClass = templates.getTemplateData("ns.msg").templateClass();
    Class<?> innerClass =
        Iterables.getOnlyElement(Arrays.asList(templateClass.getDeclaredClasses()));
    assertThat(innerClass.getSimpleName()).isEqualTo("ph_FOO");
    assertThat(innerClass.getDeclaredFields()).hasLength(2);
    templates =
        TemplateTester.compileFile(
            "{namespace ns}",
            "",
            "{template msg  kind='text'}",
            "  {@param name:string}",
            "  {if $name}",
            "    {msg desc='...'}",
            "      <a href='/'>Hello {$name + '' phname='FOO'}</a>",
            "    {/msg}",
            "  {/if}",
            "{/template}");
    templateClass = templates.getTemplateData("ns.msg").templateClass();
    // The placeholder doesn't require an inner class  because `$name` is definitely already
    // resolved so it is evaluated inline
    assertThat(templateClass.getDeclaredClasses()).isEmpty();
  }

  private static TemplateMetadata getTemplateMetadata(CompiledTemplates templates, String name) {
    return templates.getTemplateData(name).templateMethod().getAnnotation(TemplateMetadata.class);
  }

  private String render(CompiledTemplates templates, ParamStore params, String name)
      throws IOException {
    CompiledTemplate caller = templates.getTemplate(name);
    BufferingAppendable builder = LoggingAdvisingAppendable.buffering();
    assertThat(
            caller.render(params, ParamStore.EMPTY_INSTANCE, builder, getDefaultContext(templates)))
        .isEqualTo(RenderResult.done());
    return builder.toString();
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

    assertThatTemplateBody("{@param list: list<int>}", "{for $i in $list}", "  {$i}", "{/for}")
        .rendersAs("", ImmutableMap.of("list", EMPTY_LIST));

    assertThatTemplateBody("{for $i in [1,2,3,4,5]}", "  {$i}", "{/for}").rendersAs("12345");
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
            "{for $key in $map.keys()}",
            "  {$key} - {$map.get($key)}{\\n}",
            "{/for}")
        .rendersAs("a - 1\nb - 2\n", ImmutableMap.of("map", ImmutableMap.of("a", 1, "b", 2)));
  }

  @Test
  public void testForNode_nullableList() {
    // The compiler should be rejected this :(
    assertThatTemplateBody(
            "{@param map : map<string, list<int>>}",
            "{for $item in $map?.get('key')!}",
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
            "  {case 2}",
            "    dead",
            "  {default}",
            "    default",
            "{/switch}")
        .rendersAs("two");
    assertThatTemplateBody(
            "{switch 'asdf'}",
            "  {case '1'}",
            "    one",
            "  {case '2'}",
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
            "  {case '2'}",
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
            "{@param? cond1 : bool|null}",
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
    String largeString = "x".repeat(1 << 17);
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
          public ImmutableSet<Integer> getValidArgsSizes() {
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
    assertThatTemplateBody("{@param foo : [a : [ b : string]] }", "{$foo.a != null}")
        .rendersAs(
            "false", ImmutableMap.<String, Object>of("foo", ImmutableMap.<String, String>of()));
  }

  // Tests for a bug in an integration test where unnecessary float unboxing conversions happened.
  @Test
  public void testBoxedIntComparisonFromFunctions() {
    assertThatTemplateBody(
            "{@param list : list<int>}",
            "{for $item, $idx in $list}",
            "{if $idx == ceiling(length($list) / 2) - 1}",
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
            "{@param? list : list<int>|null}",
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
            "{template foo}",
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

    assertThatTemplateBody("{@inject foo: ?}", "{let $bar: $foo ?? 'abc' /}", "{$bar}")
        .rendersAs("abc", ImmutableMap.of(), ImmutableMap.of())
        .rendersAs("abc", ImmutableMap.of(), ImmutableMap.of("foo", NullData.INSTANCE))
        .rendersAs("abc", ImmutableMap.of(), ImmutableMap.of("foo", UndefinedData.INSTANCE))
        .rendersAs("xyz", ImmutableMap.of(), ImmutableMap.of("foo", "xyz"));
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
        TemplateTester.compileTemplateBody("{@param foo : int}", "{$foo ?? -1}");
    CompiledTemplate singleParam = templates.getTemplate("ns.foo");
    RenderContext context = getDefaultContext(templates);
    BufferingAppendable builder = LoggingAdvisingAppendable.buffering();

    ParamStore params = SoyValueConverterUtility.newParams("foo", IntegerData.forValue(1));
    assertThat(singleParam.render(params, ParamStore.EMPTY_INSTANCE, builder, context))
        .isEqualTo(RenderResult.done());
    assertThat(builder.getAndClearBuffer()).isEqualTo("1");

    assertThat(
            singleParam.render(
                ParamStore.EMPTY_INSTANCE, ParamStore.EMPTY_INSTANCE, builder, context))
        .isEqualTo(RenderResult.done());
    assertThat(builder.getAndClearBuffer()).isEqualTo("-1");

    templates = TemplateTester.compileTemplateBody("{@inject foo : int}", "{$foo}");
    CompiledTemplate singleIj = templates.getTemplate("ns.foo");
    context = getDefaultContext(templates);

    params = SoyValueConverterUtility.newParams("foo", IntegerData.forValue(1));
    assertThat(singleIj.render(ParamStore.EMPTY_INSTANCE, params, builder, context))
        .isEqualTo(RenderResult.done());
    assertThat(builder.getAndClearBuffer()).isEqualTo("1");

    assertThat(
            singleIj.render(ParamStore.EMPTY_INSTANCE, ParamStore.EMPTY_INSTANCE, builder, context))
        .isEqualTo(RenderResult.done());
    assertThat(builder.getAndClearBuffer()).isEqualTo("undefined");
  }

  @Test
  public void testPassHtmlAsNullableString() throws Exception {
    CompiledTemplateSubject subject =
        TemplateTester.assertThatFile(
            "{namespace ns}",
            "{template foo}",
            "  {@param? content : string|null}",
            "  {checkNotNull($content)}",
            "{/template}");
    subject.rendersAs("full", ImmutableMap.of("content", "full"));
    subject.failsToRenderWith(NullPointerException.class);
  }

  @Test
  public void testPassHtmlAsNullableString_printNodeDoesntFail() throws Exception {
    CompiledTemplateSubject subject =
        TemplateTester.assertThatFile(
            "{namespace ns}",
            "{template foo}",
            "  {@param? content : string|null}",
            "  {$content ?? 'empty'}",
            "{/template}");
    subject.rendersAs("empty");
    subject.rendersAs("full", ImmutableMap.of("content", "full"));
    // NOTE: we don't fail with a ClassCastException here, this is because we end up calling
    // `renderandResolve` on the parameter as a SoyValueProvider
    subject.rendersAs(
        "<b>hello</b>", ImmutableMap.of("content", SanitizedContents.constantHtml("<b>hello</b>")));
  }

  @Test
  public void testPassHtmlAsString_printNodeDoesntFail() throws Exception {
    CompiledTemplateSubject subject =
        TemplateTester.assertThatFile(
            "{namespace ns}",
            "{template foo}",
            "  {@param content : string}",
            "  {$content}",
            "{/template}");
    subject.rendersAs("full", ImmutableMap.of("content", "full"));
    // NOTE: we don't fail with a ClassCastException here, this is because we end up calling
    // `renderandResolve` on the parameter as a SoyValueProvider
    subject.rendersAs(
        "<b>hello</b>", ImmutableMap.of("content", SanitizedContents.constantHtml("<b>hello</b>")));
  }

  @Test
  public void testBasicFunctionality() {
    // make sure we don't break standard reflection access
    Method templateMethod =
        TemplateTester.compileTemplateBody("hello world")
            .getTemplateData("ns.foo")
            .templateMethod();

    assertThat(templateMethod.toString())
        .isEqualTo(
            "public static com.google.template.soy.jbcsrc.shared.CompiledTemplate"
                + " com.google.template.soy.jbcsrc.gen.ns.foo()");
    assertThat(templateMethod.getName()).isEqualTo("foo");
    assertThat(templateMethod.getDeclaringClass().getSimpleName()).isEqualTo("ns");

    TemplateMetadata templateMetadata = templateMethod.getAnnotation(TemplateMetadata.class);
    assertThat(templateMetadata.contentKind()).isEqualTo(ContentKind.HTML);
    assertThat(templateMetadata.injectedParams()).isEmpty();
    assertThat(templateMetadata.callees()).isEmpty();
    assertThat(templateMetadata.delCallees()).isEmpty();
  }

  @Test
  public void factoryReturnsSameInstanceEachTime() throws Exception {
    // CompiledTemplates returns the same factory each time
    CompiledTemplates templates = TemplateTester.compileTemplateBody("hello world");
    CompiledTemplates.TemplateData templateData = templates.getTemplateData("ns.foo");
    CompiledTemplate template = templateData.template();
    assertThat(template).isSameInstanceAs(templates.getTemplate("ns.foo"));

    Method templateMethod = templateData.templateMethod();
    assertThat(template).isSameInstanceAs(templateMethod.invoke(null));
    assertThat(template).isSameInstanceAs(templateMethod.invoke(null));
  }

  @Test
  public void testBasicFunctionality_privateTemplate() {
    // make sure you can't access factories for priate templates
    CompiledTemplates templates =
        TemplateTester.compileFile(
            "{namespace ns}{template foo visibility=\"private\"}hello world{/template}");
    try {
      templates.getTemplate("ns.foo");
      fail();
    } catch (IllegalArgumentException expected) {
    }
    // we can still access metadata
    assertThat(templates.getTemplateData("ns.foo").kind()).isEqualTo(ContentKind.HTML);
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
            "{msg meaning=\"noun\" desc=\"\"}Archive{/msg}",
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

  @Test
  public void testExpressionLineNumbers() throws Exception {
    CompiledTemplates templates =
        TemplateTester.compileFile(
            Joiner.on("\n")
                .join(
                    "{namespace ns}",
                    "",
                    "{template foo}",
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
                templates, asParams(ImmutableMap.of("p1", 1, "p2", 2, "p3", 3, "p4", 4)), "ns.foo"))
        .isEqualTo("10");
    ListenableFuture<?> failed = immediateFailedFuture(new RuntimeException("boom"));
    // since each parameter is on a different source line, depending on which one is assigned the
    // failed future, the template should show a different line number
    try {
      render(
          templates, asParams(ImmutableMap.of("p1", failed, "p2", 2, "p3", 3, "p4", 4)), "ns.foo");
      fail();
    } catch (Exception e) {
      assertThat(getTemplateLineNumber("ns.foo", e)).isEqualTo(8);
    }
    try {
      render(
          templates, asParams(ImmutableMap.of("p1", 1, "p2", failed, "p3", 3, "p4", 4)), "ns.foo");
      fail();
    } catch (Exception e) {
      assertThat(getTemplateLineNumber("ns.foo", e)).isEqualTo(9);
    }
    try {
      render(
          templates, asParams(ImmutableMap.of("p1", 1, "p2", 2, "p3", failed, "p4", 4)), "ns.foo");
      fail();
    } catch (Exception e) {
      assertThat(getTemplateLineNumber("ns.foo", e)).isEqualTo(10);
    }
    try {
      render(
          templates, asParams(ImmutableMap.of("p1", 1, "p2", 2, "p3", 3, "p4", failed)), "ns.foo");
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
                    "{template foo}",
                    "  {@param list : ?}",
                    "  {@param? opt : ?}",
                    "{if not $opt}",
                    // failures on the foreach loop used to get assigned the line number of the
                    // if statement.
                    "  {for $foo in $list}",
                    "    {$foo}{sp}",
                    "  {/for}",
                    "{/if}",
                    "{/template}"));
    assertThat(
            render(templates, asParams(ImmutableMap.of("list", ImmutableList.of(1, 2))), "ns.foo"))
        .isEqualTo("1 2 ");
    ListenableFuture<?> failed = immediateFailedFuture(new RuntimeException("boom"));
    // since each parameter is on a different source line, depending on which one is assigned the
    // failed future, the template should show a different line number
    try {
      render(templates, asParams(ImmutableMap.of("list", failed)), "ns.foo");
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
            "{($b ? map('a': 1) : map('a': '2')).get('a')}",
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
        .isEqualTo("Out of range: " + value);
  }

  private static int getTemplateLineNumber(String templateName, Throwable t) {
    String className = Names.javaClassNameFromSoyTemplateName(templateName);
    String methodName = Names.renderMethodNameFromSoyTemplateName(templateName);
    for (StackTraceElement ste : t.getStackTrace()) {
      if (className.equals(ste.getClassName()) && methodName.equals(ste.getMethodName())) {
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

  @Test
  public void testRenderingWithMultipleCompilationSteps() {
    SoyFileSetParser parser1 =
        createParserForFileContents(
            ImmutableMap.of(
                "loader1.soy",
                Joiner.on("\n")
                    .join(
                        "{namespace loader1.a}",
                        "import {publicTemplate2} from 'loader2.soy';",
                        "{template publicTemplate1}",
                        "L1T1",
                        "{sp}{call privateTemplate_ /}",
                        "{sp}{call publicTemplate2 /}",
                        "{/template}",
                        "",
                        "{template privateTemplate_ visibility=\"private\"}",
                        "PVT",
                        "{/template}"),
                "loader2.soy",
                Joiner.on("\n")
                    .join(
                        "{namespace loader1.b}",
                        "{template publicTemplate2}",
                        "L1T2",
                        "{/template}")));
    ParseResult parseResult1 = parser1.parse();
    CompilingClassLoader loader1 = createCompilingClassLoader(parser1, parseResult1);
    CompilationUnitAndKind dependency1 =
        CompilationUnitAndKind.create(
            SoyFileKind.DEP,
            TemplateMetadataSerializer.compilationUnitFromFileSet(
                parseResult1.fileSet(), parseResult1.registry()));

    SoyFileSetParser parser1Recompiled =
        createParserForFileContents(
            ImmutableMap.of(
                "loader1.soy",
                Joiner.on("\n")
                    .join(
                        "{namespace loader1.a}",
                        "{template publicTemplate1}",
                        "L1T1 RECOMPILED",
                        "{/template}")));
    ParseResult parseResult1Recompiled = parser1Recompiled.parse();
    CompilingClassLoader loader1Recompiled =
        createCompilingClassLoader(parser1Recompiled, parseResult1Recompiled);

    SoyFileSetParser parser2 =
        createParserForFileContentsWithDependencies(
            ImmutableMap.of(
                "loader2.soy",
                Joiner.on("\n")
                    .join(
                        "{namespace loader2}",
                        "import {publicTemplate1} from 'loader1.soy';",
                        "{template publicTemplate}",
                        "L2T",
                        "{sp}{call publicTemplate1 /}",
                        "{sp}{call publicTemplate1 /}",
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
            "com.google.template.soy.jbcsrc.gen.loader1.a",
            "com.google.template.soy.jbcsrc.gen.loader2");

    DelegatingClassLoader delegatingClassLoader2 =
        new DelegatingClassLoader(loader1Recompiled, loader2);
    SoySauce sauceReloaded = new SoySauceBuilder().withClassLoader(delegatingClassLoader2).build();
    assertThat(sauceReloaded.renderTemplate("loader2.publicTemplate").renderHtml().get().toString())
        .isEqualTo("L2T L1T1 RECOMPILED L1T1 RECOMPILED");

    assertThat(delegatingClassLoader1.loadedClasses()).containsNoDuplicates();
    assertThat(delegatingClassLoader1.loadedClasses().elementSet())
        .containsExactly(
            "com.google.template.soy.jbcsrc.gen.loader1.a",
            "com.google.template.soy.jbcsrc.gen.loader2");
  }

  public static String extern1() {
    return "EXTERN1";
  }

  public static String extern2() {
    return "EXTERN2";
  }

  @Test
  public void testRenderingWithMultipleCompilationStepsAndDynamicTemplateCalls() {
    SoyFileSetParser parser1 =
        createParserForFileContents(
            ImmutableMap.of(
                "loader1.soy",
                Joiner.on("\n")
                    .join(
                        "{namespace loader1}",
                        "import {publicTemplate2} from 'loader2.soy';",
                        "{export const CONST = 'CONST' /}",
                        "{export extern extern: ()=>string}",
                        "  {javaimpl class=\"" + BytecodeCompilerTest.class.getName() + "\"",
                        "   method=\"extern1\" params=\"\" return=\"java.lang.String\" /}",
                        "{/extern}",
                        "{template publicTemplate1}",
                        "L1T1",
                        "{sp}{call privateTemplate_ /}",
                        "{sp}{call publicTemplate2 /}",
                        "{/template}",
                        "",
                        "{template privateTemplate_ visibility=\"private\"}",
                        "PVT",
                        "{/template}"),
                "loader2.soy",
                Joiner.on("\n")
                    .join(
                        "{namespace loader2}",
                        "{template publicTemplate2}",
                        "L1T2",
                        "{/template}")));
    ParseResult parseResult1 = parser1.parse();
    CompilingClassLoader loader1 = createCompilingClassLoader(parser1, parseResult1);
    CompilationUnitAndKind dependency1 =
        CompilationUnitAndKind.create(
            SoyFileKind.DEP,
            TemplateMetadataSerializer.compilationUnitFromFileSet(
                parseResult1.fileSet(), parseResult1.registry()));

    SoyFileSetParser parser1Recompiled =
        createParserForFileContents(
            ImmutableMap.of(
                "loader1.soy",
                Joiner.on("\n")
                    .join(
                        "{namespace loader1}",
                        "{export const CONST = 'CONST RECOMPILED' /}",
                        "{export extern extern: ()=>string}",
                        "  {javaimpl class=\"" + BytecodeCompilerTest.class.getName() + "\"",
                        "   method=\"extern2\" params=\"\" return=\"java.lang.String\" /}",
                        "{/extern}",
                        "{template publicTemplate1}",
                        "L1T1 RECOMPILED",
                        "{/template}")));
    ParseResult parseResult1Recompiled = parser1Recompiled.parse();
    CompilingClassLoader loader1Recompiled =
        createCompilingClassLoader(parser1Recompiled, parseResult1Recompiled);

    SoyFileSetParser parser2 =
        createParserForFileContentsWithDependencies(
            ImmutableMap.of(
                "loader2.soy",
                Joiner.on("\n")
                    .join(
                        "{namespace loader2}",
                        "import {publicTemplate1, CONST, extern} from 'loader1.soy';",
                        "{template publicTemplate}",
                        "{@param renderTemplate: bool = true}",
                        "{let $tpl: $renderTemplate ? publicTemplate1 : dummyTemplate /}",
                        "L2T",
                        "{sp}{call $tpl /}",
                        "{sp}{call $tpl /}",
                        "{sp}{CONST}",
                        "{sp}{extern()}",
                        "{/template}",
                        "{template dummyTemplate visibility=\"private\"}dummy{/template}")),
            ImmutableList.of(dependency1));
    ParseResult parseResult2 = parser2.parse();
    CompilingClassLoader loader2 = createCompilingClassLoader(parser2, parseResult2);

    DelegatingClassLoader delegatingClassLoader1 = new DelegatingClassLoader(loader1, loader2);
    SoySauce sauce = new SoySauceBuilder().withClassLoader(delegatingClassLoader1).build();
    assertThat(sauce.renderTemplate("loader2.publicTemplate").renderHtml().get().toString())
        .isEqualTo("L2T L1T1 PVT L1T2 L1T1 PVT L1T2 CONST EXTERN1");

    assertThat(delegatingClassLoader1.loadedClasses()).containsNoDuplicates();
    assertThat(delegatingClassLoader1.loadedClasses().elementSet())
        .containsExactly(
            "com.google.template.soy.jbcsrc.gen.loader1",
            "com.google.template.soy.jbcsrc.gen.loader2");

    DelegatingClassLoader delegatingClassLoader2 =
        new DelegatingClassLoader(loader1Recompiled, loader2);
    SoySauce sauceReloaded = new SoySauceBuilder().withClassLoader(delegatingClassLoader2).build();
    assertThat(sauceReloaded.renderTemplate("loader2.publicTemplate").renderHtml().get().toString())
        .isEqualTo("L2T L1T1 RECOMPILED L1T1 RECOMPILED CONST RECOMPILED EXTERN2");

    assertThat(delegatingClassLoader2.loadedClasses()).containsNoDuplicates();
    assertThat(delegatingClassLoader2.loadedClasses().elementSet())
        .containsExactly(
            "com.google.template.soy.jbcsrc.gen.loader1",
            "com.google.template.soy.jbcsrc.gen.loader2");
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
      if (name.startsWith("com.google.template.soy.jbcsrc.gen.loader1")) {
        clazz = loader1.loadClass(name, resolve);
      } else if (name.startsWith("com.google.template.soy.jbcsrc.gen.loader2")) {
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

  private static SoyFileSetParser createParserForFileContents(Map<String, String> soyFileContents) {
    return createParserForFileContentsWithDependencies(soyFileContents, ImmutableList.of());
  }

  private static SoyFileSetParser createParserForFileContentsWithDependencies(
      Map<String, String> soyFileContents, Iterable<CompilationUnitAndKind> dependencies) {
    ImmutableList<SoyFileSupplier> files =
        soyFileContents.entrySet().stream()
            .map(
                e ->
                    SoyFileSupplier.Factory.create(e.getValue(), SourceFilePath.create(e.getKey())))
            .collect(toImmutableList());
    return SoyFileSetParserBuilder.forSuppliers(files).addCompilationUnits(dependencies).build();
  }

  private static CompilingClassLoader createCompilingClassLoader(
      SoyFileSetParser parser, ParseResult parseResult) {
    return new CompilingClassLoader(
        parseResult.fileSet(),
        parser.soyFileSuppliers(),
        parser.typeRegistry(),
        parseResult.registry());
  }
}
