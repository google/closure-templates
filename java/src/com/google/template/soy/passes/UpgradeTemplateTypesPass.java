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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.AbstractParentExprNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateMetadata;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.types.ErrorType;
import com.google.template.soy.types.LegacyObjectMapType;
import com.google.template.soy.types.ListType;
import com.google.template.soy.types.MapType;
import com.google.template.soy.types.MessageType;
import com.google.template.soy.types.NamedTemplateType;
import com.google.template.soy.types.PrimitiveType;
import com.google.template.soy.types.RecordType;
import com.google.template.soy.types.SoyProtoEnumType;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.SoyTypeVisitor;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.TemplateBindingUtil;
import com.google.template.soy.types.TemplateType;
import com.google.template.soy.types.UnionType;
import com.google.template.soy.types.UnknownType;
import com.google.template.soy.types.VeType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Upgrades template types from the "named template" placeholder type to proper types. */
final class UpgradeTemplateTypesPass implements CompilerFileSetPass {

  private static final SoyErrorKind ONLY_BASIC_TEMPLATES_ALLOWED =
      SoyErrorKind.of(
          "Only basic templates are allowed in expressions, but found template of kind: `{0}`.");

  private static final SoyErrorKind ONLY_STRICT_HTML_TEMPLATES_ALLOWED =
      SoyErrorKind.of(
          "Only strict HTML templates are allowed in expressions, but template `{0}` was not"
              + " strict HTML.");

  private final SoyTypeRegistry typeRegistry;
  private final ErrorReporter errorReporter;
  private final Set<String> invalidTemplateNames = new HashSet<>();
  private final Set<String> reportedInvalidTemplateNames = new HashSet<>();

  UpgradeTemplateTypesPass(SoyTypeRegistry typeRegistry, ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
    this.typeRegistry = typeRegistry;
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    for (SoyFileNode file : sourceFiles) {
      TemplateRegistry templateRegistry = file.getTemplateRegistry();
      for (ExprNode exprNode : SoyTreeUtils.getAllNodesOfType(file, ExprNode.class)) {
        if (SoyTypes.transitivelyContainsKind(exprNode.getType(), SoyType.Kind.NAMED_TEMPLATE)) {
          boolean isTemplateLiteral = exprNode.getKind() == ExprNode.Kind.TEMPLATE_LITERAL_NODE;
          // Template literal nodes and expr root nodes directly wrapping a TemplateLiteralNode are
          // exempt from some checks.
          boolean isSynthetic =
              (isTemplateLiteral && ((TemplateLiteralNode) exprNode).isSynthetic())
                  || (exprNode.getKind() == ExprNode.Kind.EXPR_ROOT_NODE
                      && ((ExprRootNode) exprNode).getRoot().getKind()
                          == ExprNode.Kind.TEMPLATE_LITERAL_NODE
                      && ((TemplateLiteralNode) ((ExprRootNode) exprNode).getRoot()).isSynthetic());
          SoyType resolvedType =
              exprNode
                  .getType()
                  .accept(
                      new TemplateTypeUpgrader(
                          exprNode.getSourceLocation(),
                          templateRegistry,
                          isTemplateLiteral,
                          isSynthetic));
          switch (exprNode.getKind()) {
            case TEMPLATE_LITERAL_NODE:
              ((TemplateLiteralNode) exprNode).setType(resolvedType);
              break;
            case VAR_REF_NODE:
              ((VarRefNode) exprNode).setSubstituteType(resolvedType);
              break;
            default:
              if (exprNode instanceof AbstractParentExprNode) {
                ((AbstractParentExprNode) exprNode).setType(resolvedType);
              } else {
                throw new AssertionError("Unhandled type: " + exprNode);
              }
              break;
          }
        }
      }
    }
    checkReportedAllInvalidNames();
    return Result.CONTINUE;
  }

  private void checkReportedAllInvalidNames() {
    // Sanity check that we reported at least one error for each invalid template name.
    Preconditions.checkState(
        invalidTemplateNames.equals(reportedInvalidTemplateNames),
        "Expected: " + invalidTemplateNames + "; found: " + reportedInvalidTemplateNames);
  }

  private class TemplateTypeUpgrader implements SoyTypeVisitor<SoyType> {
    private final SourceLocation whereToReportErrors;
    private final TemplateRegistry templateRegistry;
    private final boolean isTemplateLiteral;
    private final boolean isSynthetic;

    private TemplateTypeUpgrader(
        SourceLocation whereToReportErrors,
        TemplateRegistry templateRegistry,
        boolean isTemplateLiteral,
        boolean isSynthetic) {
      this.whereToReportErrors = whereToReportErrors;
      this.templateRegistry = templateRegistry;
      this.isTemplateLiteral = isTemplateLiteral;
      this.isSynthetic = isSynthetic;
    }

    @Override
    public SoyType visit(ErrorType type) {
      return type;
    }

    @Override
    public SoyType visit(LegacyObjectMapType type) {
      if (type.getKeyType() == null && type.getValueType() == null) {
        return type;
      }
      SoyType key = type.getKeyType().accept(this);
      SoyType value = type.getValueType().accept(this);
      return typeRegistry.getOrCreateLegacyObjectMapType(key, value);
    }

    @Override
    public SoyType visit(ListType type) {
      if (type.getElementType() == null) {
        return type;
      }
      SoyType element = type.getElementType().accept(this);
      return typeRegistry.getOrCreateListType(element);
    }

    @Override
    public SoyType visit(MapType type) {
      if (type.getKeyType() == null && type.getValueType() == null) {
        return type;
      }
      SoyType key = type.getKeyType().accept(this);
      SoyType value = type.getValueType().accept(this);
      return typeRegistry.getOrCreateMapType(key, value);
    }

    @Override
    public SoyType visit(NamedTemplateType type) {
      TemplateMetadata basicTemplateOrElement =
          templateRegistry.getBasicTemplateOrElement(type.getTemplateName());
      if (basicTemplateOrElement == null) {
        // Synthetic nodes are exempt from this check, to support external calls.
        if (isSynthetic) {
          return UnknownType.getInstance();
        }
        // Error reporting here should be handled by StrictDepsPass and CheckDelegatesPass.
        return ErrorType.getInstance();
      }
      if (basicTemplateOrElement.getTemplateKind() != TemplateType.TemplateKind.BASIC
          && !isSynthetic) {
        // Only report errors for template literal nodes, to avoid reporting errors multiple times
        // (ie., once for everywhere the 'named' template type has propagated in the expression
        // tree).
        invalidTemplateNames.add(type.getTemplateName());
        if (isTemplateLiteral) {
          errorReporter.report(
              whereToReportErrors,
              ONLY_BASIC_TEMPLATES_ALLOWED,
              basicTemplateOrElement.getTemplateKind());
          reportedInvalidTemplateNames.add(type.getTemplateName());
        }
        return ErrorType.getInstance();
      }
      if (basicTemplateOrElement.getContentKind() == SanitizedContentKind.HTML
          && !basicTemplateOrElement.isStrictHtml()
          && !isSynthetic) {
        // Only report errors for template literal nodes, to avoid reporting errors multiple times
        // (ie., once for everywhere the 'named' template type has propagated in the expression
        // tree).
        invalidTemplateNames.add(type.getTemplateName());
        if (isTemplateLiteral) {
          errorReporter.report(
              whereToReportErrors,
              ONLY_STRICT_HTML_TEMPLATES_ALLOWED,
              basicTemplateOrElement.getTemplateName());
          reportedInvalidTemplateNames.add(type.getTemplateName());
        }
        return ErrorType.getInstance();
      }
      TemplateType templateType =
          typeRegistry.internTemplateType(TemplateMetadata.asTemplateType(basicTemplateOrElement));
      if (type.getBoundParameters().isPresent()) {
        return TemplateBindingUtil.bindParameters(
            templateType,
            (RecordType) type.getBoundParameters().get().accept(this),
            typeRegistry,
            errorReporter,
            whereToReportErrors);
      } else {
        return templateType;
      }
    }

    @Override
    public SoyType visit(PrimitiveType type) {
      return type;
    }

    @Override
    public SoyType visit(RecordType type) {
      List<RecordType.Member> memberTypes = new ArrayList<>();
      for (RecordType.Member member : type.getMembers()) {
        memberTypes.add(RecordType.memberOf(member.name(), member.type().accept(this)));
      }
      return typeRegistry.getOrCreateRecordType(memberTypes);
    }

    @Override
    public SoyType visit(SoyProtoEnumType type) {
      return type;
    }

    @Override
    public SoyType visit(SoyProtoType type) {
      return type;
    }

    @Override
    public SoyType visit(TemplateType type) {
      // Template types are only possible at this point if they are declared types. Declared types
      // may not have a named template type in them. Check that this is the case, and return.
      Preconditions.checkState(
          !SoyTypes.transitivelyContainsKind(type, SoyType.Kind.NAMED_TEMPLATE));
      return type;
    }

    @Override
    public SoyType visit(UnionType type) {
      List<SoyType> memberTypes = new ArrayList<>();
      for (SoyType memberType : type.getMembers()) {
        memberTypes.add(memberType.accept(this));
      }
      return typeRegistry.getOrCreateUnionType(memberTypes);
    }

    @Override
    public SoyType visit(VeType type) {
      return type;
    }

    @Override
    public SoyType visit(MessageType type) {
      return type;
    }
  }
}
