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

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.multibindings.Multibinder;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.SoyModule;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.shared.internal.FunctionAdapters;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.sharedpasses.render.RenderVisitor;
import com.google.template.soy.sharedpasses.render.RenderVisitorFactory;
import com.google.template.soy.soytree.TemplateRegistry;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for TofuRenderVisitor.
 *
 */
@RunWith(JUnit4.class)
public class TofuRenderVisitorTest {

  private static final class Reverse implements SoyJavaFunction {

    static final SoyJavaFunction INSTANCE = new Reverse();

    @Override
    public SoyValue computeForJava(List<SoyValue> args) {
      return StringData.forValue(new StringBuilder(args.get(0).stringValue()).reverse().toString());
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

  private static final class Caps implements SoyJavaPrintDirective {

    static final SoyPrintDirective INSTANCE = new Caps();

    @Override
    public SoyValue applyForJava(SoyValue value, List<SoyValue> args) {
      return StringData.forValue(value.stringValue().toUpperCase());
    }

    @Override
    public String getName() {
      return "|caps";
    }

    @Override
    public Set<Integer> getValidArgsSizes() {
      return ImmutableSet.of(0);
    }

    @Override
    public boolean shouldCancelAutoescape() {
      return false;
    }
  }

  private static final Injector INJECTOR =
      Guice.createInjector(
          new SoyModule(),
          new AbstractModule() {
            @Override
            protected void configure() {
              Multibinder<SoyFunction> functionMultibinder =
                  Multibinder.newSetBinder(binder(), SoyFunction.class);
              functionMultibinder.addBinding().to(Reverse.class);
              Multibinder<SoyPrintDirective> directiveMultibinder =
                  Multibinder.newSetBinder(binder(), SoyPrintDirective.class);
              directiveMultibinder.addBinding().to(Caps.class);
            }
          });

  // TODO: Does this belong in RenderVisitorTest instead?
  @Test
  public void testLetWithinParam() throws Exception {

    String soyFileContent =
        ""
            + "{namespace ns autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/***/\n"
            + "{template .callerTemplate}\n"
            + "  {call .calleeTemplate}\n"
            + "    {param boo}\n"
            + "      {let $foo: 'blah' /}\n"
            + "      {$foo}\n"
            + "    {/param}\n"
            + "  {/call}\n"
            + "{/template}\n"
            + "\n"
            + "/** @param boo */\n"
            + "{template .calleeTemplate}\n"
            + "  {$boo}\n"
            + "{/template}\n";

    TemplateRegistry templateRegistry =
        SoyFileSetParserBuilder.forFileContents(soyFileContent).parse().registry();

    // Important: This test will be doing its intended job only if we run
    // MarkParentNodesNeedingEnvFramesVisitor, because otherwise the 'let' within the 'param' block
    // will add its var to the enclosing template's env frame.

    StringBuilder outputSb = new StringBuilder();
    RenderVisitor rv =
        INJECTOR
            .getInstance(RenderVisitorFactory.class)
            .create(
                outputSb,
                templateRegistry,
                SoyValueConverter.EMPTY_DICT,
                null /* ijData */,
                Predicates.<String>alwaysFalse() /* activeDelPackageSelector */,
                null /* msgBundle */,
                null /* xidRenamingMap */,
                null /* cssRenamingMap */);
    rv.exec(templateRegistry.getBasicTemplate("ns.callerTemplate"));

    assertThat(outputSb.toString()).isEqualTo("blah");
  }

  // Regression test covering rollback of cl/101592053.
  @Test
  public void testJavaFunctions() {
    String soyFileContent =
        "{namespace ns autoescape=\"strict\"}\n"
            + "/***/\n"
            + "{template .foo kind=\"html\"}\n"
            + "  {reverse('hello')}\n"
            + "{/template}\n";

    ParseResult result =
        SoyFileSetParserBuilder.forFileContents(soyFileContent)
            .addSoyFunction(Reverse.INSTANCE)
            .parse();
    TemplateRegistry registry = result.registry();

    StringBuilder out = new StringBuilder();
    RenderVisitor rv =
        INJECTOR
            .getInstance(RenderVisitorFactory.class)
            .create(
                out,
                registry,
                SoyValueConverter.EMPTY_DICT,
                null /* ijData */,
                null /* activeDelPackageNames */,
                null /* msgBundle */,
                null /* xidRenamingMap */,
                null /* cssRenamingMap */);
    rv.exec(registry.getBasicTemplate("ns.foo"));
    assertThat(out.toString()).isEqualTo("olleh");
  }

  @Test
  public void testTofuPrintDirectives() {
    String soyFileContent =
        "{namespace ns autoescape=\"strict\"}\n"
            + "/***/\n"
            + "{template .foo kind=\"html\"}\n"
            + "  {'hello' |caps}\n"
            + "{/template}\n";

    ImmutableMap<String, ? extends SoyJavaPrintDirective> printDirectives =
        FunctionAdapters.buildSpecificSoyDirectivesMap(
            ImmutableSet.of(Caps.INSTANCE), SoyJavaPrintDirective.class);
    ParseResult result = SoyFileSetParserBuilder.forFileContents(soyFileContent).parse();
    TemplateRegistry registry = result.registry();
    StringBuilder out = new StringBuilder();
    RenderVisitor rv =
        INJECTOR
            .getInstance(TofuRenderVisitorFactory.class)
            .create(
                out,
                registry,
                printDirectives,
                SoyValueConverter.EMPTY_DICT,
                SoyValueConverter.EMPTY_DICT /* ijData */,
                null /* activeDelPackageNames */,
                null /* msgBundle */,
                null /* xidRenamingMap */,
                null /* cssRenamingMap */);
    rv.exec(registry.getBasicTemplate("ns.foo"));
    assertThat(out.toString()).isEqualTo("HELLO");
  }
}
