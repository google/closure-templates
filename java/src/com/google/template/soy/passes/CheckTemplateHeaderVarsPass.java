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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.error.SoyErrors;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.passes.IndirectParamsCalculator.IndirectParamsInfo;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateBasicNode;
import com.google.template.soy.soytree.TemplateElementNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.defn.TemplateHeaderVarDefn;
import com.google.template.soy.soytree.defn.TemplateStateVar;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Pass for checking that in each template, the declared parameters match the data keys referenced
 * in the template.
 *
 * <p>Note this visitor only works for code in Soy V2 syntax.
 */
public final class CheckTemplateHeaderVarsPass extends CompilerFileSetPass {

  private static final SoyErrorKind UNDECLARED_DATA_KEY =
      SoyErrorKind.of("Unknown data key ''{0}''.{1}", StyleAllowance.NO_PUNCTUATION);
  private static final SoyErrorKind UNUSED_VAR =
      SoyErrorKind.of("''{0}'' unused in template body.");

  private final ErrorReporter errorReporter;

  CheckTemplateHeaderVarsPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public Result run(
      ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator, TemplateRegistry registry) {
    for (SoyFileNode fileNode : sourceFiles) {
      for (TemplateNode templateNode : fileNode.getChildren()) {
        checkTemplate(templateNode, registry);
      }
    }
    return Result.CONTINUE;
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.

  private void checkTemplate(TemplateNode node, TemplateRegistry templateRegistry) {
    ListMultimap<String, SourceLocation> dataKeys = ArrayListMultimap.create();

    for (VarRefNode varRefNode : SoyTreeUtils.getAllNodesOfType(node, VarRefNode.class)) {
      if (varRefNode.isPossibleHeaderVar()) {
        dataKeys.put(varRefNode.getName(), varRefNode.getSourceLocation());
      }
    }
    if (node instanceof TemplateElementNode) {
      TemplateElementNode el = (TemplateElementNode) node;
      for (TemplateStateVar state : el.getStateVars()) {
        for (VarRefNode varRefNode :
            SoyTreeUtils.getAllNodesOfType(state.defaultValue(), VarRefNode.class)) {
          // This is in the case where @state appears before @param and uses @param.
          if (varRefNode.getDefnDecl().kind() == VarDefn.Kind.UNDECLARED) {
            errorReporter.report(
                varRefNode.getSourceLocation(),
                UNDECLARED_DATA_KEY,
                varRefNode.getDefnDecl().name(),
                "");
          } else if (varRefNode.isPossibleHeaderVar()) {
            dataKeys.put(varRefNode.getName(), varRefNode.getSourceLocation());
          }
        }
      }
    }

    IndirectParamsInfo ipi =
        new IndirectParamsCalculator(templateRegistry)
            .calculateIndirectParams(templateRegistry.getMetadata(node));

    Set<String> allHeaderVarNames = new HashSet<>();
    List<TemplateHeaderVarDefn> unusedParams = new ArrayList<>();
    // Process @param header variables.
    // TODO: Switch getAllParams to getHeaderParams
    for (TemplateHeaderVarDefn param : node.getAllParams()) {
      allHeaderVarNames.add(param.name());
      if (dataKeys.containsKey(param.name())) {
        // Good: Declared and referenced in template. We remove these from dataKeys so
        // that at the end of the for-loop, dataKeys will only contain the keys that are referenced
        // but not declared in SoyDoc.
        dataKeys.removeAll(param.name());
        // TODO: This should only be allowed for @param and not @inject or @state.
      } else if (ipi.paramKeyToCalleesMultimap.containsKey(param.name())
          || ipi.mayHaveIndirectParamsInExternalCalls
          || ipi.mayHaveIndirectParamsInExternalDelCalls) {
        // Good: Declared in SoyDoc and either (a) used in a call that passes all data or (b) used
        // in an external call or delcall that passes all data, which may need the param (we can't
        // verify).
      } else {
        // Bad: Declared in SoyDoc but not referenced in template.
        unusedParams.add(param);
      }
    }

    List<TemplateHeaderVarDefn> unusedStateVars = new ArrayList<>();
    // Process @state header variables.
    if (node instanceof TemplateElementNode) {
      TemplateElementNode el = (TemplateElementNode) node;
      for (TemplateStateVar stateVar : el.getStateVars()) {
        allHeaderVarNames.add(stateVar.name());
        if (dataKeys.containsKey(stateVar.name())) {
          // Good: declared and referenced in the template.
          dataKeys.removeAll(stateVar.name());
        } else {
          // Bad: declared in the header, but not used.
          unusedStateVars.add(stateVar);
        }
      }
    }
    // At this point, the only keys left in dataKeys are undeclared.
    for (Entry<String, SourceLocation> undeclared : dataKeys.entries()) {
      String extraErrorMessage =
          SoyErrors.getDidYouMeanMessage(allHeaderVarNames, undeclared.getKey());
      errorReporter.report(
          undeclared.getValue(), UNDECLARED_DATA_KEY, undeclared.getKey(), extraErrorMessage);
    }

    // Delegate templates can declare unused params because other implementations
    // of the same delegate may need to use those params.
    if (node instanceof TemplateBasicNode) {
      reportUnusedHeaderVars(errorReporter, unusedParams, UNUSED_VAR);
    }
    if (node instanceof TemplateElementNode) {
      reportUnusedHeaderVars(errorReporter, unusedStateVars, UNUSED_VAR);
    }
  }

  private static void reportUnusedHeaderVars(
      ErrorReporter errorReporter,
      List<TemplateHeaderVarDefn> unusedHeaderVars,
      SoyErrorKind soyError) {
    for (TemplateHeaderVarDefn unusedVar : unusedHeaderVars) {
      errorReporter.report(unusedVar.nameLocation(), soyError, unusedVar.name());
    }
  }
}
