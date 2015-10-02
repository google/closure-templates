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
import static com.google.template.soy.data.SoyValueHelper.EMPTY_DICT;
import static com.google.template.soy.jbcsrc.TemplateTester.DEFAULT_CONTEXT;
import static com.google.template.soy.jbcsrc.TemplateTester.assertThatFile;
import static com.google.template.soy.jbcsrc.TemplateTester.assertThatTemplateBody;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueHelper;
import com.google.template.soy.data.internal.BasicParamStore;
import com.google.template.soy.data.internal.EasyDictImpl;
import com.google.template.soy.data.internal.ParamStore;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.jbcsrc.TemplateTester.CompiledTemplateSubject;
import com.google.template.soy.jbcsrc.api.AdvisingStringBuilder;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.jbcsrc.shared.CompiledTemplate;
import com.google.template.soy.jbcsrc.shared.CompiledTemplates;
import com.google.template.soy.jbcsrc.shared.DelTemplateSelector;
import com.google.template.soy.jbcsrc.shared.DelTemplateSelectorImpl;
import com.google.template.soy.jbcsrc.shared.RenderContext;
import com.google.template.soy.jbcsrc.shared.TemplateMetadata;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateRegistry;

import junit.framework.TestCase;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A test for the template compiler, notably {@link BytecodeCompiler} and its collaborators.
 */
public class BytecodeCompilerTest extends TestCase {

  public void testDelCall_delPackageSelections() throws IOException {
    String soyFileContent1 = Joiner.on("\n").join(
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
        "{deltemplate myApp.myDelegate}",  // default implementation (doesn't use $boo)
        "  {@param boo : string}",
        "  000",
        "{/deltemplate}",
        "");

    String soyFileContent2 = Joiner.on("\n").join(
        "{delpackage SecretFeature}",
        "{namespace ns2 autoescape=\"strict\"}",
        "",
        "/** */",
        "{deltemplate myApp.myDelegate}",  // implementation in SecretFeature
        "  {@param boo : string}",
        "  111 {$boo}",
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
                "  222 {call .helper data=\"all\" /}",
                "{/deltemplate}",
                "");

    String soyFileContent4 = Joiner.on("\n").join(
        "{namespace ns3 autoescape=\"strict\"}",
        "",
        "/** */",
        "{template .helper private=\"true\"}",
        "  {@param boo : string}",
        "  {$boo}",
        "{/template}",
        "");
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                soyFileContent1, soyFileContent2, soyFileContent3, soyFileContent4)
            .parse()
            .fileSet();
    TemplateRegistry templateRegistry = new TemplateRegistry(soyTree, ExplodingErrorReporter.get());
    CompiledTemplates templates = 
        BytecodeCompiler.compile(templateRegistry, false, ExplodingErrorReporter.get()).get();
    DelTemplateSelectorImpl.Factory selectorFactory =
        new DelTemplateSelectorImpl.Factory(templateRegistry, templates);
    CompiledTemplate.Factory factory = templates.getTemplateFactory("ns1.callerTemplate");
    ImmutableSet<String> activePackages = ImmutableSet.<String>of();

    assertThat(renderWithActivePackages(factory, selectorFactory.create(activePackages)))
        .isEqualTo("000");

    activePackages = ImmutableSet.of("SecretFeature");
    assertThat(renderWithActivePackages(factory, selectorFactory.create(activePackages)))
        .isEqualTo("111 aaaaaah");

    activePackages = ImmutableSet.of("AlternateSecretFeature");
    assertThat(renderWithActivePackages(factory, selectorFactory.create(activePackages)))
        .isEqualTo("222 aaaaaah");
    
    activePackages = ImmutableSet.of("NonexistentFeature");
    assertThat(renderWithActivePackages(factory, selectorFactory.create(activePackages)))
        .isEqualTo("000");
  }

  private String renderWithActivePackages(CompiledTemplate.Factory factory,
      DelTemplateSelector create) throws IOException {
    RenderContext context = DEFAULT_CONTEXT.toBuilder()
        .withTemplateSelector(create)
        .build();
    AdvisingStringBuilder builder = new AdvisingStringBuilder();
    assertEquals(RenderResult.done(), 
        factory.create(EMPTY_DICT, EMPTY_DICT).render(builder, context));
    String string = builder.toString();
    return string;
  }

  public void testDelCall_delVariant() throws IOException {
    String soyFileContent1 = Joiner.on("\n").join(
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

    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(soyFileContent1).parse().fileSet();
    TemplateRegistry templateRegistry = new TemplateRegistry(soyTree, ExplodingErrorReporter.get());
    CompiledTemplates templates = 
        BytecodeCompiler.compile(templateRegistry, false, ExplodingErrorReporter.get()).get();
    DelTemplateSelectorImpl.Factory selectorFactory =
        new DelTemplateSelectorImpl.Factory(templateRegistry, templates);
    CompiledTemplate.Factory factory = templates.getTemplateFactory("ns1.callerTemplate");
    RenderContext context = DEFAULT_CONTEXT.toBuilder()
        .withTemplateSelector(selectorFactory.create(ImmutableSet.<String>of()))
        .build();
    AdvisingStringBuilder builder = new AdvisingStringBuilder();
    assertEquals(RenderResult.done(), 
        factory.create(TemplateTester.asRecord(ImmutableMap.of("variant", "v1")), EMPTY_DICT)
            .render(builder, context));
    assertThat(builder.getAndClearBuffer()).isEqualTo("v1");

    assertEquals(RenderResult.done(),
        factory.create(TemplateTester.asRecord(ImmutableMap.of("variant", "v2")), EMPTY_DICT)
            .render(builder, context));
    assertThat(builder.getAndClearBuffer()).isEqualTo("v2");

    assertEquals(RenderResult.done(),
        factory.create(TemplateTester.asRecord(ImmutableMap.of("variant", "unknown")), EMPTY_DICT)
            .render(builder, context));
    assertThat(builder.toString()).isEmpty();
  }

  public void testCallBasicNode() throws IOException {
    CompiledTemplates templates = TemplateTester.compileFile(
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
    
    params = new BasicParamStore(2);
    params.setField("rec", new BasicParamStore(2).setField("foo", StringData.forValue("foo")));
    assertThat(render(templates, params, "ns.callerDataExpr")).isEqualTo("Foo: foo\nBoo: null\n");
    ((ParamStore) params.getField("rec")).setField("boo", StringData.forValue("boo"));
    assertThat(render(templates, params, "ns.callerDataExpr")).isEqualTo("Foo: foo\nBoo: boo\n");
    
    params = new BasicParamStore(2);
    params.setField("p1", StringData.forValue("foo"));
    assertThat(render(templates, params, "ns.callerParams")).isEqualTo("Foo: foo\nBoo: a1b\n");
    
    params = new BasicParamStore(2);
    params.setField("p1", StringData.forValue("foo"));
    params.setField("boo", StringData.forValue("boo"));
    assertThat(render(templates, params, "ns.callerParamsAndData"))
        .isEqualTo("Foo: foo\nBoo: boo\n");
  }

  private String render(CompiledTemplates templates, SoyRecord params, String name)
      throws IOException {
    CompiledTemplate caller = templates.getTemplateFactory(name).create(params, EMPTY_DICT);
    AdvisingStringBuilder sb = new AdvisingStringBuilder();
    assertEquals(RenderResult.done(), caller.render(sb, DEFAULT_CONTEXT));
    String output = sb.toString();
    return output;
  }

  public void testForNode() {
    // empty loop
    assertThatTemplateBody(
        "{for $i in range(2, 2)}",
        "  {$i}",
        "{/for}").rendersAs("");

    assertThatTemplateBody(
        "{for $i in range(10)}", 
        "  {$i}",
        "{/for}").rendersAs("0123456789");

    assertThatTemplateBody(
        "{for $i in range(2, 10)}", 
        "  {$i}",
        "{/for}").rendersAs("23456789");

    assertThatTemplateBody(
        "{for $i in range(2, 10, 2)}", 
        "  {$i}",
        "{/for}").rendersAs("2468");
  }

  public void testForEachNode() {
    // empty loop
    assertThatTemplateBody(
        "{foreach $i in []}",
        "  {$i}",
        "{/foreach}").rendersAs("");

    assertThatTemplateBody(
        "{foreach $i in []}",
        "  {$i}",
        "{ifempty}",
        "  empty",
        "{/foreach}").rendersAs("empty");

    assertThatTemplateBody(
        "{foreach $i in [1,2,3,4,5]}",
        "  {$i}",
        "{/foreach}").rendersAs("12345");

    assertThatTemplateBody(
        "{foreach $i in [1,2,3,4,5]}",
        "  {if isFirst($i)}",
        "    first!{\\n}",
        "  {/if}",
        "  {$i}-{index($i)}{\\n}",
        "  {if isLast($i)}",
        "    last!",
        "  {/if}",
        "{/foreach}").rendersAs(Joiner.on('\n').join(
            "first!",
            "1-0",
            "2-1",
            "3-2",
            "4-3",
            "5-4",
            "last!"));
  }

  public void testForEachNode_mapKeys() {
    assertThatTemplateBody(
        "{@param map : map<string, int>}",
        "{foreach $key in keys($map)}",
        "  {$key} - {$map[$key]}{if not isLast($key)}{\\n}{/if}",
        "{/foreach}")
        .rendersAs("a - 1\nb - 2",
            ImmutableMap.of("map", ImmutableMap.of("a", 1, "b", 2)));
  }

  public void testForEachNode_nullableList() {
    // The compiler should be rejected this :(
    assertThatTemplateBody(
        "{@param map : map<string, list<int>>}",
        "{foreach $item in $map?['key']}",
        "  {$item}",
        "{/foreach}")
        .rendersAs("123",
            ImmutableMap.of("map", ImmutableMap.of("key", ImmutableList.of(1, 2, 3))));
  }

  public void testSwitchNode() {
    assertThatTemplateBody(
        "{switch 1}", 
        "  {case 1}",
        "    one",
        "  {case 2}",
        "    two",
        "  {default}",
        "    default",
        "{/switch}").rendersAs("one");
    
    assertThatTemplateBody(
        "{switch 2}", 
        "  {case 1}",
        "    one",
        "  {case 2}",
        "    two",
        "  {default}",
        "    default",
        "{/switch}").rendersAs("two");
    assertThatTemplateBody(
        "{switch 'asdf'}", 
        "  {case 1}",
        "    one",
        "  {case 2}",
        "    two",
        "  {default}",
        "    default",
        "{/switch}").rendersAs("default");
  }

  public void testSwitchNode_empty() {
    assertThatTemplateBody("{switch 1}", "{/switch}").rendersAs("");
  }

  public void testSwitchNode_defaultOnly() {
    assertThatTemplateBody("{switch 1}", "  {default}Hello", "{/switch}").rendersAs("Hello");
  }

  public void testNestedSwitch() {
    assertThatTemplateBody(
        "{switch 'a'}", 
        "  {case 'a'}",
        "    {switch 1} {case 1} sub {default} sub default {/switch}",
        "  {case 2}",
        "    two",
        "  {default}",
        "    default",
        "{/switch}").rendersAs(" sub ");
  }

  public void testIfNode() {
    assertThatTemplateBody(
        "{if true}", 
        "  hello",
        "{/if}").rendersAs("hello");

    assertThatTemplateBody(
        "{if false}", 
        "  hello",
        "{/if}").rendersAs("");

    assertThatTemplateBody(
        "{if false}", 
        "  one",
        "{elseif false}",
        "  two",
        "{/if}").rendersAs("");
    assertThatTemplateBody(
        "{if true}", 
        "  one",
        "{elseif false}",
        "  two",
        "{/if}").rendersAs("one");
    assertThatTemplateBody(
        "{if false}", 
        "  one",
        "{elseif true}",
        "  two",
        "{/if}").rendersAs("two");

    assertThatTemplateBody(
        "{if true}",
        "  one",
        "{elseif true}",
        "  two",
        "{else}",
        "  three",
        "{/if}").rendersAs("one");
    assertThatTemplateBody(
        "{if false}",
        "  one",
        "{elseif true}",
        "  two",
        "{else}",
        "  three",
        "{/if}").rendersAs("two");
    assertThatTemplateBody(
        "{if false}",
        "  one",
        "{elseif false}",
        "  two",
        "{else}",
        "  three",
        "{/if}").rendersAs("three");
  }

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

  public void testPrintNode() {
    assertThatTemplateBody("{1 + 2}").rendersAs("3");
    assertThatTemplateBody("{'asdf'}").rendersAs("asdf");
  }

  public void testLogNode() {
    assertThatTemplateBody(
        "{log}", 
        "  hello{sp}",
        "  {'world'}",
        "{/log}").logsOutput("hello world");
  }

  public void testRawTextNode() {
    assertThatTemplateBody("hello raw text world").rendersAs("hello raw text world");
  }

  public void testCssNode() {
    RenderContext ctx = DEFAULT_CONTEXT.toBuilder()
        .withCssRenamingMap(new FakeRenamingMap(ImmutableMap.of("foo", "bar")))
        .build();
    assertThatTemplateBody("{css foo}").rendersAs("bar", ctx);
    assertThatTemplateBody("{css foo2}").rendersAs("foo2", ctx);
    assertThatTemplateBody("{css 1+2, foo2}").rendersAs("3-foo2", ctx);
  }

  public void testXidNode() {
    RenderContext ctx = DEFAULT_CONTEXT.toBuilder()
        .withXidRenamingMap(new FakeRenamingMap(ImmutableMap.of("foo", "bar")))
        .build();
    assertThatTemplateBody("{xid foo}").rendersAs("bar", ctx);
    assertThatTemplateBody("{xid foo2}").rendersAs("foo2_", ctx);
  }

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

  public void testIsNonNull() {
    assertThatTemplateBody(
        "{@param foo : [a : [ b : string]] }",
        "{isNonnull($foo.a)}"
    ).rendersAs("false", ImmutableMap.<String, Object>of("foo", ImmutableMap.<String, String>of()));
  }

  // Tests for a bug in an integration test where unnecessary float unboxing conversions happened.
  public void testBoxedIntComparisonFromFunctions() {
    assertThatTemplateBody(
        "{@param list : list<int>}",
        "{foreach $item in $list}",
        "{if index($item) == ceiling(length($list) / 2) - 1}",
        "  Middle.",
        "{/if}",
        "{/foreach}",
        ""
    ).rendersAs("Middle.", ImmutableMap.of("list", ImmutableList.of(1, 2, 3)));
  }

  public void testOptionalListIteration() {
    CompiledTemplateSubject tester = assertThatTemplateBody(
        "{@param? list : list<int>}",
        "{if $list}",
        "  {foreach $item in $list}",
        "    {$item}",
        "  {/foreach}",
        "{/if}",
        ""
    );
    tester.rendersAs("123", ImmutableMap.of("list", ImmutableList.of(1, 2, 3)));
    tester.rendersAs("");
  }

  public void testPrintDirectives() {
    assertThatTemplateBody("{' blah &&blahblahblah' |escapeHtml|insertWordBreaks:8}")
        .rendersAs(" blah &amp;&amp;blahbl<wbr>ahblah");
  }

  public void testParam() {
    assertThatTemplateBody(
        "{@param foo : int }",
        "{$foo + 1}")
        .rendersAs("2", ImmutableMap.of("foo", 1))
        .rendersAs("3", ImmutableMap.of("foo", 2))
        .rendersAs("4", ImmutableMap.of("foo", 3));
  }
  
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

  public void testInjectParam() {
    assertThatTemplateBody(
        "{@inject foo : int }",
        "{$foo + 1}")
        .rendersAs("2", ImmutableMap.<String, Object>of(), ImmutableMap.of("foo", 1))
        .rendersAs("3", ImmutableMap.<String, Object>of(), ImmutableMap.of("foo", 2))
        .rendersAs("4", ImmutableMap.<String, Object>of(), ImmutableMap.of("foo", 3));
  }

  public void testInjectParam_legacyIj() {
    assertThatTemplateBody(
        "{$ij.foo + 1}")
        .rendersAs("2", ImmutableMap.<String, Object>of(), ImmutableMap.of("foo", 1))
        .rendersAs("3", ImmutableMap.<String, Object>of(), ImmutableMap.of("foo", 2))
        .rendersAs("4", ImmutableMap.<String, Object>of(), ImmutableMap.of("foo", 3));
  }

  public void testParamValidation() throws Exception {
    CompiledTemplate.Factory singleParam =
        TemplateTester.compileTemplateBody(
            "{@param foo : int}", 
            "{$foo ?: -1}");
    AdvisingStringBuilder builder = new AdvisingStringBuilder();
    EasyDictImpl params = new EasyDictImpl(SoyValueHelper.UNCUSTOMIZED_INSTANCE);
    params.setField("foo", IntegerData.forValue(1));
    singleParam.create(params, EMPTY_DICT).render(builder, DEFAULT_CONTEXT);
    assertEquals("1", builder.getAndClearBuffer());
    singleParam.create(EMPTY_DICT, EMPTY_DICT).render(builder, DEFAULT_CONTEXT);
    assertEquals("-1", builder.getAndClearBuffer());

    CompiledTemplate.Factory singleIj = 
        TemplateTester.compileTemplateBody("{@inject foo : int}", "{$foo}");
    params.setField("foo", IntegerData.forValue(1));
    singleIj.create(SoyValueHelper.EMPTY_DICT, params).render(builder, DEFAULT_CONTEXT);
    assertEquals("1", builder.getAndClearBuffer());
    params.delField("foo");
    singleIj.create(SoyValueHelper.EMPTY_DICT, params).render(builder, DEFAULT_CONTEXT);
    assertEquals("null", builder.getAndClearBuffer());
  }

  public void testParamFields() throws Exception {
    CompiledTemplate.Factory multipleParams =
        TemplateTester.compileTemplateBody(
            "{@param foo : string}",
            "{@param baz : string}",
            "{@inject bar : string}",
            "{$foo + $baz + $bar}");
    EasyDictImpl params = new EasyDictImpl(SoyValueHelper.UNCUSTOMIZED_INSTANCE);
    params.setField("foo", StringData.forValue("foo"));
    params.setField("bar", StringData.forValue("bar"));
    params.setField("baz", StringData.forValue("baz"));
    CompiledTemplate template = multipleParams.create(params, params);
    assertEquals(StringData.forValue("foo"), getField("foo", template));
    assertEquals(StringData.forValue("bar"), getField("bar", template));
    assertEquals(StringData.forValue("baz"), getField("baz", template));
  }

  private Object getField(String name, CompiledTemplate template) throws Exception {
    Field declaredField = template.getClass().getDeclaredField(name);
    declaredField.setAccessible(true);
    return declaredField.get(template);
  }

  public void testBasicFunctionality() {
    // make sure we don't break standard reflection access
    CompiledTemplate.Factory factory = TemplateTester.compileTemplateBody("hello world");
    assertEquals("com.google.template.soy.jbcsrc.gen.ns.foo$Factory",
        factory.getClass().getName());
    assertEquals("Factory", factory.getClass().getSimpleName());

    Class<? extends CompiledTemplate> templateClass = 
        factory.create(EMPTY_DICT, EMPTY_DICT).getClass();
    assertEquals("com.google.template.soy.jbcsrc.gen.ns.foo", templateClass.getName());
    assertEquals("foo", templateClass.getSimpleName());
    assertEquals("HTML", templateClass.getAnnotation(TemplateMetadata.class).contentKind());

    // ensure that the factory is an inner class of the template.
    assertEquals(templateClass, factory.getClass().getEnclosingClass());
    assertEquals(templateClass, factory.getClass().getDeclaringClass());

    assertThat(templateClass.getDeclaredClasses()).asList().contains(factory.getClass());
  }

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
                + "<br /><br />"
                + "ArchiveArchiveArchiveArchive",
            ImmutableMap.of("quota", 26, "url", "http://foo.com"));
  }

  public void testGenders() {
    CompiledTemplateSubject tester = assertThatTemplateBody(
        "{@param userGender : string}",
        "{@param targetName : string}",
        "{@param targetGender : string}",
        "{msg genders=\"$userGender, $targetGender\" desc=\"...\"}",
        "  You replied to {$targetName}.",
        "{/msg}",
        "");
    tester.rendersAs("You replied to bender the offender.", 
            ImmutableMap.of(
                "userGender", "male", 
                "targetName", "bender the offender", 
                "targetGender", "male"));
    tester.rendersAs("You replied to gender bender.", 
        ImmutableMap.of(
            "userGender", "male", 
            "targetName", "gender bender", 
            "targetGender", "female"));
  }
  
  private static final class FakeRenamingMap implements SoyCssRenamingMap {
    private final Map<String, String> renamingMap;
    FakeRenamingMap(Map<String, String> renamingMap) {
      this.renamingMap = renamingMap;
    }
    @Override public String get(String key) {
      return renamingMap.get(key);
    }
  }
}
