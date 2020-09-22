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

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.Streams.stream;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.ForOverride;
import com.google.errorprone.annotations.concurrent.LazyInit;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.base.internal.TemplateContentKind;
import com.google.template.soy.soytree.ParameterP;
import com.google.template.soy.soytree.SoyTypeP;
import javax.annotation.Nullable;

/** Template type, containing a list of named, typed parameters and a return type. */
@AutoValue
public abstract class TemplateType extends SoyType {
  public enum TemplateKind {
    BASIC,
    DELTEMPLATE,
    ELEMENT;
  }

  public abstract TemplateKind getTemplateKind();

  public abstract TemplateContentKind getContentKind();

  public abstract boolean isStrictHtml();

  public abstract ImmutableList<Parameter> getParameters();

  final ImmutableMap<String, SoyType> getParameterMap() {
    return stream(getParameters()).collect(toImmutableMap(Parameter::getName, Parameter::getType));
  }

  public abstract ImmutableList<DataAllCallSituation> getDataAllCallSituations();

  public abstract String getIdentifierForDebugging();

  public abstract boolean isInferredType();

  public abstract Builder toBuilder();

  public static Builder builder() {
    return new AutoValue_TemplateType.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setTemplateKind(TemplateKind templateKind);

    public abstract Builder setContentKind(TemplateContentKind sanitizedContentKind);

    public abstract Builder setStrictHtml(boolean isStrictHtml);

    public abstract Builder setParameters(ImmutableList<Parameter> parameters);

    public abstract Builder setDataAllCallSituations(
        ImmutableList<DataAllCallSituation> dataAllCallSituations);

    public abstract Builder setIdentifierForDebugging(String identifierForDebugging);

    public abstract Builder setInferredType(boolean isInferredType);

    public abstract TemplateType build();
  }

  /**
   * Represents minimal information about a template parameter.
   *
   * <p>This only represents normal parameters. Information about injected params or state variables
   * is not recorded.
   */
  @AutoValue
  public abstract static class Parameter {

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

    public static Builder builder() {
      return new AutoValue_TemplateType_Parameter.Builder();
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

    public abstract Builder toBuilder();

    /** If comparing parameters (ignoring description), normalize instances with this method. */
    public Parameter toComparable() {
      return getDescription() == null ? this : toBuilder().setDescription(null).build();
    }

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
    public static Builder builder() {
      return new AutoValue_TemplateType_DataAllCallSituation.Builder();
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

    /** Builder for {@link DataAllCallSituation} */
    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setTemplateName(String templateName);

      public abstract Builder setDelCall(boolean isDelCall);

      public abstract Builder setExplicitlyPassedParameters(ImmutableSet<String> parameters);

      public abstract DataAllCallSituation build();
    }
  }

  public static TemplateType declaredTypeOf(Iterable<Parameter> parameters, SoyType returnType) {
    SanitizedContentKind contentKind;
    if (returnType instanceof SanitizedType) {
      contentKind = ((SanitizedType) returnType).getContentKind();
    } else {
      // Only other valid type is string.
      contentKind = SanitizedContentKind.TEXT;
    }
    TemplateContentKind templateContentKind =
        TemplateContentKind.fromSanitizedContentKind(contentKind);
    return builder()
        // Declared templates can only be basic templates (no deltemplates/elements allowed).
        .setTemplateKind(TemplateKind.BASIC)
        .setContentKind(templateContentKind)
        // Declared HTML templates are implicitly strict. A separate check enforces that
        // non-strict templates may not be bound in template literals.
        .setStrictHtml(contentKind.isHtml())
        .setParameters(ImmutableList.copyOf(parameters))
        // data=all is banned on declared templates.
        .setDataAllCallSituations(ImmutableList.of())
        .setIdentifierForDebugging(stringRepresentation(parameters, templateContentKind))
        .setInferredType(false)
        .build();
  }

  @Override
  public final SoyType.Kind getKind() {
    return SoyType.Kind.TEMPLATE;
  }

  @Override
  final boolean doIsAssignableFromNonUnionType(
      SoyType srcType, UnknownAssignmentPolicy unknownPolicy) {
    if (srcType.getKind() == SoyType.Kind.TEMPLATE) {
      TemplateType srcTemplate = (TemplateType) srcType;
      // The source template type's arguments must be a superset of this type's arguments (possibly
      // containing some optional parameters omitted from this type).
      if (!srcTemplate.getParameterMap().keySet().containsAll(this.getParameterMap().keySet())) {
        return false;
      }
      for (Parameter srcParameter : srcTemplate.getParameters()) {
        if (!this.getParameterMap().containsKey(srcParameter.getName())) {
          if (srcParameter.isRequired()) {
            return false;
          }
        } else {
          SoyType thisParameterType = this.getParameterMap().get(srcParameter.getName());
          // Check that each argument of the source type is assignable FROM the corresponding
          // argument
          // of this type. This is because the parameter types are constraints; assignability of a
          // template type is only possible when the constraints of the from-type are narrower.
          if (!srcParameter.getType().isAssignableFromInternal(thisParameterType, unknownPolicy)) {
            return false;
          }
        }
      }
      // TODO(b/167574941): Add element support.
      SanitizedContentKind thisKind = this.getContentKind().getSanitizedContentKind();
      SanitizedContentKind srcKind = srcTemplate.getContentKind().getSanitizedContentKind();
      if (!thisKind.isAssignableFrom(srcKind)) {
        return false;
      }
      return true;
    }
    return false;
  }

  @Override
  public final String toString() {
    return stringRepresentation(getParameters(), getContentKind());
  }

  static String stringRepresentation(
      Iterable<Parameter> parameters, TemplateContentKind contentKind) {
    StringBuilder sb = new StringBuilder();
    sb.append("(");
    boolean first = true;
    for (Parameter parameter : parameters) {
      if (first) {
        first = false;
      } else {
        sb.append(", ");
      }
      sb.append(parameter.getName());
      if (!parameter.isRequired()) {
        sb.append("?");
      }
      sb.append(": ");
      sb.append(parameter.getType());
    }
    sb.append(") => ");
    sb.append(SanitizedType.getTypeForContentKind(contentKind.getSanitizedContentKind()));
    return sb.toString();
  }

  @Override
  final void doToProto(SoyTypeP.Builder builder) {
    Preconditions.checkState(
        !isInferredType(), "Only declared types may be serialized to proto form.");
    SoyTypeP.TemplateTypeP.Builder templateBuilder = builder.getTemplateBuilder();
    for (Parameter parameter : getParameters()) {
      // TODO(b/168821294): Stop setting this field once a new Kythe is deployed.
      templateBuilder
          .putParameterOld(parameter.getName(), parameter.getType().toProto())
          .addParameter(
              ParameterP.newBuilder()
                  .setName(parameter.getName())
                  .setType(parameter.getType().toProto())
                  .setRequired(parameter.isRequired())
                  .build());
    }
    SoyTypeP returnType =
        SanitizedType.getTypeForContentKind(getContentKind().getSanitizedContentKind()).toProto();
    // TODO(b/168821294): Stop setting this field once a new Kythe is deployed.
    templateBuilder.setReturnTypeOld(returnType.getPrimitive()).setReturnType(returnType);
  }

  @Override
  public final <T> T accept(SoyTypeVisitor<T> visitor) {
    return visitor.visit(this);
  }
}
