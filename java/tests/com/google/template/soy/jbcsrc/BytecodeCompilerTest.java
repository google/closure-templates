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
import static com.google.template.soy.jbcsrc.TemplateTester.assertThatTemplateBody;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.data.SoyDataException;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueHelper;
import com.google.template.soy.data.internal.BasicParamStore;
import com.google.template.soy.data.internal.EasyDictImpl;
import com.google.template.soy.data.internal.ParamStore;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.jbcsrc.api.AdvisingStringBuilder;
import com.google.template.soy.jbcsrc.api.CompiledTemplate;
import com.google.template.soy.jbcsrc.api.RenderContext;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;

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
    RenderContext ctx = new RenderContext(
        new FakeRenamingMap(ImmutableMap.of("foo", "bar")),
        SoyCssRenamingMap.IDENTITY,
        ImmutableMap.<String, SoyJavaFunction>of(),
        ImmutableMap.<String, SoyJavaPrintDirective>of(),
        SoyValueHelper.UNCUSTOMIZED_INSTANCE);
    assertThatTemplateBody("{css foo}").rendersAs("bar", ctx);
    assertThatTemplateBody("{css foo2}").rendersAs("foo2", ctx);
    assertThatTemplateBody("{css 1+2, foo2}").rendersAs("3-foo2", ctx);
  }

  public void testXidNode() {
    RenderContext ctx = new RenderContext(
        SoyCssRenamingMap.IDENTITY,
        new FakeRenamingMap(ImmutableMap.of("foo", "bar")),
        ImmutableMap.<String, SoyJavaFunction>of(),
        ImmutableMap.<String, SoyJavaPrintDirective>of(),
        SoyValueHelper.UNCUSTOMIZED_INSTANCE);
    assertThatTemplateBody("{xid foo}").rendersAs("bar", ctx);
    assertThatTemplateBody("{xid foo2}").rendersAs("foo2_", ctx);
  }
  
  public void testCallCustomFunction() {
    RenderContext ctx = new RenderContext(
        SoyCssRenamingMap.IDENTITY,
        new FakeRenamingMap(ImmutableMap.of("foo", "bar")),
        ImmutableMap.<String, SoyJavaFunction>of("plusOne", new SoyJavaFunction() {
          @Override public Set<Integer> getValidArgsSizes() {
            return ImmutableSet.of(1);
          }

          @Override public String getName() {
            return "plusOne";
          }

          @Override public SoyValue computeForJava(List<SoyValue> args) {
            return IntegerData.forValue(args.get(0).integerValue() + 1);
          }
        }),
        ImmutableMap.<String, SoyJavaPrintDirective>of(),
        SoyValueHelper.UNCUSTOMIZED_INSTANCE);
    assertThatTemplateBody("{plusOne(1)}").rendersAs("2", ctx);
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

  public void testInjectParam() {
    assertThatTemplateBody(
        "{@inject foo : int }",
        "{$foo + 1}")
        .rendersAs("2", ImmutableMap.<String, Object>of(), ImmutableMap.of("foo", 1))
        .rendersAs("3", ImmutableMap.<String, Object>of(), ImmutableMap.of("foo", 2))
        .rendersAs("4", ImmutableMap.<String, Object>of(), ImmutableMap.of("foo", 3));
  }

  public void testParamValidation() {
    CompiledTemplate.Factory singleParam = 
        TemplateTester.compileTemplateBody("{@param foo : int}");
    EasyDictImpl params = new EasyDictImpl(SoyValueHelper.UNCUSTOMIZED_INSTANCE);
    params.setField("foo", IntegerData.forValue(1));
    singleParam.create(params, SoyValueHelper.EMPTY_DICT);
    params.delField("foo");
    try {
      singleParam.create(params, SoyValueHelper.EMPTY_DICT);
      fail();
    } catch (SoyDataException sde) {
      assertThat(sde).hasMessage("Required @param: 'foo' is undefined.");
    }

    CompiledTemplate.Factory singleIj = 
        TemplateTester.compileTemplateBody("{@inject foo : int}");
    params.setField("foo", IntegerData.forValue(1));
    singleIj.create(SoyValueHelper.EMPTY_DICT, params);
    params.delField("foo");
    try {
      singleIj.create(params, SoyValueHelper.EMPTY_DICT);
      fail();
    } catch (SoyDataException sde) {
      assertThat(sde).hasMessage("Required @inject: 'foo' is undefined.");
    }
  }

  public void testParamFields() throws Exception {
    CompiledTemplate.Factory multipleParams = 
        TemplateTester.compileTemplateBody(
            "{@param foo : string}",
            "{@param baz : string}",
            "{@inject bar : string}");
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
    assertEquals("com.google.template.soy.jbcsrc.gen.nsⅩfoo$Factory",
        factory.getClass().getName());
    assertEquals("Factory", factory.getClass().getSimpleName());

    Class<? extends CompiledTemplate> templateClass = 
        factory.create(EMPTY_DICT, EMPTY_DICT).getClass();
    assertEquals("com.google.template.soy.jbcsrc.gen.nsⅩfoo", templateClass.getName());
    assertEquals("nsⅩfoo", templateClass.getSimpleName());

    // ensure that the factory is an inner class of the template.
    assertEquals(templateClass, factory.getClass().getEnclosingClass());
    assertEquals(templateClass, factory.getClass().getDeclaringClass());

    assertThat(templateClass.getDeclaredClasses()).asList().contains(factory.getClass());
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
