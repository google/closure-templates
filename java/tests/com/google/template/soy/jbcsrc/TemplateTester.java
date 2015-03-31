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
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.internal.ParamStore;
import com.google.template.soy.jbcsrc.api.AdvisingStringBuilder;
import com.google.template.soy.jbcsrc.api.CompiledTemplate;
import com.google.template.soy.jbcsrc.api.RenderContext;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.soyparse.ParseResult;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateRegistry;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import javax.annotation.Nonnull;

/**
 * Utilities for testing compiled soy templates.
 */
public final class TemplateTester {
  private static final RenderContext EMPTY_CONTEXT = new RenderContext(
      EMPTY_DICT, SoyCssRenamingMap.IDENTITY, SoyCssRenamingMap.IDENTITY);

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
    return Truth.assertAbout(FACTORY).that(toTemplate(body));
  }

  static final class CompiledTemplateSubject extends Subject<CompiledTemplateSubject, String> {
    private Iterable<ClassData> classData;
    private CompiledTemplate.Factory factory;

    private CompiledTemplateSubject(FailureStrategy failureStrategy, String subject) {
      super(failureStrategy, subject);
    }
    
    CompiledTemplateSubject logsOutput(String expected) {
      return rendersAndLogs("", expected, EMPTY_DICT, EMPTY_CONTEXT);
    }

    CompiledTemplateSubject rendersAs(String expected) {
      return rendersAndLogs(expected, "", EMPTY_DICT, EMPTY_CONTEXT);
    }

    CompiledTemplateSubject rendersAs(String expected, SoyRecord params) {
      return rendersAndLogs(expected, "", params, EMPTY_CONTEXT);
    }
    
    CompiledTemplateSubject rendersAs(String expected, RenderContext context) {
      return rendersAndLogs(expected, "", EMPTY_DICT, context);
    }

    private CompiledTemplateSubject rendersAndLogs(String expectedOutput, String expectedLogged, 
        SoyRecord params, RenderContext context) {
      compile();
      CompiledTemplate template = factory.create(params);
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

    CompiledTemplateSubject hasCompiledTemplateFactoryClassName(String expectedFactoryClassName) {
      compile();
      if (!factory.getClass().getName().equals(expectedFactoryClassName)) {
        fail("hasCompiledTemplateFactoryClassName", expectedFactoryClassName);
      }
      return this;
    }

    CompiledTemplateSubject hasCompiledTemplateClassName(String expectedClassName) {
      compile();
      // Use this fake param store to make sure the constructor doesn't throw on missing parameters
      CompiledTemplate template = factory.create(new ParamStore() {
        @Override public boolean hasField(String name) {
          return true;
        }

        @Override public SoyValueProvider getFieldProvider(String name) {
          throw new UnsupportedOperationException();
        }

        @Override public void setField(String name, @Nonnull SoyValueProvider valueProvider) {
          throw new UnsupportedOperationException();
        }
      });
      if (!template.getClass().getName().equals(expectedClassName)) {
        fail("hasCompileTemplateClassName", expectedClassName);
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
        ParseResult<SoyFileSetNode> parseSoyFiles =
            SoyFileSetParserBuilder.forFileContents(getSubject()).parse();
        if (!parseSoyFiles.isSuccess()) {
          fail("parsed successfully", parseSoyFiles.getParseErrors());
        }
        // N.B. we are reproducing some of BytecodeCompiler here to make it easier to look at
        // intermediate data structures.
        SoyFileSetNode fileSet = parseSoyFiles.getParseTree();
        TemplateRegistry registry = new TemplateRegistry(fileSet);
        CompiledTemplateRegistry compilerRegistry = new CompiledTemplateRegistry(registry);
        String templateName = Iterables.getOnlyElement(registry.getBasicTemplatesMap().keySet());
        CompiledTemplateMetadata classInfo = compilerRegistry.getTemplateInfo(templateName);
        classData = new TemplateCompiler(classInfo).compile();
        factory = BytecodeCompiler.loadFactory(
            classInfo,
            new MemoryClassLoader.Builder().addAll(classData).build());
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

}
