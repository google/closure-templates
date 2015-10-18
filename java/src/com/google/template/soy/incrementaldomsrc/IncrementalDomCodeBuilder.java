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

package com.google.template.soy.incrementaldomsrc;

import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.JsExprUtils;
import com.google.template.soy.shared.internal.CodeBuilder;

import java.util.List;

/**
 * Used to generate code by printing {@link JsExpr}s into an output buffer.
 */
public final class IncrementalDomCodeBuilder extends CodeBuilder<JsExpr> {

  /** Used to track what kind of content is currently being processed. */
  private ContentKind contentKind;
  
  /**
   * Performs no action as there is no output variable to initialize.
   */
  @Override public void initOutputVarIfNecessary() {
    // NO-OP
  }

  /**
   * In Incremental DOM, the tags, attributes and print statements correspond to function calls
   * or arguments. Instead of being concatenated into an output variable, they are just emitted in
   * the correct location.
   * @param jsExprs A list of expressions that may correspond to function calls or parameters.
   */
  @Override public void addToOutputVar(List<? extends JsExpr> jsExprs) {
    if (getContentKind() == ContentKind.HTML || getContentKind() == ContentKind.ATTRIBUTES) {
      for (JsExpr jsExpr : jsExprs) {
        append(jsExpr.getText());
      }
    } else {
      appendLine(getOutputVarName(), " += ", JsExprUtils.concatJsExprs(jsExprs).getText(), ";");
    }
  }

  /**
   * Emits a series of {@link JsExpr}s at the current location in the generated code.
   * @param jsExprs A list of expressions that may correspond to function calls or parameters.
   */
  protected void addToOutput(List<? extends JsExpr> jsExprs) {
     append(JsExprUtils.concatJsExprs(jsExprs).getText());
  }

  /**
   * @param contentKind The current kind of content being processed.
   */
  public void setContentKind(ContentKind contentKind) {
    this.contentKind = contentKind;
  }

  /**
   * @return The current kind of content being processed.
   */
  public ContentKind getContentKind() {
    return contentKind;
  }
}
