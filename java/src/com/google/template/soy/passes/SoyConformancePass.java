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

package com.google.template.soy.passes;

import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.conformance.SoyConformance;
import com.google.template.soy.conformance.ValidatedConformanceConfig;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.soytree.SoyFileNode;

/** A compiler pass to run {@link SoyConformance}. */
public final class SoyConformancePass extends CompilerFilePass {
  private final SoyConformance conformance;
  private final ErrorReporter errorReporter;

  SoyConformancePass(ValidatedConformanceConfig conformanceConfig, ErrorReporter errorReporter) {
    this.conformance = SoyConformance.create(conformanceConfig);
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    conformance.check(file, errorReporter);
  }
}
