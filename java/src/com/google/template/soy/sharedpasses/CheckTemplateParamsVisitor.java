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

package com.google.template.soy.sharedpasses;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyError;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.sharedpasses.FindIndirectParamsVisitor.IndirectParamsInfo;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoytreeUtils;
import com.google.template.soy.soytree.TemplateBasicNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.defn.TemplateParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

/**
 * Visitor for checking that in each template, the parameters declared in the SoyDoc match the data
 * keys referenced in the template.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> Precondition: All template and callee names should be full names (i.e. you must execute
 * {@code SetFullCalleeNamesVisitor} before executing this visitor).
 *
 * <p> Note this visitor only works for code in Soy V2 syntax.
 *
 * <p> {@link #exec} should be called on a full parse tree. There is no return value.
 * However, errors are reported to the {@code ErrorReporter} if the parameters
 * declared in some template's SoyDoc do not match the data keys referenced in that template.
 *
 */
public final class CheckTemplateParamsVisitor extends AbstractSoyNodeVisitor<Void> {

  private static final SoyError UNDECLARED_DATA_KEY = SoyError.of("Unknown data key {0}.");
  private static final SoyError UNUSED_PARAM = SoyError.of("Param {0} unused in template body.");

  /** User-declared syntax version. */
  private final SyntaxVersion declaredSyntaxVersion;

  /** Registry of all templates in the Soy tree. */
  private TemplateRegistry templateRegistry;

  /**
   * @param declaredSyntaxVersion User-declared syntax version,
   */
  public CheckTemplateParamsVisitor(SyntaxVersion declaredSyntaxVersion,
      ErrorReporter errorReporter) {
    super(errorReporter);
    this.declaredSyntaxVersion = declaredSyntaxVersion;
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.

  @Override protected void visitSoyFileSetNode(SoyFileSetNode node) {
    // Build templateRegistry.
    templateRegistry = new TemplateRegistry(node, errorReporter);

    // Run pass only on the Soy files that are all in V2 syntax.
    for (SoyFileNode soyFile : node.getChildren()) {
      // First determine if Soy file is all in V2 syntax.
      boolean doCheckSoyDocInFile;
      if (declaredSyntaxVersion.num >= SyntaxVersion.V2_0.num) {
        doCheckSoyDocInFile = true;
      } else {
        try {
          // TODO SOON: Use a simpler visitor that doesn't report errors and shortcircuits.
          new ReportSyntaxVersionErrorsVisitor(SyntaxVersion.V2_0, true, errorReporter)
              .exec(soyFile);
          doCheckSoyDocInFile = true;
        } catch (SoySyntaxException sse) {
          doCheckSoyDocInFile = false;
        }
      }
      // Run pass on Soy file if it is all in V2 syntax.
      if (doCheckSoyDocInFile) {
        visit(soyFile);
      }
    }
  }

  @Override protected void visitTemplateNode(TemplateNode node) {
    ListMultimap<String, SourceLocation> dataKeys = ArrayListMultimap.create();

    for (VarRefNode varRefNode : SoytreeUtils.getAllNodesOfType(node, VarRefNode.class)) {
      if (varRefNode.isPossibleParam()) {
        dataKeys.put(varRefNode.getName(), varRefNode.getSourceLocation());
      }
    }

    IndirectParamsInfo ipi
        = new FindIndirectParamsVisitor(templateRegistry, errorReporter).exec(node);

    List<String> unusedParams = new ArrayList<>();
    for (TemplateParam param : node.getAllParams()) {
      if (dataKeys.containsKey(param.name())) {
        // Good: Declared and referenced in template. We remove these from dataKeys so
        // that at the end of the for-loop, dataKeys will only contain the keys that are referenced
        // but not declared in SoyDoc.
        dataKeys.removeAll(param.name());
      } else if (ipi.paramKeyToCalleesMultimap.containsKey(param.name()) ||
                 ipi.mayHaveIndirectParamsInExternalCalls ||
                 ipi.mayHaveIndirectParamsInExternalDelCalls) {
        // Good: Declared in SoyDoc and either (a) used in a call that passes all data or (b) used
        // in an external call or delcall that passes all data, which may need the param (we can't
        // verify).
      } else {
        // Bad: Declared in SoyDoc but not referenced in template.
        unusedParams.add(param.name());
      }
    }

    // At this point, the only keys left in dataKeys are undeclared.
    for (Entry<String, SourceLocation> undeclared : dataKeys.entries()) {
      errorReporter.report(undeclared.getValue(), UNDECLARED_DATA_KEY, undeclared.getKey());
    }

    // Delegate templates can declare unused params because other implementations
    // of the same delegate may need to use those params.
    if (node instanceof TemplateBasicNode) {
      for (String unusedParam : unusedParams) {
        errorReporter.report(node.getSourceLocation(), UNUSED_PARAM, unusedParam);
      }
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.

  @Override protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode<?>) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }
}
