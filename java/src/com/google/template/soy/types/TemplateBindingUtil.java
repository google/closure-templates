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
package com.google.template.soy.types;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.error.SoyErrors;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Utility for generating the return type of a template-type expression after the bind() method is
 * called on it.
 */
public final class TemplateBindingUtil {
  private static final SoyErrorKind PARAMETER_NAME_MISMATCH =
      SoyErrorKind.of(
          "Cannot bind parameter named `{0}` to template of type `{1}`; no such parameter.{2}",
          StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind PARAMETER_TYPE_MISMATCH =
      SoyErrorKind.of(
          "Cannot bind parameter named `{0}` to template of type `{1}`; expected `{2}` but found"
              + " `{3}`.");

  private static final SoyErrorKind PARAMETER_ALREADY_BOUND =
      SoyErrorKind.of("Parameter named `{0}` already bound to template.");

  public static SoyType bindParameters(
      SoyType base,
      RecordType parameterType,
      SoyTypeRegistry typeRegistry,
      ErrorReporter errorReporter,
      SourceLocation whereToReportErrors) {
    checkArgument(
        SoyTypes.isKindOrUnionOfKinds(
            base, ImmutableSet.of(SoyType.Kind.TEMPLATE, SoyType.Kind.NAMED_TEMPLATE)));
    Set<SoyType> types = new HashSet<>();
    for (SoyType baseType : SoyTypes.expandUnions(base)) {
      switch (baseType.getKind()) {
        case TEMPLATE:
          types.add(
              bindParametersToTemplate(
                  (TemplateType) baseType,
                  parameterType,
                  typeRegistry,
                  errorReporter,
                  whereToReportErrors));
          break;
        case NAMED_TEMPLATE:
          types.add(
              bindParametersToNamedTemplate(
                  (NamedTemplateType) baseType,
                  parameterType,
                  typeRegistry,
                  errorReporter,
                  whereToReportErrors));
          break;
        default:
          throw new AssertionError();
      }
    }
    return typeRegistry.getOrCreateUnionType(types);
  }

  /**
   * Helper static utility for constructing types with bound parameters. Performs validations and
   * reports errors if the parameters don't match the expected names/types. If any errors are
   * reported, returns the ErrorType instead of a TemplateType.
   */
  private static SoyType bindParametersToTemplate(
      TemplateType base,
      RecordType parameters,
      SoyTypeRegistry typeRegistry,
      ErrorReporter errorReporter,
      SourceLocation whereToReportErrors) {
    Set<String> unboundParameters = new HashSet<>(base.getParameterMap().keySet());
    boolean reportedErrors = false;
    for (RecordType.Member member : parameters.getMembers()) {
      if (!base.getParameterMap().containsKey(member.name())) {
        String didYouMeanMessage =
            SoyErrors.getDidYouMeanMessage(base.getParameterMap().keySet(), member.name());
        errorReporter.report(
            whereToReportErrors, PARAMETER_NAME_MISMATCH, member.name(), base, didYouMeanMessage);
        reportedErrors = true;
        continue;
      }
      if (!base.getParameterMap().get(member.name()).isAssignableFrom(member.type())) {
        errorReporter.report(
            whereToReportErrors,
            PARAMETER_TYPE_MISMATCH,
            member.name(),
            base,
            base.getParameterMap().get(member.name()),
            member.type());
        reportedErrors = true;
      }
      unboundParameters.remove(member.name());
    }
    if (reportedErrors) {
      return ErrorType.getInstance();
    }
    TemplateType.Builder builder = base.toBuilder();
    ImmutableList<TemplateType.Parameter> newParameters =
        base.getParameters().stream()
            .filter((parameter) -> unboundParameters.contains(parameter.getName()))
            .collect(toImmutableList());
    builder.setIdentifierForDebugging(
        TemplateType.stringRepresentation(newParameters, base.getContentKind()));
    builder.setParameters(newParameters);
    return typeRegistry.internTemplateType(builder.build());
  }

  private static SoyType bindParametersToNamedTemplate(
      NamedTemplateType base,
      RecordType parameters,
      SoyTypeRegistry typeRegistry,
      ErrorReporter errorReporter,
      SourceLocation whereToReportErrors) {
    Optional<RecordType> alreadyBound = base.getBoundParameters();
    RecordType mergedParameters = null;
    boolean reportedErrors = false;
    if (alreadyBound.isPresent()) {
      for (String name : parameters.getMemberNames()) {
        if (alreadyBound.get().getMemberType(name) != null) {
          errorReporter.report(whereToReportErrors, PARAMETER_ALREADY_BOUND, name);
          reportedErrors = true;
        }
      }
      if (!reportedErrors) {
        mergedParameters =
            typeRegistry.getOrCreateRecordType(
                Iterables.concat(alreadyBound.get().getMembers(), parameters.getMembers()));
      }
    } else {
      mergedParameters = parameters;
    }
    if (reportedErrors) {
      return ErrorType.getInstance();
    }
    return NamedTemplateType.createWithBoundParameters(base.getTemplateName(), mergedParameters);
  }

  /** Non-instantiable. */
  private TemplateBindingUtil() {}
}
