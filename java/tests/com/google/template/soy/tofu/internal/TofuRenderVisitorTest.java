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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.multibindings.Multibinder;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.basicdirectives.BasicDirectivesModule;
import com.google.template.soy.basicfunctions.BasicFunctionsModule;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.SoyValueHelper;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.shared.internal.ErrorReporterModule;
import com.google.template.soy.shared.internal.SharedModule;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.sharedpasses.SharedPassesModule;
import com.google.template.soy.sharedpasses.render.RenderVisitor;
import com.google.template.soy.sharedpasses.render.RenderVisitorFactory;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.tofu.restricted.SoyAbstractTofuFunction;
import com.google.template.soy.tofu.restricted.SoyTofuFunction;

import junit.framework.TestCase;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Unit tests for TofuRenderVisitor.
 *
 */
public class TofuRenderVisitorTest extends TestCase {

  private static final class Reverse extends SoyAbstractTofuFunction {

    static final SoyTofuFunction INSTANCE = new Reverse();

    @Override
    public SoyData compute(List<SoyData> args) {
      return StringData.forValue(
          new StringBuilder(Iterables.getOnlyElement(args).coerceToString())
              .reverse()
              .toString());
    }

    @Override
    public String getName() {
      return "reverse";
    }

    @Override
    public Set<Integer> getValidArgsSizes() {
      return ImmutableSet.of(1);
    }
  }

  private static final Injector INJECTOR = Guice.createInjector(
      new ErrorReporterModule(),
      new SharedModule(),
      new SharedPassesModule(),
      new BasicDirectivesModule(),
      new BasicFunctionsModule(),
      new AbstractModule() {
        @Override
        protected void configure() {
          Multibinder.newSetBinder(binder(), SoyFunction.class).addBinding().to(Reverse.class);
        }
      });

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

    TemplateRegistry templateRegistry =
        SoyFileSetParserBuilder.forFileContents(soyFileContent)
            .parse()
            .registry();

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

  // Regression test covering rollback of cl/101592053.
  public void testSoyTofuFunction() {
    String soyFileContent =
        "{namespace ns autoescape=\"strict\"}\n"
            + "/***/\n"
            + "{template .foo kind=\"html\"}\n"
            + "  {reverse('hello')}\n"
            + "{/template}\n";

    ParseResult result = SoyFileSetParserBuilder.forFileContents(soyFileContent)
        .soyFunctionMap(
            ImmutableMap.<String, SoyFunction>of(Reverse.INSTANCE.getName(), Reverse.INSTANCE))
        .parse();
    TemplateRegistry registry = result.registry();

    StringBuilder out = new StringBuilder();
    RenderVisitor rv = INJECTOR.getInstance(RenderVisitorFactory.class).create(
        out,
        registry,
        SoyValueHelper.EMPTY_DICT,
        null /* ijData */,
        null /* activeDelPackageNames */,
        null /* msgBundle */,
        null /* xidRenamingMap */,
        null /* cssRenamingMap */);
    rv.exec(registry.getBasicTemplate("ns.foo"));
    assertThat(out.toString()).isEqualTo("olleh");
  }
}
