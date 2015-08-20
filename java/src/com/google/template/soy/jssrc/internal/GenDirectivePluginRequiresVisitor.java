/*
 * Copyright 2012 Google Inc.
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

package com.google.template.soy.jssrc.internal;

import com.google.common.collect.Sets;
import com.google.template.soy.html.AbstractHtmlSoyNodeVisitor;
import com.google.template.soy.jssrc.restricted.SoyLibraryAssistedJsSrcPrintDirective;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;

import java.util.Map;
import java.util.SortedSet;

import javax.inject.Inject;

/**
 * A visitor to generate a set of Closure JS library names required by the plugins used by this
 * template.
 *
 */
public final class GenDirectivePluginRequiresVisitor
    extends AbstractHtmlSoyNodeVisitor<SortedSet<String>> {

  private final Map<String, SoyLibraryAssistedJsSrcPrintDirective>
      soyLibraryAssistedJsSrcDirectivesMap;

  private SortedSet<String> requiredJsLibNames;


  @Inject
  public GenDirectivePluginRequiresVisitor(
      Map<String, SoyLibraryAssistedJsSrcPrintDirective> soyLibraryAssistedJsSrcDirectivesMap) {
    this.soyLibraryAssistedJsSrcDirectivesMap = soyLibraryAssistedJsSrcDirectivesMap;
  }


  @Override public SortedSet<String> exec(SoyNode soyNode) {
    requiredJsLibNames = Sets.newTreeSet();
    visit(soyNode);
    return requiredJsLibNames;
  }


  @Override protected void visitPrintDirectiveNode(PrintDirectiveNode node) {
    String directiveName = node.getName();
    if (soyLibraryAssistedJsSrcDirectivesMap.containsKey(directiveName)) {
      requiredJsLibNames
          .addAll(soyLibraryAssistedJsSrcDirectivesMap.get(directiveName).getRequiredJsLibNames());
    }
  }


  @Override protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode<?>) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }
}
