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

import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.jssrc.internal.JsCodeBuilder;
import com.google.template.soy.jssrc.restricted.JsExpr;
import java.util.List;

/** Used to generate code by printing {@link JsExpr}s into an output buffer. */
final class IncrementalDomCodeBuilder extends JsCodeBuilder {

  /** Used to track what kind of content is currently being processed. */
  private SanitizedContentKind contentKind;

  IncrementalDomCodeBuilder() {
    super();
  }

  IncrementalDomCodeBuilder(IncrementalDomCodeBuilder parent) {
    super(parent);
    this.contentKind = parent.getContentKind();
  }

  /**
   * Performs no action as there is no output variable to initialize.
   */
  @Override public void initOutputVarIfNecessary() {
    // NO-OP
  }

  @Override
  public IncrementalDomCodeBuilder addChunksToOutputVar(List<? extends Expression> codeChunks) {
    if (getContentKind().isHtml() || getContentKind() == SanitizedContentKind.ATTRIBUTES) {
      for (Expression chunk : codeChunks) {
        append(chunk);
      }
    } else {
      super.addChunksToOutputVar(codeChunks);
    }
    return this;
  }

  /** @param contentKind The current kind of content being processed. */
  void setContentKind(SanitizedContentKind contentKind) {
    this.contentKind = contentKind;
  }

  /** @return The current kind of content being processed. */
  SanitizedContentKind getContentKind() {
    return contentKind;
  }
}
