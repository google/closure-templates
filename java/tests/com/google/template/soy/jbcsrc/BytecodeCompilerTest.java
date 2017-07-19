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
import static com.google.template.soy.data.SoyValueConverter.EMPTY_DICT;
import static com.google.template.soy.data.SoyValueConverter.EMPTY_LIST;
import static com.google.template.soy.jbcsrc.TemplateTester.asRecord;
import static com.google.template.soy.jbcsrc.TemplateTester.assertThatFile;
import static com.google.template.soy.jbcsrc.TemplateTester.assertThatTemplateBody;
import static com.google.template.soy.jbcsrc.TemplateTester.getDefaultContext;
import static com.google.template.soy.jbcsrc.TemplateTester.getDefaultContextWithDebugInfo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SanitizedContents;
import com.google.template.soy.data.SoyDict;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.internal.BasicParamStore;
import com.google.template.soy.data.internal.ParamStore;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.jbcsrc.TemplateTester.CompiledTemplateSubject;
import com.google.template.soy.jbcsrc.api.AdvisingStringBuilder;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.jbcsrc.shared.CompiledTemplate;
import com.google.template.soy.jbcsrc.shared.CompiledTemplates;
import com.google.template.soy.jbcsrc.shared.RenderContext;
import com.google.template.soy.jbcsrc.shared.TemplateMetadata;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateRegistry;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** A test for the template compiler, notably {@link BytecodeCompiler} and its collaborators. */
@RunWith(JUnit4.class)
public class BytecodeCompilerTest {

  @Test
  public void testDelCall_delPackageSelections() throws IOException {
    String soyFileContent1 =
        Joiner.on("\n")
            .join(
                "{namespace ns1 autoescape=\"strict\"}",
                "",
                "/***/",
                "{template .callerTemplate}",
                "  {delcall myApp.myDelegate}",
                "    {param boo: 'aaaaaah' /}",
                "  {/delcall}",
                "{/template}",
                "",
                "/** */",
                "{deltemplate myApp.myDelegate}", // default implementation (doesn't use $boo)
                "  {@param boo : string}",
                "  default",
                "{/deltemplate}",
                "");

    String soyFileContent2 =
        Joiner.on("\n")
            .join(
                "{delpackage SecretFeature}",
                "{namespace ns2 autoescape=\"strict\"}",
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
                "{namespace ns3 autoescape=\"strict\"}",
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
                "{namespace ns3 autoescape=\"strict\"}",
                "",
                "/** */",
                "{template .helper}",
                "  {@param boo : string}",
                "  {$boo}",
                "{/template}",
                "");
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                soyFileContent1, soyFileContent2, soyFileContent3, soyFileContent4)
            .parse()
            .fileSet();
    TemplateRegistry templateRegistry = new TemplateRegistry(soyTree, ErrorReporter.exploding());
    CompiledTemplates templates =
        BytecodeCompiler.compile(templateRegistry, false, ErrorReporter.exploding()).get();
    CompiledTemplate.Factory factory = templates.getTemplateFactory("ns1.callerTemplate");
    Predicate<String> activePackages = Predicates.alwaysFalse();

    assertThat(renderWithContext(factory, getDefaultContext(templates, activePackages)))
        .isEqualTo("default");

    activePackages = Predicates.equalTo("SecretFeature");
    assertThat(renderWithContext(factory, getDefaultContext(templates, activePackages)))
        .isEqualTo("SecretFeature aaaaaah");

    activePackages = Predicates.equalTo("AlternateSecretFeature");
    assertThat(renderWithContext(factory, getDefaultContext(templates, activePackages)))
        .isEqualTo("AlternateSecretFeature aaaaaah");

    activePackages = Predicates.equalTo("NonexistentFeature");
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
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(soyFileContent)
            .addHtmlCommentsForDebug(true)
            .parse()
            .fileSet();
    TemplateRegistry templateRegistry = new TemplateRegistry(soyTree, ErrorReporter.exploding());
    CompiledTemplates templates =
        BytecodeCompiler.compile(templateRegistry, false, ErrorReporter.exploding()).get();

    // HTML templates
    CompiledTemplate.Factory factory = templates.getTemplateFactory("ns.html");
    assertThat(renderWithContext(factory, getDefaultContext(templates)))
        .isEqualTo("<div>foo</div>");
    // If debugSoyTemplateInfo is enabled, we should render additional HTML comments.
    assertThat(renderWithContext(factory, getDefaultContextWithDebugInfo(templates)))
        .isEqualTo("<!--dta_of(ns.html, no-path, 3)--><div>foo</div><!--dta_cf(ns.html)-->");

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
    AdvisingStringBuilder builder = new AdvisingStringBuilder();
    assertEquals(
        RenderResult.done(), factory.create(EMPTY_DICT, EMPTY_DICT).render(builder, context));
    String string = builder.toString();
    return string;
  }

  @Test
  public void testDelCall_delVariant() throws IOException {
    String soyFileContent1 =
        Joiner.on("\n")
            .join(
                "{namespace ns1 autoescape=\"strict\"}",
                "",
                "/***/",
                "{template .callerTemplate}",
                "  {@param variant : string}",
                "  {delcall ns1.del variant=\"$variant\" allowemptydefault=\"true\"/}",
                "{/template}",
                "",
                "/** */",
                "{deltemplate ns1.del variant=\"'v1'\"}",
                "  v1",
                "{/deltemplate}",
                "",
                "/** */",
                "{deltemplate ns1.del variant=\"'v2'\"}",
                "  v2",
                "{/deltemplate}",
                "");

    CompiledTemplates templates = compileFiles(soyFileContent1);
    CompiledTemplate.Factory factory = templates.getTemplateFactory("ns1.callerTemplate");
    RenderContext context = getDefaultContext(templates);
    AdvisingStringBuilder builder = new AdvisingStringBuilder();
    assertEquals(
        RenderResult.done(),
        factory
            .create(TemplateTester.asRecord(ImmutableMap.of("variant", "v1")), EMPTY_DICT)
            .render(builder, context));
    assertThat(builder.getAndClearBuffer()).isEqualTo("v1");

    assertEquals(
        RenderResult.done(),
        factory
            .create(TemplateTester.asRecord(ImmutableMap.of("variant", "v2")), EMPTY_DICT)
            .render(builder, context));
    assertThat(builder.getAndClearBuffer()).isEqualTo("v2");

    assertEquals(
        RenderResult.done(),
        factory
            .create(TemplateTester.asRecord(ImmutableMap.of("variant", "unknown")), EMPTY_DICT)
            .render(builder, context));
    assertThat(builder.toString()).isEmpty();

    TemplateMetadata templateMetadata = getTemplateMetadata(templates, "ns1.callerTemplate");
    assertThat(templateMetadata.callees()).isEmpty();
    assertThat(templateMetadata.delCallees()).asList().containsExactly("ns1.del");
  }

  @Test
  public void testCallBasicNode() throws IOException {
    CompiledTemplates templates =
        TemplateTester.compileFile(
            "{namespace ns autoescape=\"strict\"}",
            "",
            "/** */",
            "{template .callerDataAll}",
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

  private static TemplateMetadata getTemplateMetadata(CompiledTemplates templates, String name) {
    return templates
        .getTemplateFactory(name)
        .getClass()
        .getDeclaringClass()
        .getAnnotation(TemplateMetadata.class);
  }

  private String render(CompiledTemplates templates, SoyRecord params, String name)
      throws IOException {
    CompiledTemplate caller = templates.getTemplateFactory(name).create(params, EMPTY_DICT);
    AdvisingStringBuilder sb = new AdvisingStringBuilder();
    assertEquals(RenderResult.done(), caller.render(sb, getDefaultContext(templates)));
    String output = sb.toString();
    return output;
  }

  @Test
  public void testForNode() {
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
  public void testForEachNode() {
    // empty loop
    assertThatTemplateBody(
            "{@param list: list<int>}", "{foreach $i in $list}", "  {$i}", "{/foreach}")
        .rendersAs("", ImmutableMap.of("list", EMPTY_LIST));

    assertThatTemplateBody(
            "{@param list: list<int>}",
            "{foreach $i in $list}",
            "  {$i}",
            "{ifempty}",
            "  empty",
            "{/foreach}")
        .rendersAs("empty", ImmutableMap.of("list", EMPTY_LIST));

    assertThatTemplateBody("{foreach $i in [1,2,3,4,5]}", "  {$i}", "{/foreach}")
        .rendersAs("12345");

    assertThatTemplateBody(
            "{foreach $i in [1,2,3,4,5]}",
            "  {if isFirst($i)}",
            "    first!{\\n}",
            "  {/if}",
            "  {$i}-{index($i)}{\\n}",
            "  {if isLast($i)}",
            "    last!",
            "  {/if}",
            "{/foreach}")
        .rendersAs(Joiner.on('\n').join("first!", "1-0", "2-1", "3-2", "4-3", "5-4", "last!"));
  }

  @Test
  public void testForEachNode_mapKeys() {
    assertThatTemplateBody(
            "{@param map : map<string, int>}",
            "{foreach $key in keys($map)}",
            "  {$key} - {$map[$key]}{if not isLast($key)}{\\n}{/if}",
            "{/foreach}")
        .rendersAs("a - 1\nb - 2", ImmutableMap.of("map", ImmutableMap.of("a", 1, "b", 2)));
  }

  @Test
  public void testForEachNode_nullableList() {
    // The compiler should be rejected this :(
    assertThatTemplateBody(
            "{@param map : map<string, list<int>>}",
            "{foreach $item in $map?['key']}",
            "  {$item}",
            "{/foreach}")
        .rendersAs(
            "123", ImmutableMap.of("map", ImmutableMap.of("key", ImmutableList.of(1, 2, 3))));
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
    assertThatTemplateBody("{plusOne(1)}").withSoyFunction(plusOneFunction).rendersAs("2");
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
            "{foreach $item in $list}",
            "{if index($item) == ceiling(length($list) / 2) - 1}",
            "  Middle.",
            "{/if}",
            "{/foreach}",
            "")
        .rendersAs("Middle.", ImmutableMap.of("list", ImmutableList.of(1, 2, 3)));
  }

  @Test
  public void testOptionalListIteration() {
    CompiledTemplateSubject tester =
        assertThatTemplateBody(
            "{@param? list : list<int>}",
            "{if $list}",
            "  {foreach $item in $list}",
            "    {$item}",
            "  {/foreach}",
            "{/if}",
            "");
    tester.rendersAs("123", ImmutableMap.of("list", ImmutableList.of(1, 2, 3)));
    tester.rendersAs("");
  }

  @Test
  public void testPrintDirectives() {
    assertThatTemplateBody("{' blah &&blahblahblah' |escapeHtml|insertWordBreaks:8}")
        .rendersAs(" blah &amp;&amp;blahbl<wbr>ahblah");
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
            "{namespace ns autoescape=\"strict\"}",
            "/** ",
            " * @param foo A foo",
            "*/ ",
            "{template .foo}",
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
        .rendersAs("2", ImmutableMap.<String, Object>of(), ImmutableMap.of("foo", 1))
        .rendersAs("3", ImmutableMap.<String, Object>of(), ImmutableMap.of("foo", 2))
        .rendersAs("4", ImmutableMap.<String, Object>of(), ImmutableMap.of("foo", 3));
  }

  @Test
  public void testInjectParam_legacyIj() {
    assertThatTemplateBody("{$ij.foo + 1}")
        .rendersAs("2", ImmutableMap.<String, Object>of(), ImmutableMap.of("foo", 1))
        .rendersAs("3", ImmutableMap.<String, Object>of(), ImmutableMap.of("foo", 2))
        .rendersAs("4", ImmutableMap.<String, Object>of(), ImmutableMap.of("foo", 3));
  }

  @Test
  public void testParamValidation() throws Exception {
    CompiledTemplates templates =
        TemplateTester.compileTemplateBody("{@param foo : int}", "{$foo ?: -1}");
    CompiledTemplate.Factory singleParam = templates.getTemplateFactory("ns.foo");
    RenderContext context = getDefaultContext(templates);
    AdvisingStringBuilder builder = new AdvisingStringBuilder();

    SoyDict params =
        SoyValueConverter.UNCUSTOMIZED_INSTANCE.newDict("foo", IntegerData.forValue(1));
    singleParam.create(params, EMPTY_DICT).render(builder, context);
    assertEquals("1", builder.getAndClearBuffer());

    singleParam.create(EMPTY_DICT, EMPTY_DICT).render(builder, context);
    assertEquals("-1", builder.getAndClearBuffer());

    templates = TemplateTester.compileTemplateBody("{@inject foo : int}", "{$foo}");
    CompiledTemplate.Factory singleIj = templates.getTemplateFactory("ns.foo");
    context = getDefaultContext(templates);

    params = SoyValueConverter.UNCUSTOMIZED_INSTANCE.newDict("foo", IntegerData.forValue(1));
    singleIj.create(SoyValueConverter.EMPTY_DICT, params).render(builder, context);
    assertEquals("1", builder.getAndClearBuffer());

    params = SoyValueConverter.UNCUSTOMIZED_INSTANCE.newDict();
    singleIj.create(SoyValueConverter.EMPTY_DICT, params).render(builder, context);
    assertEquals("null", builder.getAndClearBuffer());
  }

  @Test
  public void testParamFields() throws Exception {
    CompiledTemplate.Factory multipleParams =
        TemplateTester.compileTemplateBody(
                "{@param foo : string}",
                "{@param baz : string}",
                "{@inject bar : string}",
                "{$foo + $baz + $bar}")
            .getTemplateFactory("ns.foo");
    SoyDict params =
        SoyValueConverter.UNCUSTOMIZED_INSTANCE.newDict(
            "foo", StringData.forValue("foo"),
            "bar", StringData.forValue("bar"),
            "baz", StringData.forValue("baz"));
    CompiledTemplate template = multipleParams.create(params, params);
    assertEquals(StringData.forValue("foo"), getField("foo", template));
    assertEquals(StringData.forValue("bar"), getField("bar", template));
    assertEquals(StringData.forValue("baz"), getField("baz", template));

    TemplateMetadata templateMetadata = template.getClass().getAnnotation(TemplateMetadata.class);
    assertThat(templateMetadata.injectedParams()).asList().containsExactly("bar");
    assertThat(templateMetadata.callees()).isEmpty();
    assertThat(templateMetadata.delCallees()).isEmpty();
  }

  @Test
  public void testPassHtmlAsNullableString() throws Exception {
    CompiledTemplateSubject subject =
        TemplateTester.assertThatFile(
            "{namespace ns}",
            "{template .foo}",
            "  {@param? content : string}",
            "  {$content ?: 'empty' |escapeHtml}",
            "{/template}");
    subject.rendersAs("empty");
    subject.rendersAs(
        "<b>hello</b>", ImmutableMap.of("content", SanitizedContents.constantHtml("<b>hello</b>")));
  }

  private Object getField(String name, CompiledTemplate template) throws Exception {
    Field declaredField = template.getClass().getDeclaredField(name);
    declaredField.setAccessible(true);
    return declaredField.get(template);
  }

  @Test
  public void testBasicFunctionality() {
    // make sure we don't break standard reflection access
    CompiledTemplate.Factory factory =
        TemplateTester.compileTemplateBody("hello world").getTemplateFactory("ns.foo");
    assertEquals("com.google.template.soy.jbcsrc.gen.ns.foo$Factory", factory.getClass().getName());
    assertEquals("Factory", factory.getClass().getSimpleName());

    CompiledTemplate templateInstance = factory.create(EMPTY_DICT, EMPTY_DICT);
    Class<? extends CompiledTemplate> templateClass = templateInstance.getClass();
    assertEquals("com.google.template.soy.jbcsrc.gen.ns.foo", templateClass.getName());
    assertEquals("foo", templateClass.getSimpleName());

    TemplateMetadata templateMetadata = templateClass.getAnnotation(TemplateMetadata.class);
    assertEquals("HTML", templateMetadata.contentKind());
    assertEquals(ContentKind.HTML, templateInstance.kind());
    assertThat(templateMetadata.injectedParams()).isEmpty();
    assertThat(templateMetadata.callees()).isEmpty();
    assertThat(templateMetadata.delCallees()).isEmpty();

    // ensure that the factory is an inner class of the template.
    assertEquals(templateClass, factory.getClass().getEnclosingClass());
    assertEquals(templateClass, factory.getClass().getDeclaringClass());

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
    assertThat(templates.getTemplateContentKind("ns.foo")).hasValue(ContentKind.HTML);
  }

  @Test
  public void testContentKindNonStrict() {
    assertThat(
            TemplateTester.compileFile(
                    "{namespace ns autoescape=\"deprecated-contextual\"}",
                    "/** foo */",
                    "{template .foo}",
                    "{/template}")
                .getTemplateFactory("ns.foo")
                .getClass()
                .getDeclaringClass()
                .getAnnotation(TemplateMetadata.class)
                .contentKind())
        .isEmpty();
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
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(soyFileContent1).parse().fileSet();
    // apply an escaping directive to the callsite, just like the autoescaper would
    CallDelegateNode cdn =
        SoyTreeUtils.getAllNodesOfType(soyTree.getChild(0), CallDelegateNode.class).get(0);
    cdn.setEscapingDirectiveNames(ImmutableList.of("|escapeHtml"));
    TemplateRegistry templateRegistry = new TemplateRegistry(soyTree, ErrorReporter.exploding());
    CompiledTemplates templates =
        BytecodeCompiler.compile(templateRegistry, false, ErrorReporter.exploding()).get();
    CompiledTemplate.Factory caller = templates.getTemplateFactory("ns.callerTemplate");
    try {
      renderWithContext(caller, getDefaultContext(templates));
      fail();
    } catch (IllegalArgumentException iae) {
      assertThat(iae)
          .hasMessageThat()
          .isEqualTo(
              "Found no active impl for delegate call to 'myApp.myDelegate' "
                  + "(and no attribute allowemptydefault=\"true\").");
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
    } catch (Throwable t) {
      assertThat(getTemplateLineNumber("ns.foo", t)).isEqualTo(8);
    }
    try {
      render(
          templates, asRecord(ImmutableMap.of("p1", 1, "p2", failed, "p3", 3, "p4", 4)), "ns.foo");
      fail();
    } catch (Throwable t) {
      assertThat(getTemplateLineNumber("ns.foo", t)).isEqualTo(9);
    }
    try {
      render(
          templates, asRecord(ImmutableMap.of("p1", 1, "p2", 2, "p3", failed, "p4", 4)), "ns.foo");
      fail();
    } catch (Throwable t) {
      assertThat(getTemplateLineNumber("ns.foo", t)).isEqualTo(10);
    }
    try {
      render(
          templates, asRecord(ImmutableMap.of("p1", 1, "p2", 2, "p3", 3, "p4", failed)), "ns.foo");
      fail();
    } catch (Throwable t) {
      assertThat(getTemplateLineNumber("ns.foo", t)).isEqualTo(11);
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
                    "  {foreach $foo in $list}",
                    "    {$foo}{if not isLast($foo)}{sp}{/if}",
                    "  {/foreach}",
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
    } catch (Throwable t) {
      assertThat(getTemplateLineNumber("ns.foo", t)).isEqualTo(7);
    }
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
    private final Map<String, String> renamingMap;

    FakeRenamingMap(Map<String, String> renamingMap) {
      this.renamingMap = renamingMap;
    }

    @Nullable
    @Override
    public String get(String key) {
      return renamingMap.get(key);
    }
  }

  private CompiledTemplates compileFiles(String... soyFileContents) {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(soyFileContents).parse().fileSet();
    TemplateRegistry templateRegistry = new TemplateRegistry(soyTree, ErrorReporter.exploding());
    CompiledTemplates templates =
        BytecodeCompiler.compile(templateRegistry, false, ErrorReporter.exploding()).get();
    return templates;
  }
}
