/*
 * Copyright 2017 Google Inc.
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

package com.google.template.soy.conformance;

import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.SoyDocParam;
import com.google.template.soy.soytree.defn.TemplateParam;

/**
 * Conformance rule banning templates from declaring parameters in SoyDoc. (Parameters declared in
 * template headers using {@code {@param}} syntax can be more strongly typed.)
 *
 * @author brndn@google.com (Brendan Linn)
 */
public final class NoSoyDocParams extends Rule<TemplateNode> {

  public NoSoyDocParams(SoyErrorKind error) {
    super(error);
  }

  @Override
  public void checkConformance(TemplateNode node, ErrorReporter errorReporter) {
    for (TemplateParam param : node.getParams()) {
      if (param instanceof SoyDocParam) {
        errorReporter.report(node.getSourceLocation(), error);
      }
    }
  }
}
