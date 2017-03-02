/*
 * Copyright 2016 Google Inc.
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

import static com.google.template.soy.passes.CheckTemplateCallsPass.ARGUMENT_TYPE_MISMATCH;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrors;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ProtoInitNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.aggregate.ListType;
import com.google.template.soy.types.primitive.ErrorType;
import com.google.template.soy.types.primitive.NullType;
import com.google.template.soy.types.primitive.UnknownType;
import com.google.template.soy.types.proto.SoyProtoType;
import java.util.Set;

/**
 * A {@link CompilerFilePass} that checks proto initialization calls for correctness.
 *
 * <p>Checks that proto init calls contain all required proto fields and that args are of the
 * correct types.
 */
final class CheckProtoInitCallsPass extends CompilerFilePass {

  private static final SoyErrorKind FIELD_DOES_NOT_EXIST =
      SoyErrorKind.of("Proto field ''{0}'' does not exist.{1}");
  private static final SoyErrorKind MISSING_REQUIRED_FIELD =
      SoyErrorKind.of("Missing required proto field ''{0}''.");
  private static final SoyErrorKind NULL_ARG_TYPE =
      SoyErrorKind.of("Cannot assign static type ''null'' to proto field ''{0}''.");

  private final ErrorReporter errorReporter;

  CheckProtoInitCallsPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (ProtoInitNode node : SoyTreeUtils.getAllNodesOfType(file, ProtoInitNode.class)) {
      checkProtoInitNode(node);
    }
  }

  private void checkProtoInitNode(ProtoInitNode node) {
    SoyType soyType = node.getType();
    Preconditions.checkNotNull(soyType);

    if (soyType.getKind() == Kind.PROTO) {
      checkProto(node, (SoyProtoType) soyType);
    }
    // else, do nothing. ResolveExpressionTypesVisitor should have already reported an error.
  }

  private void checkProto(ProtoInitNode node, SoyProtoType soyType) {
    // Check that all proto required fields are present.
    // TODO(user): Consider writing a soyProtoTypeImpl.getRequiredFields()
    Set<String> givenParams = Sets.newHashSet(node.getParamNames());
    for (FieldDescriptor field : soyType.getDescriptor().getFields()) {
      if (field.isRequired() && !givenParams.contains(field.getName())) {
        errorReporter.report(node.getSourceLocation(), MISSING_REQUIRED_FIELD, field.getName());
      }
    }

    ImmutableSet<String> fields = soyType.getFieldNames();
    for (int i = 0; i < node.numChildren(); i++) {
      String fieldName = node.getParamNames().get(i);
      ExprNode expr = node.getChild(i);

      // Check that each arg exists in the proto.
      if (!fields.contains(fieldName)) {
        String extraErrorMessage = SoyErrors.getDidYouMeanMessageForProtoFields(fields, fieldName);
        errorReporter.report(
            expr.getSourceLocation(), FIELD_DOES_NOT_EXIST, fieldName, extraErrorMessage);
        continue;
      }

      // Check that the arg type is not null and that it matches the expected field type.
      SoyType argType = expr.getType();
      if (argType.equals(NullType.getInstance())) {
        errorReporter.report(expr.getSourceLocation(), NULL_ARG_TYPE, fieldName);
      }

      SoyType fieldType = soyType.getFieldType(fieldName);

      // Let args with unknown or error types pass
      if (argType.equals(UnknownType.getInstance()) || argType.equals(ErrorType.getInstance())) {
        return;
      }

      // Same for List<?>, for repeated fields
      if (fieldType.getKind() == Kind.LIST && argType.getKind() == Kind.LIST) {
        SoyType argElementType = ((ListType) argType).getElementType();
        if (argElementType == null || argElementType.equals(UnknownType.getInstance())) {
          return;
        }
      }

      SoyType expectedType = SoyTypes.makeNullable(fieldType);
      if (!expectedType.isAssignableFrom(argType)) {
        errorReporter.report(
            expr.getSourceLocation(), ARGUMENT_TYPE_MISMATCH, fieldName, expectedType, argType);
      }
    }
  }
}
