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
 * Test-only variant of {@link TemplateParseResult} that contains the errors associated with parsing
 * a template as a convenience. (In non-test code, all errors are reported to an
 * {@link com.google.template.soy.error.ErrorReporter}, and it's unnecessary to know
 * the errors associated with a particular template.)
 *
 * @author brndn@google.com (Brendan Linn)
 */
final class TestTemplateParseResult {
  private final TemplateParseResult underlying;
  private final ImmutableCollection<? extends SoySyntaxException> parseErrors;

  TestTemplateParseResult(TemplateParseResult underlying,
      ImmutableCollection<? extends SoySyntaxException> parseErrors) {
    this.underlying = underlying;
    this.parseErrors = parseErrors;
  }

  List<DeclInfo> getHeaderDecls() {
    return underlying.getHeaderDecls();
  }

  List<StandaloneNode> getBodyNodes() {
    return underlying.getBodyNodes();
  }

  ImmutableCollection<? extends SoySyntaxException> getParseErrors() {
    return parseErrors;
  }

  boolean isSuccess() {
    return parseErrors.isEmpty();
  }
}
