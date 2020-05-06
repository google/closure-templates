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
package com.google.template.soy.soytree;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.types.SoyType;
import javax.annotation.Nullable;

/**
 * Interface defining the signature of a template, used by CheckTemplateCallsPass to validate calls.
 * This interface is implemented by {@link TemplateMetadata}.
 */
@AutoValue
public abstract class TemplateSignature {
  public abstract TemplateMetadata.Kind getTemplateKind();

  public abstract SanitizedContentKind getContentKind();

  public abstract boolean isStrictHtml();

  public abstract Visibility getVisibility();

  public abstract ImmutableList<Parameter> getParameters();

  public abstract ImmutableList<DataAllCallSituation> getDataAllCallSituations();

  public abstract String getIdentifierForDebugging();

  private static Builder builder() {
    return new AutoValue_TemplateSignature.Builder();
  }

  @AutoValue.Builder
  protected abstract static class Builder {

    protected abstract Builder setTemplateKind(TemplateMetadata.Kind templateKind);

    protected abstract Builder setContentKind(SanitizedContentKind sanitizedContentKind);

    protected abstract Builder setStrictHtml(boolean isStrictHtml);

    protected abstract Builder setVisibility(Visibility visibility);

    protected abstract Builder setParameters(ImmutableList<Parameter> parameters);

    protected abstract Builder setDataAllCallSituations(
        ImmutableList<DataAllCallSituation> dataAllCallSituations);

    protected abstract Builder setIdentifierForDebugging(String identifierForDebugging);

    protected abstract TemplateSignature build();
  }

  @AutoValue
  public abstract static class Parameter {
    private static Parameter fromTemplateMetadataParameter(TemplateMetadata.Parameter parameter) {
      return new AutoValue_TemplateSignature_Parameter(
          parameter.getName(), parameter.getType(), parameter.isRequired());
    }

    public abstract String getName();

    public abstract SoyType getType();

    public abstract boolean isRequired();
  }

  @AutoValue
  public abstract static class DataAllCallSituation {
    private static DataAllCallSituation fromTemplateMetadataDataAllCallSituation(
        TemplateMetadata.DataAllCallSituation dataAllCallSituation) {
      return new AutoValue_TemplateSignature_DataAllCallSituation(
          dataAllCallSituation.getTemplateName(),
          dataAllCallSituation.isDelCall(),
          dataAllCallSituation.getExplicitlyPassedParameters());
    }

    public abstract String getTemplateName();

    public abstract boolean isDelCall();

    public abstract ImmutableSet<String> getExplicitlyPassedParameters();
  }

  @Nullable
  public static TemplateSignature fromTemplateMetadata(
      @Nullable TemplateMetadata templateMetadata) {
    if (templateMetadata == null) {
      return null;
    }
    return builder()
        .setTemplateKind(templateMetadata.getTemplateKind())
        .setContentKind(templateMetadata.getContentKind())
        .setStrictHtml(templateMetadata.isStrictHtml())
        .setVisibility(templateMetadata.getVisibility())
        .setParameters(
            templateMetadata.getParameters().stream()
                .map(Parameter::fromTemplateMetadataParameter)
                .collect(ImmutableList.toImmutableList()))
        .setDataAllCallSituations(
            templateMetadata.getDataAllCallSituations().stream()
                .map(DataAllCallSituation::fromTemplateMetadataDataAllCallSituation)
                .collect(ImmutableList.toImmutableList()))
        .setIdentifierForDebugging(templateMetadata.getTemplateName())
        .build();
  }
}
