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
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.base.internal.UniqueNameGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.error.FormattingErrorReporter;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.shared.SharedTestUtils;
import com.google.template.soy.shared.SoyGeneralOptions;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.types.SoyTypeRegistry;
import java.util.List;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

/** Custom Truth subject to aid testing Soy->JS codegen. */
final class JsSrcSubject extends Subject<JsSrcSubject, String> {

  private static final Joiner JOINER = Joiner.on('\n');

  private static final SubjectFactory<JsSrcSubject, String> FACTORY =
      new SubjectFactory<JsSrcSubject, String>() {
        @Override
        public JsSrcSubject getSubject(FailureStrategy fs, String that) {
          return new JsSrcSubject(fs, that);
        }
      };

  private SoyGeneralOptions generalOptions = new SoyGeneralOptions();
  private SoyJsSrcOptions jsSrcOptions = new SoyJsSrcOptions();
  private ImmutableMap<String, CodeChunk.WithValue> initialLocalVarTranslations = ImmutableMap.of();
  private SoyTypeRegistry typeRegistry = new SoyTypeRegistry();
  private ErrorReporter errorReporter = ExplodingErrorReporter.get();

  // Set by call to generateChunk()
  @Nullable private CodeChunk.WithValue chunk;

  private JsSrcSubject(FailureStrategy failureStrategy, @Nullable String s) {
    super(failureStrategy, s);
  }

  @CheckReturnValue
  static JsSrcSubject assertThatTemplateBody(String... lines) {
    return assertAbout(FACTORY).that(JOINER.join(lines));
  }

  /**
   * Allows callers to pass in an expression without param declarations, when the caller doesn't
   * care about the param types. (Each variable reference generates an untyped param declaration.)
   */
  @CheckReturnValue
  static JsSrcSubject assertThatSoyExpr(String... lines) {
    String templateBody = SharedTestUtils.untypedTemplateBodyForExpression(JOINER.join(lines));
    return assertThatTemplateBody(templateBody);
  }

  @CheckReturnValue
  JsSrcSubject withJsSrcOptions(SoyJsSrcOptions options) {
    this.jsSrcOptions = options;
    return this;
  }

  @CheckReturnValue
  JsSrcSubject withGeneralOptions(SoyGeneralOptions generalOptions) {
    this.generalOptions = generalOptions;
    return this;
  }

  @CheckReturnValue
  JsSrcSubject withInitialLocalVarTranslations(
      ImmutableMap<String, CodeChunk.WithValue> initialLocalVarTranslations) {
    this.initialLocalVarTranslations = initialLocalVarTranslations;
    return this;
  }

  @CheckReturnValue
  JsSrcSubject withTypeRegistry(SoyTypeRegistry typeRegistry) {
    this.typeRegistry = typeRegistry;
    return this;
  }

  private void generateChunk() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                "{namespace ns autoescape=\"deprecated-noncontextual\"}\n"
                    + "/***/\n"
                    + "{template .aaa}\n"
                    + actual()
                    + "{/template}\n")
            .allowUnboundGlobals(true)
            .typeRegistry(typeRegistry)
            .options(generalOptions)
            .parse()
            .fileSet();
    List<PrintNode> printNodes = SoyTreeUtils.getAllNodesOfType(soyTree, PrintNode.class);
    assertThat(printNodes).hasSize(1);

    ExprNode exprNode = printNodes.get(0).getExprUnion().getExpr();
    UniqueNameGenerator nameGenerator = JsSrcNameGenerators.forLocalVariables();
    this.chunk =
        new TranslateExprNodeVisitor(
                jsSrcOptions,
                TranslationContext.of(
                    SoyToJsVariableMappings.startingWith(initialLocalVarTranslations),
                    CodeChunk.Generator.create(nameGenerator),
                    nameGenerator),
                errorReporter)
            .exec(exprNode);
  }

  @CanIgnoreReturnValue
  JsSrcSubject generatesCode(String... expectedLines) {
    generateChunk();

    String expected = Joiner.on('\n').join(expectedLines);
    assertThat(chunk.getExpressionTestOnly()).isEqualTo(expected);

    return this;
  }

  @CanIgnoreReturnValue
  JsSrcSubject withPrecedence(Operator operator) {
    Preconditions.checkNotNull(this.chunk, "Call generatesCode() first.");

    assertThat((this.chunk).assertExpr().getPrecedence()).isEqualTo(operator.getPrecedence());

    return this;
  }

  void causesErrors(String... expectedErrorMsgSubstrings) {
    FormattingErrorReporter errorReporter = new FormattingErrorReporter();
    this.errorReporter = errorReporter;

    // Throw the result away
    generateChunk();

    ImmutableList<String> errorMessages = errorReporter.getErrorMessages();
    assertThat(errorMessages).hasSize(expectedErrorMsgSubstrings.length);
    for (int i = 0; i < expectedErrorMsgSubstrings.length; ++i) {
      assertThat(errorMessages.get(i)).contains(expectedErrorMsgSubstrings[i]);
    }
  }
}
