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

package com.google.template.soy.idomsrc;

import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.jssrc.dsl.SourceMapHelper;
import com.google.template.soy.jssrc.internal.GenCallCodeUtils;
import com.google.template.soy.jssrc.internal.GenJsCodeVisitor.ScopedJsTypeRegistry;
import com.google.template.soy.jssrc.internal.GenJsExprsVisitor;
import com.google.template.soy.jssrc.internal.IsComputableAsJsExprsVisitor;
import com.google.template.soy.jssrc.internal.TemplateAliases;
import com.google.template.soy.jssrc.internal.TranslationContext;
import com.google.template.soy.jssrc.internal.VisitorsState;
import com.google.template.soy.soytree.SoyNode;
import java.util.ArrayList;
import java.util.List;

/** Overrides the base class to provide the correct helpers classes. */
public final class GenIdomExprsVisitor extends GenJsExprsVisitor {

  public GenIdomExprsVisitor(
      VisitorsState state,
      GenCallCodeUtils genCallCodeUtils,
      IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor,
      TranslationContext translationContext,
      ErrorReporter errorReporter,
      TemplateAliases templateAliases,
      ScopedJsTypeRegistry jsTypeRegistry,
      SourceMapHelper sourceMapHelper) {
    super(
        state,
        genCallCodeUtils,
        isComputableAsJsExprsVisitor,
        translationContext,
        errorReporter,
        templateAliases,
        jsTypeRegistry,
        sourceMapHelper);
  }

  @Override
  public List<Expression> exec(SoyNode node) {
    chunks = new ArrayList<>();
    visit(node);
    return chunks;
  }
}
