/*
 * Copyright 2016 Google Inc.
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

package com.google.template.soy.jssrc.internal;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.ForOverride;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.SoyModule;
import com.google.template.soy.base.internal.UniqueNameGenerator;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.error.FormattingErrorReporter;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.dsl.CodeChunk.RequiresCollector;
import com.google.template.soy.shared.SharedTestUtils;
import com.google.template.soy.shared.SoyGeneralOptions;
import com.google.template.soy.shared.internal.GuiceSimpleScope;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.types.SoyTypeRegistry;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

/** Custom Truth subject to aid testing Soy->JS codegen. */
@CheckReturnValue
abstract class JsSrcSubject<T extends Subject<T, String>> extends Subject<T, String> {

  private static final Joiner JOINER = Joiner.on('\n');

  private static final SubjectFactory<ForFile, String> TEMPLATE_FACTORY =
      new SubjectFactory<ForFile, String>() {
        @Override
        public ForFile getSubject(FailureStrategy fs, String that) {
          return new ForFile(fs, that);
        }
      };

  private static final SubjectFactory<ForExprs, String> EXPR_FACTORY =
      new SubjectFactory<ForExprs, String>() {
        @Override
        public ForExprs getSubject(FailureStrategy fs, String that) {
          return new ForExprs(fs, that);
        }
      };

  private SoyGeneralOptions generalOptions = new SoyGeneralOptions().disableOptimizer();
  SoyJsSrcOptions jsSrcOptions = new SoyJsSrcOptions();
  private SoyTypeRegistry typeRegistry = new SoyTypeRegistry();
  ErrorReporter errorReporter = ExplodingErrorReporter.get();
  private final List<SoyFunction> soyFunctions = new ArrayList<>();
  private SyntaxVersion syntaxVersion = SyntaxVersion.V2_0;

  private JsSrcSubject(FailureStrategy failureStrategy, @Nullable String s) {
    super(failureStrategy, s);
  }

  static ForFile assertThatSoyFile(String... lines) {
    return assertAbout(TEMPLATE_FACTORY).that(JOINER.join(lines));
  }

  static ForFile assertThatTemplateBody(String... lines) {
    String templateBody = JOINER.join(lines);
    return assertAbout(TEMPLATE_FACTORY)
        .that(
            "{namespace ns autoescape=\"deprecated-noncontextual\"}\n"
                + "{template .aaa}\n"
                + templateBody
                + "{/template}\n");
  }

  /**
   * Allows callers to pass in an expression without param declarations, when the caller doesn't
   * care about the param types. (Each variable reference generates an untyped param declaration.)
   */
  static ForExprs assertThatSoyExpr(String... lines) {
    return assertThatSoyExpr(expr(lines));
  }

  static ForExprs assertThatSoyExpr(TestExpr build) {
    return assertAbout(EXPR_FACTORY).that(build.buildTemplateThatContainsOneExpression());
  }

  static TestExpr expr(String... lines) {
    return new TestExpr(JOINER.join(lines));
  }

  /** A utility for building an strongly typed expression. */
  static final class TestExpr {
    private final String exprText;
    private final StringBuilder paramDecls = new StringBuilder();

    private TestExpr(String exprText) {
      this.exprText = exprText;
    }

    TestExpr withParam(String param) {
      paramDecls.append(param).append('\n');
      return this;
    }

    TestExpr withParam(String name, String type) {
      paramDecls.append("{@param ").append(name).append(": ").append(type).append("}\n");
      return this;
    }

    private String buildTemplateThatContainsOneExpression() {
      String templateBody;
      if (paramDecls.length() == 0) {
        templateBody = SharedTestUtils.untypedTemplateBodyForExpression(exprText);
      } else {
        templateBody = paramDecls.toString() + "{" + exprText + "}";
      }
      return "{namespace ns autoescape=\"deprecated-noncontextual\"}\n"
          + "{template .aaa}\n"
          + templateBody
          + "\n{/template}";
    }
  }

  T withJsSrcOptions(SoyJsSrcOptions options) {
    this.jsSrcOptions = options;
    return typedThis();
  }

  T withTypeRegistry(SoyTypeRegistry typeRegistry) {
    this.typeRegistry = typeRegistry;
    return typedThis();
  }

  @CheckReturnValue
  T withDeclaredSyntaxVersion(SyntaxVersion version) {
    this.syntaxVersion = version;
    return typedThis();
  }

  @ForOverride
  abstract T typedThis();

  @ForOverride
  abstract void generateCode();

  private ParseResult parse() {
    SoyFileSetParserBuilder builder =
        SoyFileSetParserBuilder.forFileContents(actual())
            .allowUnboundGlobals(true)
            .declaredSyntaxVersion(syntaxVersion)
            .typeRegistry(typeRegistry)
            .options(generalOptions);
    for (SoyFunction soyFunction : soyFunctions) {
      builder.addSoyFunction(soyFunction);
    }
    ParseResult parse = builder.parse();
    // genjscodevisitor depends on this having been run
    new ExtractMsgVariablesVisitor().exec(parse.fileSet());
    return parse;
  }

  void causesErrors(String... expectedErrorMsgSubstrings) {
    FormattingErrorReporter formattingErrorReporter = new FormattingErrorReporter();
    this.errorReporter = formattingErrorReporter;

    generateCode();

    ImmutableList<String> errorMessages = formattingErrorReporter.getErrorMessages();
    assertThat(errorMessages).hasSize(expectedErrorMsgSubstrings.length);
    for (int i = 0; i < expectedErrorMsgSubstrings.length; ++i) {
      assertThat(errorMessages.get(i)).contains(expectedErrorMsgSubstrings[i]);
    }
  }

  /** Asserts on the contents of a generated soy file. */
  static final class ForFile extends JsSrcSubject<ForFile> {
    private static final Injector INJECTOR = Guice.createInjector(new SoyModule());
    private String file;
    private SoyFileNode fileNode;

    private ForFile(FailureStrategy fs, String expr) {
      super(fs, expr);
    }

    @Override
    void generateCode() {
      ParseResult parseResult = super.parse();
      try (GuiceSimpleScope.InScope inScope =
          JsSrcTestUtils.simulateNewApiCall(INJECTOR, jsSrcOptions)) {
        this.fileNode = parseResult.fileSet().getChild(0);
        this.file =
            INJECTOR
                .getInstance(GenJsCodeVisitor.class)
                .gen(parseResult.fileSet(), parseResult.registry(), errorReporter)
                .get(0);
      }
    }

    StringSubject generatesTemplateThat() {
      generateCode();
      if (fileNode.numChildren() != 1) {
        fail("expected to only have 1 template: " + fileNode.getChildren());
      }
      TemplateNode template = fileNode.getChild(0);
      // we know that 'file' contains exactly one template.  so find it.
      int functionIndex = file.indexOf("function(");
      int startOfFunction = file.substring(0, functionIndex).lastIndexOf('\n') + 1;
      int endOfFunction = file.lastIndexOf("}\n") + 2; //+2 to capture the \n

      // if it is a delegate function we want to include the registration code which is a single
      // statement after the end of the template
      if (template instanceof TemplateDelegateNode) {
        endOfFunction = file.indexOf(";\n", endOfFunction) + 2;
      }
      // if we are generating jsdoc we want to capture that too
      String templateBody;
      if (jsSrcOptions.shouldGenerateJsdoc()) {
        int startOfJsDoc = file.substring(0, startOfFunction).lastIndexOf("/**");
        templateBody = file.substring(startOfJsDoc, endOfFunction);
      } else {
        templateBody = file.substring(startOfFunction, endOfFunction);
      }
      return new StringSubject(badCodeStrategy(failureStrategy, "template body"), templateBody);
    }

    private FailureStrategy badCodeStrategy(final FailureStrategy delegate, final String type) {
      return new FailureStrategy() {
        private String prependMessage(String message) {
          return "Unexpected "
              + type
              + " generated for "
              + actual()
              + ":"
              + (message.isEmpty() ? "" : " " + message);
        }

        @Override
        public void fail(String message) {
          delegate.fail(prependMessage(message));
        }

        @Override
        public void fail(String message, Throwable cause) {
          delegate.fail(prependMessage(message), cause);
        }

        @Override
        public void failComparing(String message, CharSequence expected, CharSequence actual) {
          delegate.failComparing(prependMessage(message), expected, actual);
        }

        @Override
        public void failComparing(
            String message, CharSequence expected, CharSequence actual, Throwable cause) {
          delegate.failComparing(prependMessage(message), expected, actual, cause);
        }
      };
    }

    @Override
    ForFile typedThis() {
      return this;
    }
  }

  /** For asserting on the contents of a single soy expression. */
  static final class ForExprs extends JsSrcSubject<ForExprs> {
    private CodeChunk.WithValue chunk;

    private ForExprs(FailureStrategy fs, String templateThatContainsOneExpression) {
      super(fs, templateThatContainsOneExpression);
    }

    @Override
    void generateCode() {
      ParseResult parseResult = super.parse();
      List<PrintNode> printNodes =
          SoyTreeUtils.getAllNodesOfType(parseResult.fileSet().getChild(0), PrintNode.class);
      assertThat(printNodes).hasSize(1);

      ExprNode exprNode = printNodes.get(0).getExpr();
      UniqueNameGenerator nameGenerator = JsSrcNameGenerators.forLocalVariables();
      this.chunk =
          new TranslateExprNodeVisitor(
                  jsSrcOptions,
                  TranslationContext.of(
                      SoyToJsVariableMappings.create(nameGenerator),
                      CodeChunk.Generator.create(nameGenerator)),
                  errorReporter)
              .exec(exprNode);
    }

    @CanIgnoreReturnValue
    JsSrcSubject.ForExprs withPrecedence(Operator operator) {
      Preconditions.checkNotNull(this.chunk, "Call generatesCode() first.");

      assertThat(this.chunk.assertExprAndCollectRequires(RequiresCollector.NULL).getPrecedence())
          .isEqualTo(operator.getPrecedence());

      return this;
    }

    @CanIgnoreReturnValue
    ForExprs generatesCode(String... expectedLines) {
      generateCode();

      String expected = Joiner.on('\n').join(expectedLines);
      assertThat(chunk.getExpressionTestOnly()).isEqualTo(expected);

      return this;
    }

    @Override
    ForExprs typedThis() {
      return this;
    }
  }
}
