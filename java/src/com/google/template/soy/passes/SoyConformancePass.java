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

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.conformance.SoyConformance;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.soytree.SoyFileNode;

/** A compiler pass to run {@link SoyConformance}. */
final class SoyConformancePass extends CompilerFilePass {
  private final SoyConformance conformance;
  private final ErrorReporter errorReporter;

  SoyConformancePass(ImmutableList<ByteSource> conformanceConfigs, ErrorReporter errorReporter) {
    this.conformance = SoyConformance.create(conformanceConfigs);
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    conformance.check(file, errorReporter);
  }
}
