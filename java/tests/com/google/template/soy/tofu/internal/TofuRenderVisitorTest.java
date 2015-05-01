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


package com.google.template.soy.tofu.internal;

import static com.google.common.truth.Truth.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.basicdirectives.BasicDirectivesModule;
import com.google.template.soy.basicfunctions.BasicFunctionsModule;
import com.google.template.soy.data.SoyValueHelper;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.shared.internal.SharedModule;
import com.google.template.soy.sharedpasses.SharedPassesModule;
import com.google.template.soy.sharedpasses.render.RenderVisitor;
import com.google.template.soy.sharedpasses.render.RenderVisitorFactory;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateRegistry;

import junit.framework.TestCase;

import java.util.Collections;

/**
 * Unit tests for TofuRenderVisitor.
 *
 */
public class TofuRenderVisitorTest extends TestCase {


  private static final Injector INJECTOR =
      Guice.createInjector(new SharedModule(), new SharedPassesModule(),
          new BasicDirectivesModule(), new BasicFunctionsModule());


  // TODO: Does this belong in RenderVisitorTest instead?
  public void testLetWithinParam() throws Exception {

    String soyFileContent = "" +
        "{namespace ns autoescape=\"deprecated-noncontextual\"}\n" +
        "\n" +
        "/***/\n" +
        "{template .callerTemplate}\n" +
        "  {call .calleeTemplate}\n" +
        "    {param boo}\n" +
        "      {let $foo: 'blah' /}\n" +
        "      {$foo}\n" +
        "    {/param}\n" +
        "  {/call}\n" +
        "{/template}\n" +
        "\n" +
        "/** @param boo */\n" +
        "{template .calleeTemplate}\n" +
        "  {$boo}\n" +
        "{/template}\n";

    ErrorReporter boom = ExplodingErrorReporter.get();
    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forFileContents(soyFileContent)
        .errorReporter(boom)
        .parse();
    TemplateRegistry templateRegistry = new TemplateRegistry(soyTree, boom);

    // Important: This test will be doing its intended job only if we run
    // MarkParentNodesNeedingEnvFramesVisitor, because otherwise the 'let' within the 'param' block
    // will add its var to the enclosing template's env frame.

    StringBuilder outputSb = new StringBuilder();
    RenderVisitor rv = INJECTOR.getInstance(RenderVisitorFactory.class).create(
        outputSb, templateRegistry, SoyValueHelper.EMPTY_DICT, null,
        Collections.<String>emptySet(), null, null, null);
    rv.exec(templateRegistry.getBasicTemplate("ns.callerTemplate"));

    assertThat(outputSb.toString()).isEqualTo("blah");
  }

}
