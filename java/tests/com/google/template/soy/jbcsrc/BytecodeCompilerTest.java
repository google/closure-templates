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
import static com.google.template.soy.jbcsrc.TemplateTester.assertThatTemplateBody;

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.data.SoyDataException;
import com.google.template.soy.data.SoyValueHelper;
import com.google.template.soy.data.internal.EasyDictImpl;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.jbcsrc.api.CompiledTemplate;
import com.google.template.soy.jbcsrc.api.RenderContext;
import com.google.template.soy.shared.SoyCssRenamingMap;

import junit.framework.TestCase;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * A test for the template compiler, notably {@link BytecodeCompiler} and its collaborators.
 */
public class BytecodeCompilerTest extends TestCase {

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
        SoyCssRenamingMap.IDENTITY);
    assertThatTemplateBody("{css foo}").rendersAs("bar", ctx);
    assertThatTemplateBody("{css foo2}").rendersAs("foo2", ctx);
    assertThatTemplateBody("{css 1+2, foo2}").rendersAs("3-foo2", ctx);
  }

  public void testXidNode() {
    RenderContext ctx = new RenderContext(
        SoyCssRenamingMap.IDENTITY,
        new FakeRenamingMap(ImmutableMap.of("foo", "bar")));
    assertThatTemplateBody("{xid foo}").rendersAs("bar", ctx);
    assertThatTemplateBody("{xid foo2}").rendersAs("foo2_", ctx);
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
