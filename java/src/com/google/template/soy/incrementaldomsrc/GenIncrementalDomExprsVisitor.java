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

package com.google.template.soy.incrementaldomsrc;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.internal.GenJsExprsVisitor;
import com.google.template.soy.jssrc.internal.TemplateAliases;
import com.google.template.soy.jssrc.internal.TranslationContext;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyNode;
import java.util.ArrayList;
import java.util.List;

/** Overrides the base class to provide the correct helpers classes. */
public final class GenIncrementalDomExprsVisitor extends GenJsExprsVisitor {

  /** Injectable factory for creating an instance of this class. */
  public static final class GenIncrementalDomExprsVisitorFactory extends GenJsExprsVisitorFactory {

    @SuppressWarnings("unchecked")
    GenIncrementalDomExprsVisitorFactory(
        SoyJsSrcOptions options,
        Supplier<IncrementalDomGenCallCodeUtils> genCallCodeUtils,
        IsComputableAsIncrementalDomExprsVisitor isComputableAsJsExprsVisitor) {
      super(options, (Supplier) genCallCodeUtils, isComputableAsJsExprsVisitor);
    }

    @Override
    public GenIncrementalDomExprsVisitor create(
        TranslationContext translationContext,
        TemplateAliases templateAliases,
        ErrorReporter errorReporter) {
      return new GenIncrementalDomExprsVisitor(
          options,
          (IncrementalDomGenCallCodeUtils) genCallCodeUtils.get(),
          (IsComputableAsIncrementalDomExprsVisitor) isComputableAsJsExprsVisitor,
          this,
          translationContext,
          errorReporter,
          templateAliases);
    }
  }

  public GenIncrementalDomExprsVisitor(
      SoyJsSrcOptions options,
      IncrementalDomGenCallCodeUtils genCallCodeUtils,
      IsComputableAsIncrementalDomExprsVisitor isComputableAsJsExprsVisitor,
      GenIncrementalDomExprsVisitorFactory genIncrementalDomExprsVisitorFactory,
      TranslationContext translationContext,
      ErrorReporter errorReporter,
      TemplateAliases templateAliases) {
    super(
        options,
        genCallCodeUtils,
        isComputableAsJsExprsVisitor,
        genIncrementalDomExprsVisitorFactory,
        translationContext,
        errorReporter,
        templateAliases);
  }

  @Override
  public List<CodeChunk.WithValue> exec(SoyNode node) {
    // HTML PrintNodes in idom are not directly computable as expressions. However, the idom codegen
    // emits them by wrapping the result of this visitor in an idom command statement, so we need to
    // skip this check for PrintNodes.
    Preconditions.checkArgument(
        node instanceof PrintNode || isComputableAsJsExprsVisitor.exec(node));
    chunks = new ArrayList<>();
    visit(node);
    return chunks;
  }
}
