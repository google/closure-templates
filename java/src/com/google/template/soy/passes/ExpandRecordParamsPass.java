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

import static com.google.template.soy.base.SourceLocation.UNKNOWN;

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
import com.google.template.soy.types.UnknownType;

/** Expands a {@link TemplateParamsNode} into {@link TemplateParam} and adds them to the AST. */
@RunAfter(ResolveDeclaredTypesPass.class)
@RunBefore(CheckTemplateCallsPass.class)
final class ExpandRecordParamsPass implements CompilerFilePass {
  private final ErrorReporter errorReporter;

  private static final SoyErrorKind ATTRIBUTE_PARAM_ONLY_IN_ELEMENT_TEMPLATE =
      SoyErrorKind.of("Only templates of kind=\"html<?>\" can have @attribute.");

  private static final SoyErrorKind NO_SUCH_TYPE_MEMBER =
      SoyErrorKind.of("''{0}'' is not a member of type ''{1}''.");

  private static final SoyErrorKind NOT_A_RECORD_TYPE =
      SoyErrorKind.of("Type ''{0}'' must be a record type.");

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

    SoyType authoredType = paramsNode.getTypeNode().getResolvedType();
    SoyType paramsType = authoredType.getEffectiveType();
    RecordType recordType = null;
    if (paramsType instanceof RecordType) {
      recordType = (RecordType) paramsType;
    } else {
      errorReporter.report(
          paramsNode.getTypeNode().sourceLocation(), NOT_A_RECORD_TYPE, authoredType);
    }

    for (Identifier memberId : paramsNode.getNames()) {
      String memberName = memberId.identifier();

      if (recordType != null) {
        RecordType.Member member = recordType.getMember(memberName);
        if (member != null) {
          ExprNode defaultValue = null;
          if (defaultValueRecord != null) {
            if (defaultValueRecord.getKind() == ExprNode.Kind.RECORD_LITERAL_NODE) {
              defaultValue = ((RecordLiteralNode) defaultValueRecord).getValue(memberName);
              if (defaultValue != null) {
                defaultValue = defaultValue.copy(new CopyState());
              }
            } else {
              defaultValue =
                  new FieldAccessNode(
                      defaultValueRecord.copy(new CopyState()),
                      memberName,
                      memberId.location(),
                      false);
            }
          }
          TemplateParam param =
              new TemplateParam(
                  memberName,
                  memberId.location(),
                  UNKNOWN,
                  null,
                  false,
                  false,
                  member.optional(),
                  null,
                  defaultValue);
          param.setType(member.checkedType());
          node.addParam(param);
          continue;
        } else {
          errorReporter.report(memberId.location(), NO_SUCH_TYPE_MEMBER, memberName, authoredType);
        }
      }

      // Avoid additional errors when referencing the params that aren't in the record.
      TemplateParam param =
          new TemplateParam(memberName, UNKNOWN, UNKNOWN, null, false, false, true, null, null);
      param.setType(UnknownType.getInstance());
      node.addParam(param);
    }
  }
}
