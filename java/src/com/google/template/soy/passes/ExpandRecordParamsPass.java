/*
 * Copyright 2024 Google Inc.
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

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.base.internal.TemplateContentKind;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.FieldAccessNode;
import com.google.template.soy.exprtree.RecordLiteralNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateParamsNode;
import com.google.template.soy.soytree.defn.AttrParam;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.RecordType;
import com.google.template.soy.types.SoyType;

/** Expands a {@link TemplateParamsNode} into {@link TemplateParam} and adds them to the AST. */
@RunAfter(ResolveDeclaredTypesPass.class)
@RunBefore(CheckTemplateCallsPass.class)
final class ExpandRecordParamsPass implements CompilerFilePass {
  private final ErrorReporter errorReporter;

  private static final SoyErrorKind ATTRIBUTE_PARAM_ONLY_IN_ELEMENT_TEMPLATE =
      SoyErrorKind.of("Only templates of kind=\"html<?>\" can have @attribute.");

  public ExpandRecordParamsPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    file.getTemplates().forEach(this::visitTemplateNode);
  }

  private void visitTemplateNode(TemplateNode node) {
    for (TemplateParam param : node.getAllParams()) {
      if (param instanceof AttrParam
          && !(node.getTemplateContentKind() instanceof TemplateContentKind.ElementContentKind)) {
        errorReporter.report(param.getSourceLocation(), ATTRIBUTE_PARAM_ONLY_IN_ELEMENT_TEMPLATE);
      }
    }

    TemplateParamsNode paramsNode = node.getParamsNode();
    if (paramsNode == null) {
      return;
    }

    ExprNode defaultValueRecord = null;
    if (paramsNode.getDefaultValue() != null) {
      defaultValueRecord = paramsNode.getDefaultValue().getRoot();
    }

    SoyType paramsType = paramsNode.getTypeNode().getResolvedType().getEffectiveType();
    if (paramsType instanceof RecordType) {
      RecordType recordType = (RecordType) paramsType;
      for (Identifier memberId : paramsNode.getNames()) {
        String memberName = memberId.identifier();
        RecordType.Member member = recordType.getMember(memberName);
        if (member != null) {
          ExprNode defaultValue = null;
          if (defaultValueRecord != null) {
            if (defaultValueRecord.getKind() == ExprNode.Kind.RECORD_LITERAL_NODE) {
              defaultValue = ((RecordLiteralNode) defaultValueRecord).getValue(memberName);
            } else {
              defaultValue =
                  new FieldAccessNode(
                      defaultValueRecord.copy(new CopyState()),
                      memberName,
                      SourceLocation.UNKNOWN,
                      false);
            }
          }
          TemplateParam param =
              new TemplateParam(
                  memberName,
                  memberId.location(),
                  SourceLocation.UNKNOWN,
                  null,
                  false,
                  false,
                  member.optional(),
                  null,
                  defaultValue);
          param.setType(member.checkedType());
          node.addParam(param);
        }
      }
    }
  }
}
