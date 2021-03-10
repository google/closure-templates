/*
 * Copyright 2008 Google Inc.
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
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.passes.IndirectParamsCalculator.IndirectParamsInfo;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.TemplateMetadata;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.defn.TemplateHeaderVarDefn;
import java.util.function.Supplier;

/**
 * Pass for checking that in each template there is no ambiguity between inject parameters and
 * indirect parameters.
 *
 * <p>TODO(lukes): rename this pass? find another place for this functionality
 */
@RunAfter(FinalizeTemplateRegistryPass.class)
public final class CheckTemplateHeaderVarsPass implements CompilerFileSetPass {

  private static final SoyErrorKind INJECTED_PARAM_COLLISION =
      SoyErrorKind.of(
          "Injected param ''{0}'' conflicts with indirect param with the same name in template"
              + " ''{1}''.");

  private final ErrorReporter errorReporter;
  private final Supplier<TemplateRegistry> templateRegistryFull;

  CheckTemplateHeaderVarsPass(
      ErrorReporter errorReporter, Supplier<TemplateRegistry> templateRegistryFull) {
    this.errorReporter = errorReporter;
    this.templateRegistryFull = templateRegistryFull;
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    IndirectParamsCalculator ipc = new IndirectParamsCalculator(templateRegistryFull.get());
    for (SoyFileNode fileNode : sourceFiles) {
      for (TemplateNode templateNode : fileNode.getTemplates()) {
        checkTemplate(templateNode, ipc);
      }
    }
    return Result.CONTINUE;
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.

  private void checkTemplate(TemplateNode node, IndirectParamsCalculator calculator) {
    IndirectParamsInfo ipi = calculator.calculateIndirectParams(node);

    // Check for naming collisions between @inject in this template and @param in a data=all callee
    for (TemplateHeaderVarDefn param : node.getInjectedParams()) {
      for (TemplateMetadata template : ipi.paramKeyToCalleesMultimap.get(param.name())) {
        errorReporter.report(
            param.getSourceLocation(),
            INJECTED_PARAM_COLLISION,
            param.name(),
            template.getTemplateName());
      }
    }
  }
}
