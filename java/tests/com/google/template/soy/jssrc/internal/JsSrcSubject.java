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
import com.google.common.collect.ImmutableMap;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.ForOverride;
import com.google.protobuf.Descriptors.GenericDescriptor;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.base.internal.UniqueNameGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyError;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.logging.ValidatedLoggingConfig;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.testing.SharedTestUtils;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import com.google.template.soy.types.SoyTypeRegistryBuilder;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

/** Custom Truth subject to aid testing Soy->JS codegen. */
@CheckReturnValue
abstract class JsSrcSubject<T extends Subject> extends Subject {

  private static final Joiner JOINER = Joiner.on('\n');

  private final String actual;
  SoyJsSrcOptions jsSrcOptions = new SoyJsSrcOptions();
  private GenericDescriptor[] protoDescriptors = new GenericDescriptor[0];
  private ValidatedLoggingConfig loggingConfig = ValidatedLoggingConfig.EMPTY;
  private ImmutableList<String> experimentalFeatures = ImmutableList.of();
  ErrorReporter errorReporter = ErrorReporter.exploding();
  private final List<SoyFunction> soyFunctions = new ArrayList<>();

  private JsSrcSubject(FailureMetadata failureMetadata, @Nullable String s) {
    super(failureMetadata, s);
    this.actual = s;
  }

  static ForFile assertThatSoyFile(String... lines) {
    return assertAbout(ForFile::new).that(JOINER.join(lines));
  }

  static ForFile assertThatTemplateBody(String... lines) {
    String templateBody = JOINER.join(lines);
    return assertAbout(ForFile::new).that("{template .aaa}\n" + templateBody + "{/template}\n");
  }

  /**
   * Allows callers to pass in an expression without param declarations, when the caller doesn't
   * care about the param types. (Each variable reference generates an untyped param declaration.)
   */
  static ForExprs assertThatSoyExpr(String... lines) {
    return assertThatSoyExpr(expr(lines));
  }

  static ForExprs assertThatSoyExpr(TestExpr build) {
    return assertAbout(ForExprs::new).that(build.buildTemplateThatContainsOneExpression());
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
      return "{template .aaa}\n" + templateBody + "\n{/template}";
    }
  }

  T withJsSrcOptions(SoyJsSrcOptions options) {
    this.jsSrcOptions = options;
    return typedThis();
  }

  T withProtoImports(GenericDescriptor[] descriptors) {
    this.protoDescriptors = descriptors;
    return typedThis();
  }

  T withLoggingConfig(ValidatedLoggingConfig loggingConfig) {
    this.loggingConfig = loggingConfig;
    return typedThis();
  }

  T withExperimentalFeatures(ImmutableList<String> experimetalFeatures) {
    this.experimentalFeatures = experimetalFeatures;
    return typedThis();
  }

  @ForOverride
  abstract T typedThis();

  @ForOverride
  abstract void generateCode();

  private ParseResult parse() {
    SoyFileSetParserBuilder builder =
        SoyFileSetParserBuilder.forTemplateAndImports(actual, protoDescriptors)
            .allowUnboundGlobals(true)
            .allowV1Expression(true)
            .setLoggingConfig(loggingConfig)
            .allowUnknownJsGlobals(true)
            .enableExperimentalFeatures(experimentalFeatures);
    for (SoyFunction soyFunction : soyFunctions) {
      builder.addSoyFunction(soyFunction);
    }
    ParseResult parse = builder.parse();
    // genjscodevisitor depends on this having been run
    return parse;
  }

  void causesErrors(String... expectedErrorMsgSubstrings) {
    ErrorReporter reporter = ErrorReporter.createForTest();
    this.errorReporter = reporter;

    generateCode();

    ImmutableList<SoyError> errors = reporter.getErrors();
    assertThat(errors).hasSize(expectedErrorMsgSubstrings.length);
    for (int i = 0; i < expectedErrorMsgSubstrings.length; ++i) {
      assertThat(errors.get(i).message()).contains(expectedErrorMsgSubstrings[i]);
    }
  }

  /** Asserts on the contents of a generated soy file. */
  static final class ForFile extends JsSrcSubject<ForFile> {
    private String file;
    private SoyFileNode fileNode;
    private final GenJsCodeVisitor visitor =
        JsSrcMain.createVisitor(
            jsSrcOptions,
            SoyTypeRegistryBuilder.create(),
            BidiGlobalDir.LTR,
            ErrorReporter.exploding());

    private ForFile(FailureMetadata failureMetadata, String expr) {
      super(failureMetadata, expr);
    }

    @Override
    void generateCode() {
      ParseResult parseResult = super.parse();
      this.fileNode = parseResult.fileSet().getChild(0);
      this.file = visitor.gen(parseResult.fileSet(), parseResult.registry(), errorReporter).get(0);
    }

    StringSubject generatesTemplateThat() {
      generateCode();
      check("parse().getChildren()").that(fileNode.getChildren()).hasSize(1);
      TemplateNode template = (TemplateNode) fileNode.getChild(0);
      // we know that 'file' contains exactly one template.  so find it.
      int functionIndex = file.indexOf("function(");
      int startOfFunction = file.substring(0, functionIndex).lastIndexOf('\n') + 1;
      int endOfFunction = file.lastIndexOf("}\n") + 2; // +2 to capture the \n

      // if it is a delegate function we want to include the registration code which is a single
      // statement after the end of the template
      if (template instanceof TemplateDelegateNode) {
        endOfFunction = file.indexOf(";\n", endOfFunction) + 2;
      }
      // if we are generating jsdoc we want to capture that too
      String templateBody;
        int startOfJsDoc = file.substring(0, startOfFunction).lastIndexOf("/**");
        templateBody = file.substring(startOfJsDoc, endOfFunction);
      return check("generatedTemplate()").that(templateBody);
    }

    @Override
    ForFile typedThis() {
      return this;
    }
  }

  /** For asserting on the contents of a single soy expression. */
  static final class ForExprs extends JsSrcSubject<ForExprs> {
    private Expression chunk;
    private ImmutableMap<String, Expression> initialLocalVarTranslations = ImmutableMap.of();

    private ForExprs(FailureMetadata failureMetadata, String templateThatContainsOneExpression) {
      super(failureMetadata, templateThatContainsOneExpression);
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
                  new JavaScriptValueFactoryImpl(BidiGlobalDir.LTR, ErrorReporter.exploding()),
                  TranslationContext.of(
                      SoyToJsVariableMappings.startingWith(initialLocalVarTranslations),
                      CodeChunk.Generator.create(nameGenerator),
                      nameGenerator),
                  AliasUtils.createTemplateAliases(parseResult.fileSet().getChild(0)),
                  errorReporter)
              .exec(exprNode);
    }

    JsSrcSubject.ForExprs withInitialLocalVarTranslations(
        ImmutableMap<String, Expression> initialLocalVarTranslations) {
      this.initialLocalVarTranslations = initialLocalVarTranslations;
      return this;
    }

    @CanIgnoreReturnValue
    JsSrcSubject.ForExprs withPrecedence(Operator operator) {
      Preconditions.checkNotNull(this.chunk, "Call generatesCode() first.");

      assertThat(this.chunk.assertExprAndCollectRequires(r -> {}).getPrecedence())
          .isEqualTo(operator.getPrecedence());

      return this;
    }

    @CanIgnoreReturnValue
    ForExprs generatesCode(String... expectedLines) {
      generateCode();

      String expected = Joiner.on('\n').join(expectedLines);
      assertThat(chunk.getCode()).isEqualTo(expected);

      return this;
    }

    @Override
    ForExprs typedThis() {
      return this;
    }
  }
}
