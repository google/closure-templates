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
import com.google.template.soy.basetree.SourceBuilder;
import com.google.template.soy.soytree.defn.TemplateHeaderVarDefn;

/** Formatting logic for {template}, {detemplate} and {element}. A 'friend' of TemplateNode. */
public final class TemplateNodeFormatter {
  private final AbstractSoyNodeVisitor<SourceBuilder> formatter;
  private final SourceBuilder sb;

  public TemplateNodeFormatter(AbstractSoyNodeVisitor<SourceBuilder> formatter, SourceBuilder sb) {
    this.formatter = formatter;
    this.sb = sb;
  }

  public void format(TemplateNode node) {
    if (node.getSoyDoc() != null) {
      sb.appendLine(node.getSoyDoc());
    }

    // Begin tag.
    sb.appendLine(node.getTagString());
    sb.indent();

    ImmutableList<TemplateHeaderVarDefn> headerVars = node.getHeaderParamsForSourceString();
    for (TemplateHeaderVarDefn headerVar : headerVars) {
      StringBuilder paramBuilder = new StringBuilder();
      paramBuilder.append("{").append(node.getDeclNameMap().get(headerVar.getClass()));
      if (!headerVar.isRequired()) {
        paramBuilder.append("?");
      }
      paramBuilder.append(" ").append(headerVar.name());
      String typeString =
          String.valueOf(headerVar.hasType() ? ": " + headerVar.type() : headerVar.getTypeNode());
      boolean implicitType = typeString.equals("null");
      if (!implicitType) {
        // TODO(user): This won't work for non-primites, to be fixed.
        paramBuilder.append(": ").append(typeString);
      }
      if (headerVar.defaultValue() != null) {
        if (!implicitType) {
          paramBuilder.append(" ");
        } else {
          paramBuilder.append(" :");
        }
        paramBuilder.append("= ").append(headerVar.defaultValue().toSourceString());
      }
      paramBuilder.append("}");
      if (headerVar.desc() != null) {
        paramBuilder.append("  /** ").append(headerVar.desc()).append(" */");
      }
      sb.appendLine(paramBuilder.toString());
    }

    formatter.visitChildren(node);

    sb.dedent();
    // End tag.
    sb.appendLine("{/" + node.getCommandName() + "}");
  }
}
