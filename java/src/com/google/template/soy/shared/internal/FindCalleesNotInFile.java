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

package com.google.template.soy.shared.internal;

import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Visitor for finding the templates called in a file that are not defined in the file.
 *
 * <p>Important: Only deals with basic callees (not delegates). Calls to delegates are not
 * applicable here because we cannot tell at compile time which delegate will be called (if any).
 *
 * <p>{@link #exec} should be called on a {@code SoyFileNode}. The returned set will be the full
 * names of all templates called by the templates in this file that that not in this file. In other
 * words, if T is the set of templates in this file and U is the set of templates not in this file,
 * then the returned set consists of the full names of all templates in U called by any template in
 * T.
 *
 */
public final class FindCalleesNotInFile {
  public static Set<TemplateLiteralNode> findCalleesNotInFile(SoyFileNode soyFileNode) {
    Set<String> templatesInFile = new LinkedHashSet<>();
    Set<TemplateLiteralNode> calleesNotInFile = new LinkedHashSet<>();
    for (TemplateNode template : SoyTreeUtils.getAllNodesOfType(soyFileNode, TemplateNode.class)) {
      templatesInFile.add(template.getTemplateName());
    }
    for (TemplateLiteralNode templateLiteralNode :
        SoyTreeUtils.getAllNodesOfType(soyFileNode, TemplateLiteralNode.class)) {
      String calleeName = templateLiteralNode.getResolvedName();
      if (!templatesInFile.contains(calleeName)) {
        calleesNotInFile.add(templateLiteralNode);
      }
    }
    return calleesNotInFile;
  }

  /** Non-instantiable. */
  private FindCalleesNotInFile() {}
}
