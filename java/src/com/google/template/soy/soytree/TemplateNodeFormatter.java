/*
 * Copyright 2019 Google Inc.
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

package com.google.template.soy.soytree;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.basetree.Node;
import com.google.template.soy.basetree.NodeFormatter;
import com.google.template.soy.basetree.SourceBuilder;
import com.google.template.soy.basetree.SourceBuilder.Indent;
import com.google.template.soy.basetree.SourceBuilder.LineBuilder;
import com.google.template.soy.soytree.defn.TemplateHeaderVarDefn;
import com.google.template.soy.types.ast.TypeFormatter;

/** Formatting logic for {template}, {detemplate} and {element}. A 'friend' of TemplateNode. */
public final class TemplateNodeFormatter {
  private final NodeFormatter nodeFormatter;
  private final TypeFormatter typeFormatter;
  private final SourceBuilder sb;

  public TemplateNodeFormatter(
      NodeFormatter nodeFormatter, TypeFormatter typeFormatter, SourceBuilder sb) {
    this.nodeFormatter = nodeFormatter;
    this.typeFormatter = typeFormatter;
    this.sb = sb;
  }

  public void format(TemplateNode node) {
    sb.newLine();
    if (node.getSoyDoc() != null) {
      sb.maybeNewLine();
      sb.appendMultiline(node.getSoyDoc());
      sb.newLine();
    }

    // Begin tag.
    sb.maybeNewLine().append(node.getTagString());
    try (Indent indent = sb.indent()) {
      ImmutableList<TemplateHeaderVarDefn> headerVars = node.getHeaderParamsForSourceString();
      for (final TemplateHeaderVarDefn headerVar : headerVars) {
        LineBuilder line = sb.maybeNewLine();
        line.append("{").append(node.getDeclNameMap().get(headerVar.getClass()));
        if (!headerVar.isRequired()) {
          line.append("?");
        }
        line.append(" ").append(headerVar.name());
        boolean implicitType = headerVar.getTypeNode() == null;
        if (!implicitType) {
          line.append(": ");
          typeFormatter.formatNext(headerVar.getTypeNode());
        }

        if (headerVar.defaultValue() != null) {
          line.append(implicitType ? " :" : " ")
              .append("= ")
              .append(headerVar.defaultValue().toSourceString());
        }
        line.append("}");
        if (headerVar.desc() != null) {
          line.append("  /** ").append(headerVar.desc()).append(" */");
        }
      }
      if (!headerVars.isEmpty() && node.numChildren() > 0) {
        sb.newLine();
        sb.newLine();
      }
      for (Node child : node.getChildren()) {
        nodeFormatter.formatNext(child);
      }
    }
    // End tag.
    sb.maybeNewLine().append("{/").append(node.getCommandName()).append("}");
  }
}
