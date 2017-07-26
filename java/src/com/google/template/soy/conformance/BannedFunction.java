/*
 * Copyright 2014 Google Inc.
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

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.FunctionNode;

/**
 * Conformance rule banning particular Soy functions (plausible example: {@code hasData}).
 *
 * @author brndn@google.com (Brendan Linn)
 */
public final class BannedFunction extends Rule<FunctionNode> {

  private final ImmutableSet<String> bannedFunctions;

  public BannedFunction(ImmutableSet<String> bannedFunctions, SoyErrorKind error) {
    super(error);
    this.bannedFunctions = bannedFunctions;
  }

  @Override
  public void checkConformance(FunctionNode node, ErrorReporter errorReporter) {
    String functionName = node.getFunctionName();
    if (bannedFunctions.contains(functionName)) {
      errorReporter.report(node.getSourceLocation(), error);
    }
  }
}
