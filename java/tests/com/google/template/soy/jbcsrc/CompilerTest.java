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

import com.google.common.base.Joiner;
import com.google.template.soy.jbcsrc.api.AdvisingStringBuilder;
import com.google.template.soy.jbcsrc.api.CompiledTemplate;
import com.google.template.soy.jbcsrc.api.RenderContext;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.shared.SharedTestUtils;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateRegistry;

import junit.framework.TestCase;

import java.io.IOException;

/**
 * A test for the template compiler, notably {@link BytecodeCompiler} and its collaborators.
 */
public class CompilerTest extends TestCase {
  private static final RenderContext EMPTY_CONTEXT = new RenderContext(
      EMPTY_DICT, SoyCssRenamingMap.IDENTITY, SoyCssRenamingMap.IDENTITY);

  public void testBasicFunctionality() throws IOException {
    CompiledTemplate.Factory factory = factoryForTemplate(toTemplate("hello world"));
    assertEquals("com.google.template.soy.jbcsrc.gen.ns$$foo_Factory",
        factory.getClass().getName());
    CompiledTemplate template = factory.create(EMPTY_DICT);
    assertEquals("com.google.template.soy.jbcsrc.gen.ns$$foo",
        template.getClass().getName());

    AdvisingStringBuilder builder = new AdvisingStringBuilder();
    RenderResult result = template.render(builder, EMPTY_CONTEXT);
    assertEquals(RenderResult.done(), result);
    assertEquals("", builder.toString());
  }

  private static String toTemplate(String ...body) {
    StringBuilder builder = new StringBuilder();
    builder.append("{namespace ns autoescape=\"strict\"}\n")
        .append("{template .foo}\n");
    Joiner.on("\n").appendTo(builder, body);
    builder.append("\n{/template}\n");
    return builder.toString();
  }

  private static CompiledTemplate.Factory factoryForTemplate(String template) {
    SoyFileSetNode fileSet = SharedTestUtils.parseSoyFiles(template).getParseTree();
    TemplateRegistry registry = new TemplateRegistry(fileSet);
    return BytecodeCompiler.compile(registry).getTemplateFactory("ns.foo");
  }
}
