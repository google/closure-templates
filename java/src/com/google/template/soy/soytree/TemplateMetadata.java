/*
 * Copyright 2018 Google Inc.
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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.ForOverride;
import com.google.errorprone.annotations.concurrent.LazyInit;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.TemplateType;
import com.google.template.soy.types.UnknownType;
import javax.annotation.Nullable;

/**
 * An abstract representation of a template that provides the minimal amount of information needed
 * compiling against dependency templates.
 *
 * <p>When compiling with dependencies the compiler needs to examine certain information from
 * dependent templates in order to validate calls and escape call sites. Traditionally, the Soy
 * compiler accomplished this by having each compilation parse all transitive dependencies. This is
 * an expensive solution. So instead of that we instead use this object to represent the minimal
 * information we need about dependencies.
 *
 * <p>The APIs on this class mirror ones available on {@link TemplateNode}.
 */
@AutoValue
public abstract class TemplateMetadata {

  /** Builds a Template from a parsed TemplateNode. */
  public static TemplateMetadata fromTemplate(TemplateNode template) {
    TemplateMetadata.Builder builder =
        builder()
            .setTemplateName(template.getTemplateName())
            .setSourceLocation(template.getSourceLocation())
            .setSoyFileKind(SoyFileKind.SRC)
            .setSoyElement(
                SoyElementMetadataP.newBuilder()
                    .setIsSoyElement(template instanceof TemplateElementNode)
                    .build())
            .setContentKind(template.getContentKind())
            .setStrictHtml(template.isStrictHtml())
            .setDelPackageName(template.getDelPackageName())
            .setVisibility(template.getVisibility())
            .setParameters(Parameter.directParametersFromTemplate(template))
            .setDataAllCallSituations(DataAllCallSituation.fromTemplate(template));
    // In various conditions such as Conformance tests, this can be null.
    if (template.getHtmlElementMetadata() != null) {
      builder.setHtmlElement(template.getHtmlElementMetadata());
    }
    switch (template.getKind()) {
      case TEMPLATE_BASIC_NODE:
        builder.setTemplateKind(TemplateType.TemplateKind.BASIC);
        break;
      case TEMPLATE_DELEGATE_NODE:
        builder.setTemplateKind(TemplateType.TemplateKind.DELTEMPLATE);
        TemplateDelegateNode deltemplate = (TemplateDelegateNode) template;
        builder.setDelTemplateName(deltemplate.getDelTemplateName());
        builder.setDelTemplateVariant(deltemplate.getDelTemplateVariant());
        break;
      case TEMPLATE_ELEMENT_NODE:
        builder.setTemplateKind(TemplateType.TemplateKind.ELEMENT);
        break;
      default:
        throw new AssertionError("unexpected template kind: " + template.getKind());
    }
    return builder.build();
  }

  public static TemplateMetadata.Builder builder() {
    return new AutoValue_TemplateMetadata.Builder();
  }

  /**
   * Represents minimal information about a template parameter.
   *
   * <p>This only represents normal parameters. Information about injected params or state variables
   * is not recorded.
   */
  @AutoValue
  public abstract static class Parameter {
    public static Parameter fromParam(TemplateParam param) {
      return builder()
          .setName(param.name())
          // Proto imports when compiler is not given proto descriptors will cause type to be unset.
          .setType(param.hasType() ? param.type() : UnknownType.getInstance())
          .setRequired(param.isRequired())
          .setDescription(param.desc())
          .build();
    }

    /**
     * A simple wrapper so that Parameter continues to have correct equals/hashCode methods even
     * though we might only lazily calculate the type.
     */
    abstract static class LazyTypeWrapper {
      static LazyTypeWrapper constant(final SoyType type) {
        return new LazyTypeWrapper() {
          @Override
          SoyType getType() {
            return type;
          }
        };
      }

      static LazyTypeWrapper fromSupplier(final Supplier<SoyType> typeSupplier) {
        return new LazyTypeWrapper() {
          @LazyInit SoyType type;

          @Override
          SoyType getType() {
            SoyType local = type;
            if (local == null) {
              local = typeSupplier.get();
              if (local == null) {
                throw new IllegalStateException("typeSupplier returned null");
              }
              this.type = local;
            }
            return local;
          }
        };
      }

      @ForOverride
      abstract SoyType getType();

      @Override
      public final int hashCode() {
        return getType().hashCode();
      }

      @Override
      public final boolean equals(Object other) {
        return other instanceof LazyTypeWrapper
            && ((LazyTypeWrapper) other).getType().equals(getType());
      }

      @Override
      public String toString() {
        return getType().toString();
      }
    }

    static ImmutableList<Parameter> directParametersFromTemplate(TemplateNode node) {
      ImmutableList.Builder<Parameter> params = ImmutableList.builder();
      for (TemplateParam param : node.getParams()) {
        params.add(fromParam(param));
      }
      return params.build();
    }

    public static Builder builder() {
      return new AutoValue_TemplateMetadata_Parameter.Builder();
    }

    public abstract String getName();

    // TODO(lukes): this will likely not work once we start compiling templates separately,
    // especially if we want to start pruning the proto descriptors required by the compiler.
    public SoyType getType() {
      return getTypeWrapper().getType();
    }

    abstract LazyTypeWrapper getTypeWrapper();

    public abstract boolean isRequired();

    /**
     * Note that description is not serialized by TemplateMetadataSerializer so this field will be
     * null if this instance is created via deserialization.
     */
    @Nullable
    public abstract String getDescription();

    /** If comparing parameters (ignoring description), normalize instances with this method. */
    public Parameter toComparable() {
      return getDescription() == null ? this : toBuilder().setDescription(null).build();
    }

    public abstract Builder toBuilder();

    /** Builder for {@link Parameter} */
    @AutoValue.Builder
    public abstract static class Builder {

      public abstract Builder setName(String name);

      public Builder setTypeLazily(final Supplier<SoyType> typeSupplier) {
        return setTypeWrapper(LazyTypeWrapper.fromSupplier(typeSupplier));
      }

      public Builder setType(final SoyType type) {
        return setTypeWrapper(LazyTypeWrapper.constant(type));
      }

      abstract Builder setTypeWrapper(LazyTypeWrapper typeWrapper);

      public abstract Builder setRequired(boolean isRequired);

      public abstract Builder setDescription(String description);

      public abstract Parameter build();
    }
  }

  /**
   * Represents information about a {@code data="all"} calls to a template.
   *
   * <p>This doesn't necessarily represent a single call site since if a template is called multiple
   * times in ways that aren't different according to this data structure we only record it once.
   */
  @AutoValue
  public abstract static class DataAllCallSituation {
    static ImmutableList<DataAllCallSituation> fromTemplate(TemplateNode node) {
      ImmutableSet.Builder<DataAllCallSituation> calls = ImmutableSet.builder();
      for (CallNode call : SoyTreeUtils.getAllNodesOfType(node, CallNode.class)) {
        if (call.isPassingAllData()) {
          DataAllCallSituation.Builder builder = builder();
          ImmutableSet.Builder<String> explicitlyPassedParams = ImmutableSet.builder();
          for (CallParamNode param : call.getChildren()) {
            explicitlyPassedParams.add(param.getKey().identifier());
          }
          builder.setExplicitlyPassedParameters(explicitlyPassedParams.build());
          switch (call.getKind()) {
            case CALL_BASIC_NODE:
              builder.setDelCall(false).setTemplateName(((CallBasicNode) call).getCalleeName());
              break;
            case CALL_DELEGATE_NODE:
              builder
                  .setDelCall(true)
                  .setTemplateName(((CallDelegateNode) call).getDelCalleeName());
              break;
            default:
              throw new AssertionError("unexpected call kind: " + call.getKind());
          }
          calls.add(builder.build());
        }
      }
      return calls.build().asList();
    }

    public static Builder builder() {
      return new AutoValue_TemplateMetadata_DataAllCallSituation.Builder();
    }

    /** The fully qualified name of the called template. */
    public abstract String getTemplateName();

    /** Whether this is a delcall or not. */
    public abstract boolean isDelCall();

    /**
     * Records the names of the parameters that were explicitly.
     *
     * <p>This is necessary to calculate indirect parameters.
     */
    public abstract ImmutableSet<String> getExplicitlyPassedParameters();

    /** Builder for {@link CallSituation} */
    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setTemplateName(String templateName);

      public abstract Builder setDelCall(boolean isDelCall);

      public abstract Builder setExplicitlyPassedParameters(ImmutableSet<String> parameters);

      public abstract DataAllCallSituation build();
    }
  }

  public abstract SoyFileKind getSoyFileKind();

  /**
   * The source location of the template. For non {@code SOURCE} templates this will merely refer to
   * the file path, line and column information isn't recorded.
   */
  public abstract SourceLocation getSourceLocation();

  @Nullable
  public abstract HtmlElementMetadataP getHtmlElement();

  @Nullable
  public abstract SoyElementMetadataP getSoyElement();

  public abstract TemplateType.TemplateKind getTemplateKind();

  public abstract String getTemplateName();

  /** Guaranteed to be non-null for deltemplates, null otherwise. */
  @Nullable
  public abstract String getDelTemplateName();

  @Nullable
  public abstract String getDelTemplateVariant();

  public abstract SanitizedContentKind getContentKind();

  public abstract boolean isStrictHtml();

  public abstract Visibility getVisibility();

  @Nullable
  public abstract String getDelPackageName();

  /** The Parameters defined directly on the template. Includes {@code $ij} parameters. */
  public abstract ImmutableList<Parameter> getParameters();

  /**
   * The unique template calls that are performed by this template.
   *
   * <p>This is needed to calculate information about transitive parameters.
   */
  public abstract ImmutableList<DataAllCallSituation> getDataAllCallSituations();

  public abstract Builder toBuilder();

  /** Builder for {@link TemplateMetadata} */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setSoyFileKind(SoyFileKind location);

    public abstract Builder setSourceLocation(SourceLocation location);

    public abstract Builder setHtmlElement(HtmlElementMetadataP isHtml);

    public abstract Builder setSoyElement(SoyElementMetadataP isSoyEl);

    public abstract Builder setTemplateKind(TemplateType.TemplateKind kind);

    public abstract Builder setTemplateName(String templateName);

    public abstract Builder setDelTemplateName(String delTemplateName);

    public abstract Builder setDelTemplateVariant(String delTemplateVariant);

    public abstract Builder setContentKind(SanitizedContentKind contentKind);

    public abstract Builder setStrictHtml(boolean strictHtml);

    public abstract Builder setDelPackageName(@Nullable String delPackageName);

    public abstract Builder setVisibility(Visibility visibility);

    public abstract Builder setParameters(ImmutableList<Parameter> parameters);

    public abstract Builder setDataAllCallSituations(
        ImmutableList<DataAllCallSituation> dataAllCallSituations);

    public final TemplateMetadata build() {
      TemplateMetadata built = autobuild();
      if (built.getTemplateKind() == TemplateType.TemplateKind.DELTEMPLATE) {
        checkState(built.getDelTemplateName() != null, "Deltemplates must have a deltemplateName");
        checkState(
            built.getDelTemplateVariant() != null, "Deltemplates must have a deltemplateName");
      } else {
        checkState(
            built.getDelTemplateVariant() == null, "non-Deltemplates must not have a variant");
        checkState(
            built.getDelTemplateName() == null, "non-Deltemplates must not have a deltemplateName");
      }
      return built;
    }

    abstract TemplateMetadata autobuild();
  }

  public static TemplateType asTemplateType(TemplateMetadata templateMetadata) {
    return TemplateType.builder()
        .setTemplateKind(templateMetadata.getTemplateKind())
        .setContentKind(templateMetadata.getContentKind())
        .setStrictHtml(templateMetadata.isStrictHtml())
        .setParameters(
            templateMetadata.getParameters().stream()
                .map(
                    (param) ->
                        TemplateType.Parameter.create(
                            param.getName(), param.getType(), param.isRequired()))
                .collect(toImmutableList()))
        .setDataAllCallSituations(
            templateMetadata.getDataAllCallSituations().stream()
                .map(
                    (dataAllCallSituation) ->
                        TemplateType.DataAllCallSituation.create(
                            dataAllCallSituation.getTemplateName(),
                            dataAllCallSituation.isDelCall(),
                            dataAllCallSituation.getExplicitlyPassedParameters()))
                .collect(toImmutableList()))
        .setIdentifierForDebugging(templateMetadata.getTemplateName())
        .setInferredType(true)
        .build();
  }
}
