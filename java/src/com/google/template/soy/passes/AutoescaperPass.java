/*
 * Copyright 2018 Google Inc.
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
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.parsepasses.contextautoesc.ContextualAutoescaper;
import com.google.template.soy.parsepasses.contextautoesc.Inferences;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.SoyFileNode;

/** A shim around ContextualAutoescaper to make it conform to the pass interface. */
final class AutoescaperPass implements CompilerFileSetPass {

  private final ContextualAutoescaper autoescaper;
  private final ErrorReporter errorReporter;
  private final boolean autoescaperEnabled;

  AutoescaperPass(
      ErrorReporter errorReporter,
      ImmutableList<? extends SoyPrintDirective> printDirectives,
      boolean autoescaperEnabled) {
    this.errorReporter = errorReporter;
    this.autoescaper = new ContextualAutoescaper(errorReporter, printDirectives);
    this.autoescaperEnabled = autoescaperEnabled;
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    // The autoescaper depends on certain amounts of template validation having been done, so we
    // can't safely run on broken trees.
    //  * that all deltemplates have compatible content kinds
    //  * that 'external' templates are correctly allowed or disallowed.
    // So just bail out.
    if (errorReporter.hasErrors()) {
      return Result.STOP;
    }
    Inferences inferences = autoescaper.annotate(sourceFiles);
    if (inferences == null) {
      return Result.STOP;
    }
    if (autoescaperEnabled) {
      autoescaper.rewrite(sourceFiles, idGenerator, inferences);
    }
    // for historical reasons compiler passes that run after the autoescaper depend on the metadata
    // addded by the escaping being present. So for now we abort compilation on autoescaper errors.
    if (errorReporter.hasErrors()) {
      return Result.STOP;
    }
    return Result.CONTINUE;
  }
}
