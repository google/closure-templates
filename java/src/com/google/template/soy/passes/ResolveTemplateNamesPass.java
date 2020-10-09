/*
 * Copyright 2020 Google Inc.
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
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.soytree.ImportsContext.ImportsTemplateRegistry;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode.SoyFileHeaderInfo;
import com.google.template.soy.soytree.TemplateNodeBuilder;
import com.google.template.soy.soytree.TemplatesPerFile.TemplateName;
import java.util.Optional;

/** Resolves template names in calls, checking against template names & imports. */
@RunAfter({
  ResolveTemplateImportsPass.class,
})
@RunBefore({
  SoyElementPass.class, // Needs {@link CallBasicNode#getCalleeName} to be resolved.
})
public final class ResolveTemplateNamesPass implements CompilerFileSetPass {

  public ResolveTemplateNamesPass() {}

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    for (SoyFileNode file : sourceFiles) {
      visitFile(
          file,
          file.hasTemplateRegistry() ? Optional.of(file.getTemplateRegistry()) : Optional.empty());
    }

    return Result.CONTINUE;
  }

  private void visitFile(SoyFileNode file, Optional<ImportsTemplateRegistry> templateRegistry) {
    for (TemplateLiteralNode node :
        SoyTreeUtils.getAllNodesOfType(file, TemplateLiteralNode.class)) {
      resolveTemplateName(node, templateRegistry, file.getHeaderInfo());
    }
  }

  /** Attempts to resolve a template name, checking against aliases & imports. */
  private void resolveTemplateName(
      TemplateLiteralNode templateLiteralNode,
      Optional<ImportsTemplateRegistry> importsTemplateRegistry,
      SoyFileHeaderInfo header) {

    if (templateLiteralNode.isResolved()) {
      return;
    }

    Identifier unresolvedIdent = templateLiteralNode.getIdentifier();
    String name = unresolvedIdent.identifier();

    // First, check if the symbol is imported. This could be a module import (e.g.
    // "fooTemplates.render") or a regular template symbol import ("render").
    if (importsTemplateRegistry.isPresent()) {
      TemplateName importedTemplate =
          importsTemplateRegistry.get().getSymbolsToTemplateNamesMap().get(name);
      if (importedTemplate != null) {
        templateLiteralNode.resolveTemplateName(
            Identifier.create(
                importedTemplate.fullyQualifiedName(), name, unresolvedIdent.location()));
        return;
      }
    }

    switch (unresolvedIdent.type()) {
      case SINGLE_IDENT:
      case DOT_IDENT:
        // Case 1: ".foo" and "foo" Source callee name is partial.
        templateLiteralNode.resolveTemplateName(
            Identifier.create(
                TemplateNodeBuilder.combineNsAndName(header.getNamespace(), name),
                name,
                unresolvedIdent.location()));
        return;
      case DOTTED_IDENT:
        // Case 2: "foo.bar.baz" Source callee name is a proper dotted ident, which might start with
        // an alias.
        templateLiteralNode.resolveTemplateName(header.resolveAlias(unresolvedIdent));
        return;
    }
    throw new AssertionError(unresolvedIdent.type());
  }
}
