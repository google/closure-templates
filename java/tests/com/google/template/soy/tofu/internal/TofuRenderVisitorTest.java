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
import com.google.template.soy.SoyFileSet;
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
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import com.google.template.soy.shared.restricted.SoyJavaRuntimeFunction;
import com.google.template.soy.shared.restricted.SoyJavaRuntimePrintDirective;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.sharedpasses.SharedPassesModule;
import com.google.template.soy.sharedpasses.render.RenderVisitor;
import com.google.template.soy.sharedpasses.render.RenderVisitorFactory;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.tofu.restricted.SoyAbstractTofuFunction;
import com.google.template.soy.tofu.restricted.SoyAbstractTofuPrintDirective;
import com.google.template.soy.tofu.restricted.SoyTofuFunction;
import com.google.template.soy.tofu.restricted.SoyTofuPrintDirective;

import junit.framework.TestCase;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Unit tests for TofuRenderVisitor.
 *
 */
public class TofuRenderVisitorTest extends TestCase {

  // These three reverse plugins have identical behavior. They differ only in their
  // class hierarchies (which, unfortunately, is important to test, because
  // the legacy SoyTofuFunction and SoyJavaRuntimeFunction have to be adapted to the canonical
  // SoyJavaFunction).

  private static final class Reverse1 extends SoyAbstractTofuFunction {

    static final SoyFunction INSTANCE = new Reverse1();

    @Override
    public SoyData compute(List<SoyData> args) {
      return StringData.forValue(
          new StringBuilder(Iterables.getOnlyElement(args).coerceToString())
              .reverse()
              .toString());
    }

    @Override
    public String getName() {
      return "reverse1";
    }

    @Override
    public Set<Integer> getValidArgsSizes() {
      return ImmutableSet.of(1);
    }
  }

  private static final class Reverse2 implements SoyJavaRuntimeFunction {

    static final SoyFunction INSTANCE = new Reverse2();

    @Override
    public SoyData compute(List<SoyData> args) {
      return StringData.forValue(
          new StringBuilder(Iterables.getOnlyElement(args).coerceToString())
          .reverse()
          .toString());
    }

    @Override
    public String getName() {
      return "reverse2";
    }

    @Override
    public Set<Integer> getValidArgsSizes() {
      return ImmutableSet.of(1);
    }
  }

  private static final class Reverse3 implements SoyTofuFunction {

    static final SoyFunction INSTANCE = new Reverse3();

    @Override
    public SoyData computeForTofu(List<SoyData> args) {
      return StringData.forValue(
          new StringBuilder(Iterables.getOnlyElement(args).coerceToString())
              .reverse()
              .toString());
    }

    @Override
    public String getName() {
      return "reverse3";
    }

    @Override
    public Set<Integer> getValidArgsSizes() {
      return ImmutableSet.of(1);
    }
  }

  // These three caps plugins have identical behavior. They differ only in their
  // class hierarchies (which, unfortunately, is important to test, because
  // the legacy SoyTofuPrintDirective and SoyJavaRuntimePrintDirective have to be adapted
  // to the canonical SoyJavaPrintDirective).

  private static final class Caps1 extends SoyAbstractTofuPrintDirective {

    static final SoyPrintDirective INSTANCE = new Caps1();

    @Override
    public SoyData apply(SoyData value, List<SoyData> args) {
      return StringData.forValue(value.coerceToString().toUpperCase());
    }

    @Override
    public String getName() {
      return "|caps1";
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

  private static final class Caps2 implements SoyJavaRuntimePrintDirective {

    static final SoyPrintDirective INSTANCE = new Caps2();

    @Override
    public SoyData apply(SoyData value, List<SoyData> args) {
      return StringData.forValue(value.coerceToString().toUpperCase());
    }

    @Override
    public String getName() {
      return "|caps2";
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

  private static final class Caps3 implements SoyTofuPrintDirective {

    static final SoyPrintDirective INSTANCE = new Caps3();

    @Override
    public SoyData applyForTofu(SoyData value, List<SoyData> args) {
      return StringData.forValue(value.coerceToString().toUpperCase());
    }

    @Override
    public String getName() {
      return "|caps3";
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

  private static final Injector INJECTOR = Guice.createInjector(
      new ErrorReporterModule(),
      new SharedModule(),
      new SharedPassesModule(),
      new BasicDirectivesModule(),
      new BasicFunctionsModule(),
      new AbstractModule() {
        @Override
        protected void configure() {
          Multibinder<SoyFunction> functionMultibinder =
              Multibinder.newSetBinder(binder(), SoyFunction.class);
          functionMultibinder.addBinding().to(Reverse1.class);
          functionMultibinder.addBinding().to(Reverse2.class);
          functionMultibinder.addBinding().to(Reverse3.class);

          Multibinder<SoyPrintDirective> directiveMultibinder =
              Multibinder.newSetBinder(binder(), SoyPrintDirective.class);
          directiveMultibinder.addBinding().to(Caps1.class);
          directiveMultibinder.addBinding().to(Caps2.class);
          directiveMultibinder.addBinding().to(Caps3.class);
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
        outputSb,
        templateRegistry,
        SoyValueHelper.EMPTY_DICT,
        null /* ijData */,
        Collections.<String>emptySet() /* activeDelPackageNames */,
        null /* msgBundle */,
        null /* xidRenamingMap */,
        null /* cssRenamingMap */);
    rv.exec(templateRegistry.getBasicTemplate("ns.callerTemplate"));

    assertThat(outputSb.toString()).isEqualTo("blah");
  }

  // Regression test covering rollback of cl/101592053.
  public void testTofuFunctions() {
    String soyFileContent =
        "{namespace ns autoescape=\"strict\"}\n"
            + "/***/\n"
            + "{template .foo kind=\"html\"}\n"
            + "  {reverse1('hello')}\n"
            + "  {reverse2('hello')}\n"
            + "  {reverse3('hello')}\n"
            + "{/template}\n";

    ParseResult result = SoyFileSetParserBuilder.forFileContents(soyFileContent)
        .soyFunctionMap(
            SoyFileSet.adaptSoyJavaRuntimeFunctionsAndSoyTofuFunctions(
                ImmutableMap.of(
                    Reverse1.INSTANCE.getName(), Reverse1.INSTANCE,
                    Reverse2.INSTANCE.getName(), Reverse2.INSTANCE,
                    Reverse3.INSTANCE.getName(), Reverse3.INSTANCE)))
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
    assertThat(out.toString()).isEqualTo("olleholleholleh");
  }

  public void testTofuPrintDirectives() {
    String soyFileContent =
        "{namespace ns autoescape=\"strict\"}\n"
        + "/***/\n"
        + "{template .foo kind=\"html\"}\n"
        + "  {'hello' |caps1}\n"
        + "  {'hello' |caps2}\n"
        + "  {'hello' |caps3}\n"
        + "{/template}\n";

    ImmutableMap<String, ? extends SoyJavaPrintDirective> printDirectives =
        SoyFileSet.adaptSoyJavaRuntimePrintDirectivesAndSoyTofuPrintDirectives(
            ImmutableMap.of(
                Caps1.INSTANCE.getName(), Caps1.INSTANCE,
                Caps2.INSTANCE.getName(), Caps2.INSTANCE,
                Caps3.INSTANCE.getName(), Caps3.INSTANCE));
    ParseResult result = SoyFileSetParserBuilder.forFileContents(soyFileContent).parse();
    TemplateRegistry registry = result.registry();
    StringBuilder out = new StringBuilder();
    RenderVisitor rv = INJECTOR.getInstance(TofuRenderVisitorFactory.class).create(
        out,
        registry,
        printDirectives,
        SoyValueHelper.EMPTY_DICT,
        null /* ijData */,
        null /* activeDelPackageNames */,
        null /* msgBundle */,
        null /* xidRenamingMap */,
        null /* cssRenamingMap */);
    rv.exec(registry.getBasicTemplate("ns.foo"));
    assertThat(out.toString()).isEqualTo("HELLOHELLOHELLO");
  }
}
