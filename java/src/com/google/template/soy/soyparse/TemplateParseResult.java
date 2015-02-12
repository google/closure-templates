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

package com.google.template.soy.soyparse;

import com.google.common.collect.ImmutableCollection;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.TemplateNodeBuilder.DeclInfo;

import java.util.List;

/**
 * Container for the result of parsing a Soy template.
 *
 * @author brndn@google.com (Brendan Linn)
 */
public final class TemplateParseResult {
  private final List<DeclInfo> headerDecls;
  private final List<StandaloneNode> bodyNodes;
  private final ImmutableCollection<? extends SoySyntaxException> parseErrors;

  TemplateParseResult(
      List<DeclInfo> headerDecls,
      List<StandaloneNode> bodyNodes,
      ImmutableCollection<? extends SoySyntaxException> parseErrors) {
    this.headerDecls = headerDecls;
    this.bodyNodes = bodyNodes;
    this.parseErrors = parseErrors;
  }

  public List<DeclInfo> getHeaderDecls() {
    return headerDecls;
  }

  public List<StandaloneNode> getBodyNodes() {
    return bodyNodes;
  }

  public ImmutableCollection<? extends SoySyntaxException> getParseErrors() {
    return parseErrors;
  }

  public boolean isSuccess() {
    return parseErrors.isEmpty();
  }
}
