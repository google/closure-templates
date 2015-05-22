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
import com.google.common.collect.Iterables;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.common.truth.Truth;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.basicdirectives.BasicDirectivesModule;
import com.google.template.soy.basicfunctions.BasicFunctionsModule;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.SoyValueHelper;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.jbcsrc.api.AdvisingStringBuilder;
import com.google.template.soy.jbcsrc.api.CompiledTemplate;
import com.google.template.soy.jbcsrc.api.CompiledTemplate.Factory;
import com.google.template.soy.jbcsrc.api.CompiledTemplates;
import com.google.template.soy.jbcsrc.api.DelTemplateSelector;
import com.google.template.soy.jbcsrc.api.RenderContext;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.shared.internal.SharedModule;
import com.google.template.soy.shared.internal.SharedModule.Shared;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import com.google.template.soy.sharedpasses.SharedPassesModule;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.types.SoyTypeRegistry;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Utilities for testing compiled soy templates.
 */
public final class TemplateTester {
  private static final Injector INJECTOR =
      Guice.createInjector(
          new SharedModule(), 
          new SharedPassesModule(),
          new BasicDirectivesModule(), 
          new BasicFunctionsModule(), 
          new AbstractModule() {
            @Provides RenderContext provideContext(
                SoyValueHelper converter,
                @Shared Map<String, SoyJavaFunction> functions,
                @Shared Map<String, SoyJavaPrintDirective> printDirectives) {
              return new RenderContext.Builder()
                  .withSoyFunctions(functions)
                  .withSoyPrintDirectives(printDirectives)
                  .withConverter(converter)
                  .withTemplateSelector(new DelTemplateSelector() {
                    @Override public Factory selectDelTemplate(String calleeName, String variant,
                        boolean allowEmpty) {
                      throw new UnsupportedOperationException();
                    }
                  })
                  .build();
            }
            @Override protected void configure() {}
          });

  static final RenderContext DEFAULT_CONTEXT = INJECTOR.getInstance(RenderContext.class);

  private static final SubjectFactory<CompiledTemplateSubject, String> FACTORY =
      new SubjectFactory<CompiledTemplateSubject, String>() {
        @Override public CompiledTemplateSubject getSubject(FailureStrategy fs, String that) {
          return new CompiledTemplateSubject(fs, that);
        }
      };

  /**
   * Returns a truth subject that can be used to assert on an template given the template body.
   * 
   * <p>The given body lines are wrapped in a template called {@code ns.foo} that has no params.
   */
  public static CompiledTemplateSubject assertThatTemplateBody(String ...body) {
    String template = toTemplate(body);
    return assertThatFile(template);
  }

  static CompiledTemplateSubject assertThatFile(String ...template) {
    return Truth.assertAbout(FACTORY).that(Joiner.on('\n').join(template));
  }

  /**
   * Returns a {@link com.google.template.soy.jbcsrc.api.CompiledTemplate.Factory} for the given 
   * template body.
   */
  public static CompiledTemplate.Factory compileTemplateBody(String ...body) {
    // a little hacky to use the subject to do this...
    CompiledTemplateSubject that = Truth.assertAbout(FACTORY).that(toTemplate(body));
    that.compile();
    return that.factory;
  }
  
  static SoyRecord asRecord(Map<String, ?> params) {
    return (SoyRecord) SoyValueHelper.UNCUSTOMIZED_INSTANCE.convert(params);
  }

  static final class CompiledTemplateSubject extends Subject<CompiledTemplateSubject, String> {
    private Iterable<ClassData> classData;
    private SoyTypeRegistry typeRegistry = new SoyTypeRegistry();
    private SoyValueConverter converter = SoyValueHelper.UNCUSTOMIZED_INSTANCE;
    private CompiledTemplate.Factory factory;

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
    
    CompiledTemplateSubject logsOutput(String expected) {
      return rendersAndLogs("", expected, EMPTY_DICT, EMPTY_DICT, DEFAULT_CONTEXT);
    }

    CompiledTemplateSubject rendersAs(String expected) {
      return rendersAndLogs(expected, "", EMPTY_DICT, EMPTY_DICT, DEFAULT_CONTEXT);
    }
    
    CompiledTemplateSubject rendersAs(String expected, Map<String, ?> params) {
      return rendersAndLogs(expected, "", asRecord(params), EMPTY_DICT, DEFAULT_CONTEXT);
    }

    CompiledTemplateSubject rendersAs(
        String expected, Map<String, ?> params, RenderContext context) {
      return rendersAndLogs(expected, "", asRecord(params), EMPTY_DICT, context);
    }
    
    CompiledTemplateSubject rendersAs(String expected, Map<String, ?> params,  Map<String, ?> ij) {
      return rendersAndLogs(expected, "", asRecord(params), asRecord(ij), DEFAULT_CONTEXT);
    }
    
    CompiledTemplateSubject rendersAs(String expected, RenderContext context) {
      return rendersAndLogs(expected, "", EMPTY_DICT, EMPTY_DICT, context);
    }

    private SoyRecord asRecord(Map<String, ?> params) {
      return (SoyRecord) converter.convert(params);
    }

    private CompiledTemplateSubject rendersAndLogs(String expectedOutput, String expectedLogged, 
        SoyRecord params, SoyRecord ij, RenderContext context) {
      compile();
      CompiledTemplate template = factory.create(params, ij);
      AdvisingStringBuilder builder = new AdvisingStringBuilder();
      LogCapturer logOutput = new LogCapturer();
      RenderResult result;
      try (SystemOutRestorer restorer = logOutput.enter()) {
        result = template.render(builder, context);
      } catch (IOException e) {
        // AdvisingStringBuilder doesn't throw IOE
        throw new AssertionError(e);
      }
      if (result.type() != RenderResult.Type.DONE) {
        fail("renders to completion", result);
      }
      String output = builder.toString();
      if (!output.equals(expectedOutput)) {
        failWithBadResults("rendersAs", expectedOutput, "renders as", output);
      }
      if (!expectedLogged.equals(logOutput.toString())) {
        failWithBadResults("logs", expectedLogged, "logs", logOutput.toString());
      }
      return this;
    }

    @Override protected String getDisplaySubject() {
      if (classData == null) {
        // hasn't been compiled yet.  just use the source text
        return super.getDisplaySubject();
      }

      String customName = super.internalCustomName();
      return (customName != null ? customName : "")
          + " (<\n" + getSubject() + "\n Compiled as: \n" 
          + Joiner.on('\n').join(classData) + "\n>)";
    }

    private void compile() {
      if (classData == null) {
        SoyFileSetNode fileSet = 
            SoyFileSetParserBuilder.forFileContents(getSubject())
                .typeRegistry(typeRegistry).parse();
        new UnsupportedFeatureReporter(ExplodingErrorReporter.get()).check(fileSet);
        // N.B. we are reproducing some of BytecodeCompiler here to make it easier to look at
        // intermediate data structures.
        TemplateRegistry registry = new TemplateRegistry(fileSet, ExplodingErrorReporter.get());
        CompiledTemplateRegistry compilerRegistry = new CompiledTemplateRegistry(registry);
        String templateName = Iterables.getOnlyElement(registry.getBasicTemplatesMap().keySet());
        CompiledTemplateMetadata classInfo = compilerRegistry.getTemplateInfo(templateName);
        classData = new TemplateCompiler(compilerRegistry, classInfo,
            ExplodingErrorReporter.get()).compile();
        checkClasses(classData);
        factory = BytecodeCompiler.loadFactory(
            classInfo,
            new MemoryClassLoader.Builder().addAll(classData).build());
      }
    }

    private static void checkClasses(Iterable<ClassData> classData2) {
      for (ClassData d : classData2) {
        d.checkClass();
      }
    }
  }

  private interface SystemOutRestorer extends AutoCloseable {
    @Override public void close();
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
        @Override public void close() {
          System.setOut(prevStream);
        }
      };
    }

    @Override public String toString() {
      return new String(logOutput.toByteArray(), StandardCharsets.UTF_8);
    }
  }
  
  private static String toTemplate(String ...body) {
    StringBuilder builder = new StringBuilder();
    builder.append("{namespace ns autoescape=\"strict\"}\n")
        .append("{template .foo}\n");
    Joiner.on("\n").appendTo(builder, body);
    builder.append("\n{/template}\n");
    return builder.toString();
  }

  static CompiledTemplates compileFile(String ...fileBody) {
    String file = Joiner.on('\n').join(fileBody);
    return BytecodeCompiler.compile(
        new TemplateRegistry(SoyFileSetParserBuilder.forFileContents(file).parse(),
            ExplodingErrorReporter.get()));
  }
}
