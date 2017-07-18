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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.template.soy.data.SoyValueConverter.EMPTY_DICT;

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.common.truth.Truth;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.SoyModule;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.jbcsrc.api.AdvisingStringBuilder;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.jbcsrc.shared.CompiledTemplate;
import com.google.template.soy.jbcsrc.shared.CompiledTemplates;
import com.google.template.soy.jbcsrc.shared.RenderContext;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.shared.SoyGeneralOptions;
import com.google.template.soy.shared.SoyIdRenamingMap;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.types.SoyTypeRegistry;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Utilities for testing compiled soy templates. */
public final class TemplateTester {
  private static final Injector INJECTOR =
      Guice.createInjector(
          new SoyModule(),
          new AbstractModule() {
            @Provides
            RenderContext.Builder provideContext(
                ImmutableMap<String, ? extends SoyFunction> functions,
                SoyValueConverter converter,
                ImmutableMap<String, ? extends SoyJavaPrintDirective> printDirectives) {
              @SuppressWarnings("unchecked")
              ImmutableMap<String, SoyJavaFunction> soyJavaFunctions =
                  ImmutableMap.copyOf(
                      (Map<String, SoyJavaFunction>)
                          Maps.filterValues(
                              functions, Predicates.instanceOf(SoyJavaFunction.class)));
              return new RenderContext.Builder()
                  .withSoyFunctions(soyJavaFunctions)
                  .withSoyPrintDirectives(printDirectives);
            }

            @Override
            protected void configure() {}
          });

  static final Provider<RenderContext.Builder> DEFAULT_CONTEXT_BUILDER =
      INJECTOR.getProvider(RenderContext.Builder.class);

  static RenderContext getDefaultContext(CompiledTemplates templates) {
    return getDefaultContext(
        templates, Predicates.<String>alwaysFalse(), /* debugSoyTemplateInfo= */ false);
  }

  static RenderContext getDefaultContext(
      CompiledTemplates templates, Predicate<String> activeDelPackages) {
    return getDefaultContext(templates, activeDelPackages, /* debugSoyTemplateInfo= */ false);
  }

  static RenderContext getDefaultContext(
      CompiledTemplates templates,
      Predicate<String> activeDelPackages,
      boolean debugSoyTemplateInfo) {
    return DEFAULT_CONTEXT_BUILDER
        .get()
        .withActiveDelPackageSelector(activeDelPackages)
        .withCompiledTemplates(templates)
        .withDebugSoyTemplateInfo(debugSoyTemplateInfo)
        .build();
  }

  static RenderContext getDefaultContextWithDebugInfo(CompiledTemplates templates) {
    return getDefaultContext(
        templates, Predicates.<String>alwaysFalse(), /* debugSoyTemplateInfo= */ true);
  }

  private static final SubjectFactory<CompiledTemplateSubject, String> FACTORY =
      new SubjectFactory<CompiledTemplateSubject, String>() {
        @Override
        public CompiledTemplateSubject getSubject(FailureStrategy fs, String that) {
          return new CompiledTemplateSubject(fs, that);
        }
      };

  /**
   * Returns a truth subject that can be used to assert on an template given the template body.
   *
   * <p>The given body lines are wrapped in a template called {@code ns.foo} that has no params.
   */
  public static CompiledTemplateSubject assertThatTemplateBody(String... body) {
    String template = toTemplate(body);
    return assertThatFile(template);
  }

  static CompiledTemplateSubject assertThatFile(String... template) {
    return Truth.assertAbout(FACTORY).that(Joiner.on('\n').join(template));
  }

  /**
   * Returns a {@link com.google.template.soy.jbcsrc.shared.CompiledTemplates} for the given
   * template body. Containing a single template {@code ns.foo} with the given body
   */
  public static CompiledTemplates compileTemplateBody(String... body) {
    return compileFile(toTemplate(body));
  }

  static SoyRecord asRecord(Map<String, ?> params) {
    return (SoyRecord) SoyValueConverter.UNCUSTOMIZED_INSTANCE.convert(params);
  }

  static final class CompiledTemplateSubject extends Subject<CompiledTemplateSubject, String> {
    private final List<SoyFunction> soyFunctions = new ArrayList<>();
    private final RenderContext.Builder defaultContextBuilder = DEFAULT_CONTEXT_BUILDER.get();

    private Iterable<ClassData> classData;
    private CompiledTemplate.Factory factory;
    private SoyTypeRegistry typeRegistry = new SoyTypeRegistry();
    private SoyValueConverter converter = SoyValueConverter.UNCUSTOMIZED_INSTANCE;
    private SoyGeneralOptions generalOptions = new SoyGeneralOptions();
    private RenderContext defaultContext;

    private CompiledTemplateSubject(FailureStrategy failureStrategy, String subject) {
      super(failureStrategy, subject);
    }

    CompiledTemplateSubject withTypeRegistry(SoyTypeRegistry typeRegistry) {
      classData = null;
      factory = null;
      this.typeRegistry = typeRegistry;
      return this;
    }

    CompiledTemplateSubject withValueConverter(SoyValueConverter converter) {
      classData = null;
      factory = null;
      this.converter = converter;
      return this;
    }

    CompiledTemplateSubject withSoyFunction(SoyFunction soyFunction) {
      classData = null;
      factory = null;
      this.soyFunctions.add(checkNotNull(soyFunction));
      return this;
    }

    CompiledTemplateSubject withGeneralOptions(SoyGeneralOptions options) {
      this.generalOptions = options;
      return this;
    }

    CompiledTemplateSubject withCssRenamingMap(SoyCssRenamingMap renamingMap) {
      this.defaultContextBuilder.withCssRenamingMap(renamingMap);
      return this;
    }

    CompiledTemplateSubject withXidRenamingMap(SoyIdRenamingMap renamingMap) {
      this.defaultContextBuilder.withXidRenamingMap(renamingMap);
      return this;
    }

    CompiledTemplateSubject logsOutput(String expected) {
      compile();
      return rendersAndLogs("", expected, EMPTY_DICT, EMPTY_DICT, defaultContext);
    }

    CompiledTemplateSubject rendersAs(String expected) {
      compile();
      return rendersAndLogs(expected, "", EMPTY_DICT, EMPTY_DICT, defaultContext);
    }

    CompiledTemplateSubject rendersAs(String expected, Map<String, ?> params) {
      compile();
      return rendersAndLogs(expected, "", asRecord(params), EMPTY_DICT, defaultContext);
    }

    CompiledTemplateSubject rendersAs(String expected, Map<String, ?> params, Map<String, ?> ij) {
      compile();
      return rendersAndLogs(expected, "", asRecord(params), asRecord(ij), defaultContext);
    }

    CompiledTemplateSubject failsToRenderWith(Class<? extends Throwable> expected) {
      return failsToRenderWith(expected, ImmutableMap.<String, Object>of());
    }

    CompiledTemplateSubject failsToRenderWith(
        Class<? extends Throwable> expected, Map<String, ?> params) {
      AdvisingStringBuilder builder = new AdvisingStringBuilder();
      compile();
      try {
        factory.create(asRecord(params), EMPTY_DICT).render(builder, defaultContext);
        failureStrategy.fail(
            String.format(
                "Expected %s to fail to render with a %s, but it rendered '%s'",
                actual(), expected, ""));
      } catch (Throwable t) {
        if (!expected.isInstance(t)) {
          failWithBadResults("failsToRenderWith", expected, "failed with", t);
        }
      }
      return this; // may be dead
    }

    private SoyRecord asRecord(Map<String, ?> params) {
      return (SoyRecord) converter.convert(params);
    }

    private CompiledTemplateSubject rendersAndLogs(
        String expectedOutput,
        String expectedLogged,
        SoyRecord params,
        SoyRecord ij,
        RenderContext context) {
      CompiledTemplate template = factory.create(params, ij);
      AdvisingStringBuilder builder = new AdvisingStringBuilder();
      LogCapturer logOutput = new LogCapturer();
      RenderResult result;
      try (SystemOutRestorer restorer = logOutput.enter()) {
        result = template.render(builder, context);
      } catch (Throwable e) {
        failureStrategy.fail(String.format("Unexpected failure for %s", getDisplaySubject()), e);
        result = null;
      }
      if (result.type() != RenderResult.Type.DONE) {
        fail("renders to completion", result);
      }

      String output = builder.toString();
      if (!output.equals(expectedOutput)) {
        failWithBadResults("renders as", expectedOutput, "renders as", output);
      }
      if (!expectedLogged.equals(logOutput.toString())) {
        failWithBadResults("logs", expectedLogged, "logs", logOutput.toString());
      }
      return this;
    }

    @Override
    protected String getDisplaySubject() {
      if (classData == null) {
        // hasn't been compiled yet.  just use the source text
        return actual();
      }

      String customName = super.internalCustomName();
      return (customName != null ? customName : "")
          + " (<\n"
          + actual()
          + "\n Compiled as: \n"
          + Joiner.on('\n').join(classData)
          + "\n>)";
    }

    private void compile() {
      if (classData == null) {
        SoyFileSetParserBuilder builder = SoyFileSetParserBuilder.forFileContents(actual());
        for (SoyFunction function : soyFunctions) {
          builder.addSoyFunction(function);
        }
        SoyFileSetNode fileSet =
            builder
                .typeRegistry(typeRegistry)
                .options(generalOptions)
                .errorReporter(ErrorReporter.exploding())
                .parse()
                .fileSet();
        new UnsupportedFeatureReporter(ErrorReporter.exploding()).check(fileSet);
        // Clone the tree, there tend to be bugs in the AST clone implementations that don't show
        // up until development time when we do a lot of AST cloning, so clone here to try to flush
        // them out.
        fileSet = SoyTreeUtils.cloneNode(fileSet);

        Map<String, SoyJavaFunction> functions = new LinkedHashMap<>();
        for (FunctionNode fnNode : SoyTreeUtils.getAllNodesOfType(fileSet, FunctionNode.class)) {
          if (fnNode.getSoyFunction() instanceof SoyJavaFunction) {
            functions.put(fnNode.getFunctionName(), (SoyJavaFunction) fnNode.getSoyFunction());
          }
        }

        // N.B. we are reproducing some of BytecodeCompiler here to make it easier to look at
        // intermediate data structures.
        TemplateRegistry registry = new TemplateRegistry(fileSet, ErrorReporter.exploding());
        CompiledTemplateRegistry compilerRegistry = new CompiledTemplateRegistry(registry);
        String templateName = Iterables.getOnlyElement(registry.getBasicTemplatesMap().keySet());
        classData =
            new TemplateCompiler(
                    compilerRegistry, compilerRegistry.getTemplateInfoByTemplateName(templateName))
                .compile();
        checkClasses(classData);
        CompiledTemplates compiledTemplates =
            new CompiledTemplates(
                compilerRegistry.getDelegateTemplateNames(), new MemoryClassLoader(classData));
        factory = compiledTemplates.getTemplateFactory(templateName);
        defaultContext =
            defaultContextBuilder
                .withCompiledTemplates(compiledTemplates)
                .withSoyFunctions(ImmutableMap.copyOf(functions))
                .withMessageBundle(SoyMsgBundle.EMPTY)
                .build();
      }
    }

    private static void checkClasses(Iterable<ClassData> classData2) {
      for (ClassData d : classData2) {
        d.checkClass();
      }
    }
  }

  private interface SystemOutRestorer extends AutoCloseable {
    @Override
    public void close();
  }

  private static final class LogCapturer {
    private final ByteArrayOutputStream logOutput;
    private final PrintStream stream;

    LogCapturer() {
      this.logOutput = new ByteArrayOutputStream();
      try {
        this.stream = new PrintStream(logOutput, true, StandardCharsets.UTF_8.name());
      } catch (UnsupportedEncodingException e) {
        throw new AssertionError("StandardCharsets must be supported", e);
      }
    }

    SystemOutRestorer enter() {
      final PrintStream prevStream = System.out;
      System.setOut(stream);
      return new SystemOutRestorer() {
        @Override
        public void close() {
          System.setOut(prevStream);
        }
      };
    }

    @Override
    public String toString() {
      return new String(logOutput.toByteArray(), StandardCharsets.UTF_8);
    }
  }

  private static String toTemplate(String... body) {
    StringBuilder builder = new StringBuilder();
    builder.append("{namespace ns autoescape=\"strict\"}\n").append("{template .foo}\n");
    Joiner.on("\n").appendTo(builder, body);
    builder.append("\n{/template}\n");
    return builder.toString();
  }

  static CompiledTemplates compileFile(String... fileBody) {
    String file = Joiner.on('\n').join(fileBody);
    return BytecodeCompiler.compile(
            SoyFileSetParserBuilder.forFileContents(file).parse().registry(),
            false,
            ErrorReporter.exploding())
        .get();
  }
}
