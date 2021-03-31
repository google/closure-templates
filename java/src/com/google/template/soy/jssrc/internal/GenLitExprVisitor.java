/*
 * Copyright 2021 Google Inc.
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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.jssrc.dsl.SoyJsPluginUtils;
import com.google.template.soy.jssrc.dsl.TaggedTemplateLiteral;
import com.google.template.soy.jssrc.dsl.TemplateLiteral;
import com.google.template.soy.jssrc.restricted.SoyJsSrcPrintDirective;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.TemplateNode;
import java.util.ArrayList;
import java.util.List;

/** Visitor for generating a lit-html expression for a soy template body. */
public class GenLitExprVisitor extends AbstractSoyNodeVisitor<List<Expression>> {

  /** Injectable factory for creating an instance of this class. */
  public static class GenLitExprVisitorFactory {
    protected final JavaScriptValueFactoryImpl javaScriptValueFactory;
    protected final IsComputableAsLitTemplateVisitor isComputableAsLitTemplateVisitor;

    protected GenLitExprVisitorFactory(
        JavaScriptValueFactoryImpl javaScriptValueFactory,
        IsComputableAsLitTemplateVisitor isComputableAsLitTemplateVisitor) {
      this.javaScriptValueFactory = javaScriptValueFactory;
      this.isComputableAsLitTemplateVisitor = isComputableAsLitTemplateVisitor;
    }

    /**
     * Creates an instance of the visitor.
     *
     * @param templateAliases A mapping for looking up the function name for a given fully qualified
     *     name.
     */
    public GenLitExprVisitor create(
        TranslationContext translationContext,
        TemplateAliases templateAliases,
        ErrorReporter errorReporter) {
      return new GenLitExprVisitor(
          javaScriptValueFactory,
          isComputableAsLitTemplateVisitor,
          translationContext,
          errorReporter,
          templateAliases);
    }
  }

  private static final SoyErrorKind UNKNOWN_SOY_JS_SRC_PRINT_DIRECTIVE =
      SoyErrorKind.of("Unknown SoyJsSrcPrintDirective ''{0}''.");

  protected final JavaScriptValueFactoryImpl javaScriptValueFactory;
  protected final IsComputableAsLitTemplateVisitor isComputableAsLitTemplateVisitor;

  protected final TranslationContext translationContext;
  protected final ErrorReporter errorReporter;

  /** List to collect the results. */
  protected List<Expression> chunks;

  /**
   * Used for looking up the local name for a given template call to a fully qualified template
   * name.
   */
  protected final TemplateAliases templateAliases;

  /**
   * @param isComputableAsLitTemplateVisitor The IsComputableAsLitTemplateVisitor used by this
   *     instance (when needed).
   * @param templateAliases A mapping for looking up the function name for a given fully qualified
   *     name.
   */
  protected GenLitExprVisitor(
      JavaScriptValueFactoryImpl javaScriptValueFactory,
      IsComputableAsLitTemplateVisitor isComputableAsLitTemplateVisitor,
      TranslationContext translationContext,
      ErrorReporter errorReporter,
      TemplateAliases templateAliases) {
    this.javaScriptValueFactory = javaScriptValueFactory;
    this.isComputableAsLitTemplateVisitor = isComputableAsLitTemplateVisitor;

    this.translationContext = translationContext;
    this.errorReporter = errorReporter;
    this.templateAliases = templateAliases;
  }

  @Override
  public List<Expression> exec(SoyNode node) {
    Preconditions.checkArgument(isComputableAsLitTemplateVisitor.exec(node));
    chunks = new ArrayList<>();
    visit(node);
    return chunks;
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.

  /** List to collect results for the current template. */
  protected List<String> textParts;

  protected List<Expression> interpolatedParts;

  @Override
  protected void visitTemplateNode(TemplateNode node) {
    // TODO(user): Create a data structure that orders the parts in alternating
    // text-interpolated-text-interpolated-text order, and remove the hacks below.
    Preconditions.checkState(textParts == null);
    Preconditions.checkState(interpolatedParts == null);
    Preconditions.checkState(
        node.getContentKind() == SanitizedContentKind.TEXT
            || node.getContentKind() == SanitizedContentKind.HTML);

    textParts = new ArrayList<>();
    interpolatedParts = new ArrayList<>();

    visitChildren(node);
    // Hack to make textParts.size == interpolatedParts.size + 1
    if (textParts.size() == interpolatedParts.size()) {
      textParts.add("");
    }

    TemplateLiteral literal =
        TemplateLiteral.create(
            ImmutableList.copyOf(textParts), ImmutableList.copyOf(interpolatedParts));

    if (node.getContentKind() == SanitizedContentKind.TEXT) {
      chunks.add(literal);
    } else {
      chunks.add(TaggedTemplateLiteral.create(LitRuntime.HTML, literal));
    }

    textParts = null;
    interpolatedParts = null;
  }

  /**
   * Example:
   *
   * <pre>
   *   I'm feeling lucky!
   * </pre>
   *
   * generates the inside of the tagged template:
   *
   * <pre>
   *   html`I'm feeling lucky!`
   * </pre>
   */
  @Override
  protected void visitRawTextNode(RawTextNode node) {
    // TODO(user): make sure we're respecting the kind
    textParts.add(node.getRawText());
  }

  /**
   * Example:
   *
   * <pre>
   *   {$boo.foo}
   *   {$goo.moo + 5}
   * </pre>
   *
   * might generate the inside of the tagged template:
   *
   * <pre>
   *   html`${data.boo.foo}${data.goo.moo + 5}`
   * </pre>
   */
  @Override
  protected void visitPrintNode(PrintNode node) {
    Expression expr = translateExpr(node.getExpr());

    // Process directives.
    for (PrintDirectiveNode directiveNode : node.getChildren()) {

      // Get directive.
      SoyPrintDirective directive = directiveNode.getPrintDirective();
      if (!(directive instanceof SoyJsSrcPrintDirective)) {
        errorReporter.report(
            node.getSourceLocation(), UNKNOWN_SOY_JS_SRC_PRINT_DIRECTIVE, directiveNode.getName());
        return;
      }

      // Get directive args.
      List<ExprRootNode> argNodes = directiveNode.getArgs();

      // Convert args to CodeChunks.
      List<Expression> argChunks = new ArrayList<>(argNodes.size());
      for (ExprRootNode argNode : argNodes) {
        argChunks.add(translateExpr(argNode));
      }

      // Apply directive.
      expr =
          SoyJsPluginUtils.applyDirective(
              expr,
              (SoyJsSrcPrintDirective) directive,
              argChunks,
              node.getSourceLocation(),
              errorReporter);
    }

    // Hack to make textParts.size == interpolatedParts.size + 1
    if (textParts.size() == interpolatedParts.size()) {
      textParts.add("");
    }
    interpolatedParts.add(expr);
  }

  protected TranslateExprNodeVisitor getExprTranslator() {
    return new TranslateExprNodeVisitor(
        javaScriptValueFactory,
        translationContext,
        templateAliases,
        errorReporter,
        LitRuntime.DATA);
  }

  private Expression translateExpr(ExprRootNode argNode) {
    return getExprTranslator().exec(argNode);
  }
}
