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

import static com.google.template.soy.data.SoyValueHelper.EMPTY_DICT;
import static com.google.template.soy.jbcsrc.TemplateTester.assertThatTemplateBody;

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.jbcsrc.api.RenderContext;
import com.google.template.soy.shared.SoyCssRenamingMap;

import junit.framework.TestCase;

import java.util.Map;

/**
 * A test for the template compiler, notably {@link BytecodeCompiler} and its collaborators.
 */
public class BytecodeCompilerTest extends TestCase {

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
    RenderContext ctx = new RenderContext(EMPTY_DICT,
        new FakeRenamingMap(ImmutableMap.of("foo", "bar")),
        SoyCssRenamingMap.IDENTITY);
    assertThatTemplateBody("{css foo}").rendersAs("bar", ctx);
    assertThatTemplateBody("{css foo2}").rendersAs("foo2", ctx);
    assertThatTemplateBody("{css 1+2, foo2}").rendersAs("3-foo2", ctx);
  }

  public void testXidNode() {
    RenderContext ctx = new RenderContext(EMPTY_DICT,
        SoyCssRenamingMap.IDENTITY,
        new FakeRenamingMap(ImmutableMap.of("foo", "bar")));
    assertThatTemplateBody("{xid foo}").rendersAs("bar", ctx);
    assertThatTemplateBody("{xid foo2}").rendersAs("foo2_", ctx);
  }

  public void testBasicFunctionality() {
    assertThatTemplateBody("hello world")
        .hasCompiledTemplateFactoryClassName("com.google.template.soy.jbcsrc.gen.ns$$foo_Factory")
        .hasCompiledTemplateClassName("com.google.template.soy.jbcsrc.gen.ns$$foo")
        .rendersAs("hello world");
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
