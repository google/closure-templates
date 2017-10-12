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
import com.google.template.soy.soytree.AutoescapeMode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.TemplateNode;

/** Conformance rule banning templates from using deprecated escaping modes. */
final class RequireStrictAutoescaping extends Rule<SoyFileNode> {
  RequireStrictAutoescaping(SoyErrorKind error) {
    super(error);
  }

  @Override
  protected void doCheckConformance(SoyFileNode node, ErrorReporter errorReporter) {
    // Make sure none of the children have overridden the default.
    for (TemplateNode template : node.getChildren()) {
      if (template.getAutoescapeMode() != AutoescapeMode.STRICT) {
        errorReporter.report(template.getSourceLocation(), error);
      }
    }
  }
}
