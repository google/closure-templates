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
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Fact.simpleFact;
import static com.google.common.truth.Truth.assertAbout;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Joiner;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.Subject;
import com.google.common.truth.ThrowableSubject;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.protobuf.Descriptors.GenericDescriptor;
import com.google.template.soy.SoyFileSetParser;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.css.CssRegistry;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.LoggingAdvisingAppendable.BufferingAppendable;
import com.google.template.soy.data.SoyInjector;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.internal.ParamStore;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyError;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.jbcsrc.internal.ClassData;
import com.google.template.soy.jbcsrc.internal.MemoryClassLoader;
import com.google.template.soy.jbcsrc.shared.CompiledTemplate;
import com.google.template.soy.jbcsrc.shared.CompiledTemplates;
import com.google.template.soy.jbcsrc.shared.RenderContext;
import com.google.template.soy.jbcsrc.shared.StackFrame;
import com.google.template.soy.plugin.java.PluginInstances;
import com.google.template.soy.plugin.restricted.SoySourceFunction;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.shared.SoyIdRenamingMap;
import com.google.template.soy.shared.internal.InternalPlugins;
import com.google.template.soy.shared.internal.SoySimpleScope;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.FileSetMetadata;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateMetadata;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.SoyTypeRegistryBuilder;
import com.google.template.soy.types.TemplateType;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Utilities for testing compiled soy templates. */
public final class TemplateTester {

  private static RenderContext.Builder createDefaultBuilder(CompiledTemplates templates) {
    return new RenderContext.Builder(
        templates,
        InternalPlugins.internalDirectives(new SoySimpleScope()).stream()
            .filter(e -> e instanceof SoyJavaPrintDirective)
            .collect(toImmutableMap(SoyPrintDirective::getName, d -> (SoyJavaPrintDirective) d)),
        PluginInstances.empty());
  }

  static RenderContext getDefaultContext(CompiledTemplates templates) {
    return getDefaultContext(templates, arg -> false, /* debugSoyTemplateInfo= */ false);
  }

  static RenderContext getDefaultContext(
      CompiledTemplates templates, Predicate<String> activeMods) {
    return getDefaultContext(templates, activeMods, /* debugSoyTemplateInfo= */ false);
  }

  static RenderContext getDefaultContext(
      CompiledTemplates templates, Predicate<String> activeMods, boolean debugSoyTemplateInfo) {
    return createDefaultBuilder(templates)
        .withActiveModSelector(activeMods)
        .withDebugSoyTemplateInfo(debugSoyTemplateInfo)
        .build();
  }

  static RenderContext getDefaultContextWithDebugInfo(CompiledTemplates templates) {
    return getDefaultContext(templates, arg -> false, /* debugSoyTemplateInfo= */ true);
  }

  /**
   * Returns a truth subject that can be used to assert on an template given the template body.
   *
   * <p>The given body lines are wrapped in a template called {@code ns.foo} that has no params.
   */
  public static CompiledTemplateSubject assertThatTemplateBody(String... body) {
    String template = toTemplate(body);
    return assertThatFile(template);
  }

  /**
   * Returns a truth subject that can be used to assert on an element given the element body.
   *
   * <p>The given body lines are wrapped in a template called {@code ns.foo} that has no params.
   */
  public static CompiledTemplateSubject assertThatElementBody(String... body) {
    String template = toElement(body);
    return assertThatFile(template);
  }

  static CompiledTemplateSubject assertThatFile(String... template) {
    return assertAbout(CompiledTemplateSubject::new).that(Joiner.on('\n').join(template));
  }

  /**
   * Returns a {@link com.google.template.soy.jbcsrc.shared.CompiledTemplates} for the given
   * template body. Containing a single template {@code ns.foo} with the given body
   */
  public static CompiledTemplates compileTemplateBody(String... body) {
    return compileFile(toTemplate(body));
  }

  static SoyRecord asRecord(Map<String, ?> params) {
    return (SoyRecord) SoyValueConverter.INSTANCE.convert(params);
  }

  static ParamStore asParams(Map<String, ?> params) {
    return ParamStore.fromRecord(asRecord(params));
  }

  static final class CompiledTemplateSubject extends Subject {
    private final String actual;
    private final List<SoyFunction> soyFunctions = new ArrayList<>();
    private final List<SoySourceFunction> soySourceFunctions = new ArrayList<>();

    private Iterable<ClassData> classData;
    private CompiledTemplate template;
    private SoyTypeRegistry typeRegistry = SoyTypeRegistryBuilder.create();
    private ImmutableList<String> experimentalFeatures = ImmutableList.of();
    private SoyCssRenamingMap cssRenamingMap = SoyCssRenamingMap.EMPTY;
    private SoyIdRenamingMap xidRenamingMap = SoyCssRenamingMap.EMPTY;
    private RenderContext defaultContext;

    private CompiledTemplateSubject(FailureMetadata failureMetadata, String subject) {
      super(failureMetadata, subject);
      this.actual = subject;
    }

    @CanIgnoreReturnValue
    CompiledTemplateSubject withTypeRegistry(SoyTypeRegistry typeRegistry) {
      classData = null;
      template = null;
      this.typeRegistry = typeRegistry;
      return this;
    }

    @CanIgnoreReturnValue
    CompiledTemplateSubject withExperimentalFeatures(ImmutableList<String> experimentalFeatures) {
      this.experimentalFeatures = experimentalFeatures;
      return this;
    }

    @CanIgnoreReturnValue
    CompiledTemplateSubject withLegacySoyFunction(SoyFunction soyFunction) {
      classData = null;
      template = null;
      this.soyFunctions.add(checkNotNull(soyFunction));
      return this;
    }

    @CanIgnoreReturnValue
    CompiledTemplateSubject withSoySourceFunction(SoySourceFunction soySourceFunction) {
      classData = null;
      template = null;
      this.soySourceFunctions.add(checkNotNull(soySourceFunction));
      return this;
    }

    @CanIgnoreReturnValue
    CompiledTemplateSubject withCssRenamingMap(SoyCssRenamingMap renamingMap) {
      this.cssRenamingMap = renamingMap;
      return this;
    }

    @CanIgnoreReturnValue
    CompiledTemplateSubject withXidRenamingMap(SoyIdRenamingMap renamingMap) {
      this.xidRenamingMap = renamingMap;
      return this;
    }

    CompiledTemplateSubject logsOutput(String expected) {
      compile();
      return rendersAndLogs("", expected, ParamStore.EMPTY_INSTANCE, defaultContext);
    }

    CompiledTemplateSubject rendersAs(String expected) {
      compile();
      return rendersAndLogs(expected, "", ParamStore.EMPTY_INSTANCE, defaultContext);
    }

    CompiledTemplateSubject rendersAs(String expected, Map<String, ?> params) {
      compile();
      return rendersAndLogs(expected, "", asParams(params), defaultContext);
    }

    CompiledTemplateSubject rendersAs(String expected, Map<String, ?> params, Map<String, ?> ij) {
      compile();
      return rendersAndLogs(
          expected,
          "",
          asParams(params),
          defaultContext.toBuilder().withIj(SoyInjector.fromStringMap(ij)).build());
    }

    CompiledTemplateSubject failsToRenderWith(Class<? extends Throwable> expected) {
      return failsToRenderWith(expected, ImmutableMap.of());
    }

    @CanIgnoreReturnValue
    CompiledTemplateSubject failsToRenderWith(
        Class<? extends Throwable> expected, Map<String, ?> params) {
      BufferingAppendable builder = LoggingAdvisingAppendable.buffering();
      compile();
      try {
        var unused = template.render(null, asParams(params), builder, defaultContext);
        failWithoutActual(
            simpleFact(
                String.format(
                    "Expected %s to fail to render with a %s, but it rendered '%s'",
                    actual, expected, "")));
      } catch (Throwable t) {
        check("failure()").that(t).isInstanceOf(expected);
      }
      return this; // may be dead
    }

    @CheckReturnValue
    ThrowableSubject failsToRenderWithExceptionThat() {
      return failsToRenderWithExceptionThat(ImmutableMap.of());
    }

    @CheckReturnValue
    ThrowableSubject failsToRenderWithExceptionThat(Map<String, ?> params) {
      BufferingAppendable builder = LoggingAdvisingAppendable.buffering();
      compile();
      try {
        var unused = template.render(null, asParams(params), builder, defaultContext);
        failWithoutActual(
            simpleFact(
                String.format(
                    "Expected %s to fail to render, but it rendered '%s'.", actual, builder)));
      } catch (Throwable t) {
        return check("failure()").that(t);
      }
      throw new AssertionError("unreachable");
    }

    @CheckReturnValue
    public IterableSubject failsToCompileWithErrorsThat() {
      SoyFileSetParserBuilder builder = SoyFileSetParserBuilder.forFileContents(actual);
      for (SoyFunction function : soyFunctions) {
        builder.addSoyFunction(function);
      }
      builder.addSoySourceFunctions(soySourceFunctions);
      SoyFileSetParser parser =
          builder
              .typeRegistry(typeRegistry)
              .runOptimizer(true)
              .enableExperimentalFeatures(experimentalFeatures)
              .errorReporter(ErrorReporter.exploding())
              .build();
      ParseResult parseResult = parser.parse();
      ErrorReporter errors = ErrorReporter.create();
      Optional<CompiledTemplates> template =
          BytecodeCompiler.compile(
              parseResult.registry(),
              parseResult.fileSet(),
              errors,
              parser.soyFileSuppliers(),
              typeRegistry);
      if (template.isPresent()) {
        failWithoutActual(
            simpleFact(
                String.format(
                    "Expected %s to fail to compile, but it compiled successfully.", actual)));
      }
      return check("errors()").that(Lists.transform(errors.getErrors(), SoyError::message));
    }

    private ParamStore asParams(Map<String, ?> params) {
      return ParamStore.fromRecord(SoyValueConverter.INSTANCE.convert(params).resolve());
    }

    @CanIgnoreReturnValue
    private CompiledTemplateSubject rendersAndLogs(
        String expectedOutput, String expectedLogged, ParamStore params, RenderContext context) {
      BufferingAppendable builder = LoggingAdvisingAppendable.buffering();
      LogCapturer logOutput = new LogCapturer();
      StackFrame result;
      try (SystemOutRestorer restorer = logOutput.enter()) {
        result = template.render(null, params, builder, context);
      } catch (Throwable e) {
        // TODO(lukes): the fact that we are catching an exception means we have structured
        // this subject poorly.  The subject should be responsible for asserting, not actually
        // invoking the functionality under test.
        failWithCauseAndMessage(e, "template was not expected to throw an exception");
        result = null;
      }
      if (result != null) {
        failWithActual("expected to render to completion", result);
      }
      context.logDeferredErrors();

      check("render()").that(builder.toString()).isEqualTo(expectedOutput);
      check("logOutput()").that(logOutput.toString()).isEqualTo(expectedLogged);
      return this;
    }

    @Override
    protected String actualCustomStringRepresentation() {
      if (classData == null) {
        // hasn't been compiled yet.  just use the source text
        return actual;
      }

      return "(<\n" + actual + "\n Compiled as: \n" + Joiner.on('\n').join(classData) + "\n>)";
    }

    private void compile() {
      if (classData == null) {
        SoyFileSetParserBuilder builder = SoyFileSetParserBuilder.forFileContents(actual);
        for (SoyFunction function : soyFunctions) {
          builder.addSoyFunction(function);
        }
        builder.addSoySourceFunctions(soySourceFunctions);
        ParseResult parseResult =
            builder
                .typeRegistry(typeRegistry)
                .runOptimizer(true)
                .errorReporter(ErrorReporter.explodeOnErrorsAndIgnoreDeprecations())
                .enableExperimentalFeatures(experimentalFeatures)
                .parse();
        SoyFileSetNode fileSet = parseResult.fileSet();

        ImmutableMap.Builder<String, Supplier<Object>> pluginInstances = ImmutableMap.builder();
        for (FunctionNode fnNode : SoyTreeUtils.getAllNodesOfType(fileSet, FunctionNode.class)) {
          if (fnNode.getSoyFunction() instanceof SoyJavaFunction) {
            pluginInstances.put(
                fnNode.getFunctionName(), Suppliers.ofInstance(fnNode.getSoyFunction()));
          }
        }

        // N.B. we are reproducing some of BytecodeCompiler here to make it easier to look at
        // intermediate data structures.
        FileSetMetadata registry = parseResult.registry();

        SoyFileNode fileNode = fileSet.getChild(0);
        String templateName = fileNode.getTemplates().get(0).getTemplateName();
        classData =
            new SoyFileCompiler(
                    fileNode,
                    new JavaSourceFunctionCompiler(typeRegistry, ErrorReporter.exploding()),
                    registry)
                .compile();
        checkClasses(classData);
        CompiledTemplates compiledTemplates =
            new CompiledTemplates(
                /* delTemplateNames=*/ registry.getAllTemplates().stream()
                    .filter(
                        t ->
                            t.getTemplateType().getTemplateKind()
                                == TemplateType.TemplateKind.DELTEMPLATE)
                    .map(TemplateMetadata::getTemplateName)
                    .collect(toImmutableSet()),
                new MemoryClassLoader(classData));
        try {
          this.template = compiledTemplates.getTemplate(templateName);
        } catch (Error error) {
          throw new RuntimeException("Failed to load class: " + classData, error);
        }
        defaultContext =
            createDefaultBuilder(compiledTemplates)
                .withPluginInstances(PluginInstances.of(pluginInstances.buildOrThrow()))
                .withCssRenamingMap(cssRenamingMap)
                .withXidRenamingMap(xidRenamingMap)
                .build();
      }
    }

    private static void checkClasses(Iterable<ClassData> classData2) {
      for (ClassData d : classData2) {
        d.checkClass();
      }
    }

    /*
     * Hack to get Truth to include a given exception as the cause of the failure. It works by
     * letting us delegate to a new Subject whose value under test is the exception. Because that
     * makes the assertion "about" the exception, Truth includes it as a cause.
     */

    private void failWithCauseAndMessage(Throwable cause, String message) {
      check("thrownException()").about(UnexpectedFailureSubject::new).that(cause).doFail(message);
    }

    private static final class UnexpectedFailureSubject extends Subject {
      UnexpectedFailureSubject(FailureMetadata metadata, Throwable actual) {
        super(metadata, actual);
      }

      void doFail(String message) {
        failWithoutActual(simpleFact(message));
      }
    }
  }

  private interface SystemOutRestorer extends AutoCloseable {
    @Override
    void close();
  }

  private static final class LogCapturer {
    private final ByteArrayOutputStream logOutput;
    private final PrintStream stream;

    LogCapturer() {
      this.logOutput = new ByteArrayOutputStream();
      try {
        this.stream = new PrintStream(logOutput, true, UTF_8.name());
      } catch (UnsupportedEncodingException e) {
        throw new AssertionError("StandardCharsets must be supported", e);
      }
    }

    SystemOutRestorer enter() {
      PrintStream prevStream = System.out;
      System.setOut(stream);
      return () -> System.setOut(prevStream);
    }

    @Override
    public String toString() {
      return new String(logOutput.toByteArray(), UTF_8);
    }
  }

  private static String toTemplate(String... body) {
    StringBuilder builder = new StringBuilder();
    builder.append("{namespace ns}\n").append("{template foo}\n");
    Joiner.on("\n").appendTo(builder, body);
    builder.append("\n{/template}\n");
    return builder.toString();
  }

  private static String toElement(String... body) {
    StringBuilder builder = new StringBuilder();
    builder.append("{namespace ns}\n").append("{element foo}\n");
    Joiner.on("\n").appendTo(builder, body);
    builder.append("\n{/element}\n");
    return builder.toString();
  }

  static CompiledTemplates compileFile(String... fileBody) {
    return doCompileFileAndRunAutoescaper(false, fileBody);
  }

  static CompiledTemplates compileFileAndRunAutoescaper(String... fileBody) {
    return doCompileFileAndRunAutoescaper(true, fileBody);
  }

  static CompiledTemplates doCompileFileAndRunAutoescaper(
      boolean runAutoescaper, String... fileBody) {
    String file = Joiner.on('\n').join(fileBody);
    SoyFileSetParser parser =
        SoyFileSetParserBuilder.forFileContents(file)
            .runAutoescaper(runAutoescaper)
            .errorReporter(ErrorReporter.explodeOnErrorsAndIgnoreDeprecations())
            .build();
    ParseResult parseResult = parser.parse();
    return BytecodeCompiler.compile(
            parseResult.registry(),
            parseResult.fileSet(),
            ErrorReporter.explodeOnErrorsAndIgnoreDeprecations(),
            parser.soyFileSuppliers(),
            parser.typeRegistry())
        .get();
  }

  static CompiledTemplates compileFileWithImports(
      GenericDescriptor[] protoImports, String... fileBody) {
    String file = Joiner.on('\n').join(fileBody);
    SoyFileSetParser parser =
        SoyFileSetParserBuilder.forTemplateAndImports(file, protoImports)
            .build();
    ParseResult parseResult = parser.parse();
    return BytecodeCompiler.compile(
            parseResult.registry(),
            parseResult.fileSet(),
            ErrorReporter.exploding(),
            parser.soyFileSuppliers(),
            parser.typeRegistry())
        .get();
  }

  static CompiledTemplates compileFileWithCss(CssRegistry cssRegistry, String... fileBody) {
    String file = Joiner.on('\n').join(fileBody);
    SoyFileSetParser parser =
        SoyFileSetParserBuilder.forFileContents(file).cssRegistry(cssRegistry).build();
    ParseResult parseResult = parser.parse();
    return BytecodeCompiler.compile(
            parseResult.registry(),
            parseResult.fileSet(),
            ErrorReporter.exploding(),
            parser.soyFileSuppliers(),
            parser.typeRegistry())
        .get();
  }

  private TemplateTester() {}
}
