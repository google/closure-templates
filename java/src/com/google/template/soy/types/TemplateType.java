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

import static com.google.common.base.Functions.identity;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.stream.Collectors.joining;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.CaseFormat;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.ForOverride;
import com.google.errorprone.annotations.concurrent.LazyInit;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.base.internal.TemplateContentKind;
import com.google.template.soy.base.internal.TemplateContentKind.ElementContentKind;
import com.google.template.soy.soytree.ParameterP;
import com.google.template.soy.soytree.SoyTypeP;
import com.google.template.soy.types.SanitizedType.ElementType;
import java.util.Map;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** Template type, containing a list of named, typed parameters and a return type. */
@AutoValue
public abstract class TemplateType extends SoyType {

  public static final String ATTRIBUTES_HIDDEN_PARAM_NAME = "__soyInternalAttributes";
  private static final Parameter ATTRIBUTES_HIDDEN_PARAM =
      Parameter.builder()
          .setName(ATTRIBUTES_HIDDEN_PARAM_NAME)
          .setType(SanitizedType.AttributesType.getInstance())
          .setKind(ParameterKind.PARAM)
          .setRequired(false)
          .setImplicit(true)
          .build();

  /** The kind of template. */
  public enum TemplateKind {
    BASIC,
    DELTEMPLATE,
    ELEMENT;
  }

  /** The kind of template parameter. */
  public enum ParameterKind {
    PARAM,
    ATTRIBUTE;

    public ParameterP.KindP toProto() {
      return ParameterP.KindP.valueOf(name());
    }

    public static ParameterKind fromProto(ParameterP.KindP proto) {
      if (proto == null || proto == ParameterP.KindP.DEFAULT) {
        return PARAM;
      }
      return valueOf(proto.name());
    }
  }

  public abstract boolean getAllowExtraAttributes();

  public abstract ImmutableSet<String> getReservedAttributes();

  public abstract TemplateKind getTemplateKind();

  public abstract TemplateContentKind getContentKind();

  public abstract boolean isStrictHtml();

  public abstract ImmutableList<Parameter> getParameters();

  /** The same as {@link #getParameters} but also includes hidden parameters. */
  @Memoized
  public ImmutableList<Parameter> getActualParameters() {
    if (getAllowExtraAttributes()) {
      return ImmutableList.<Parameter>builder()
          .addAll(getParameters())
          .add(ATTRIBUTES_HIDDEN_PARAM)
          .build();
    }
    return getParameters();
  }

  public final ImmutableMap<String, SoyType> getParameterMap() {
    return getParameters().stream().collect(toImmutableMap(Parameter::getName, Parameter::getType));
  }

  public abstract ImmutableList<DataAllCallSituation> getDataAllCallSituations();

  public abstract String getIdentifierForDebugging();

  public abstract Builder toBuilder();

  public static Builder builder() {
    return new AutoValue_TemplateType.Builder();
  }

  /** Builder pattern. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setAllowExtraAttributes(boolean allowAttributes);

    public abstract Builder setReservedAttributes(ImmutableSet<String> attributes);

    public abstract Builder setTemplateKind(TemplateKind templateKind);

    public abstract Builder setContentKind(TemplateContentKind sanitizedContentKind);

    public abstract Builder setStrictHtml(boolean isStrictHtml);

    public abstract Builder setParameters(ImmutableList<Parameter> parameters);

    public abstract Builder setDataAllCallSituations(
        ImmutableList<DataAllCallSituation> dataAllCallSituations);

    public abstract Builder setIdentifierForDebugging(String identifierForDebugging);

    abstract TemplateType autoBuild();

    public TemplateType build() {
      TemplateType built = autoBuild();
      if (built.getParameters().stream()
          .anyMatch(p -> p.getName().equals(ATTRIBUTES_HIDDEN_PARAM_NAME))) {
        throw new IllegalStateException();
      }
      return built;
    }
  }

  /**
   * Represents minimal information about a template parameter.
   *
   * <p>This only represents normal parameters. Information about injected params or state variables
   * is not recorded.
   */
  @AutoValue
  public abstract static class Parameter {

    private static final Pattern ATTR_NAME = Pattern.compile("^[a-z_][a-z_\\d]*(-[a-z_\\d]+)*$");

    public static String attrToParamName(String attrName) {
      return CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_CAMEL, attrName);
    }

    public static String paramToAttrName(String paramName) {
      return CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_HYPHEN, paramName);
    }

    public static boolean isValidAttrName(String attrName) {
      return ATTR_NAME.matcher(attrName).matches();
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

    public static Builder builder() {
      return new AutoValue_TemplateType_Parameter.Builder();
    }

    public abstract String getName();

    public abstract ParameterKind getKind();

    // TODO(lukes): this will likely not work once we start compiling templates separately,
    // especially if we want to start pruning the proto descriptors required by the compiler.
    public SoyType getType() {
      return getTypeWrapper().getType();
    }

    abstract LazyTypeWrapper getTypeWrapper();

    public abstract boolean isRequired();

    public abstract boolean isImplicit();

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

      public abstract Builder setKind(ParameterKind kind);

      abstract Builder setTypeWrapper(LazyTypeWrapper typeWrapper);

      public abstract Builder setRequired(boolean isRequired);

      public abstract Builder setImplicit(boolean isImplicit);

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
    TemplateContentKind templateContentKind = fromType(returnType);
    SanitizedContentKind contentKind = templateContentKind.getSanitizedContentKind();
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
        .setIdentifierForDebugging(
            stringRepresentation(parameters, templateContentKind, ImmutableSet.of()))
        .setAllowExtraAttributes(false)
        .setReservedAttributes(ImmutableSet.of())
        .build();
  }

  public static TemplateContentKind fromType(SoyType type) {
    if (type instanceof ElementType) {
      return ElementContentKind.valueOf(((ElementType) type).getTagName());
    } else if (type instanceof SanitizedType) {
      return TemplateContentKind.fromSanitizedContentKind(((SanitizedType) type).getContentKind());
    } else {
      return TemplateContentKind.fromSanitizedContentKind(SanitizedContentKind.TEXT);
    }
  }

  @Override
  public final SoyType.Kind getKind() {
    return SoyType.Kind.TEMPLATE;
  }

  @Override
  final boolean doIsAssignableFromNonUnionType(
      SoyType srcType, UnknownAssignmentPolicy unknownPolicy) {
    if (srcType.getKind() != SoyType.Kind.TEMPLATE) {
      return false;
    }

    TemplateType srcTemplate = (TemplateType) srcType;

    Map<String, Parameter> thisParams =
        getParameters().stream().collect(toImmutableMap(Parameter::getName, identity()));
    Map<String, Parameter> srcParams =
        srcTemplate.getParameters().stream()
            .collect(toImmutableMap(Parameter::getName, identity()));

    // The source template type's arguments must be a superset of this type's arguments (possibly
    // containing some optional parameters omitted from this type).
    for (Parameter thisParam : getParameters()) {
      if (thisParam.getKind() == ParameterKind.ATTRIBUTE) {
        if (!(srcParams.containsKey(thisParam.getName())
            && srcParams.get(thisParam.getName()).getKind() == ParameterKind.ATTRIBUTE)) {
          return false;
        }
      } else {
        if (!srcParams.containsKey(thisParam.getName())) {
          return false;
        }
      }
    }

    for (Parameter srcParam : srcTemplate.getParameters()) {
      Parameter thisParam = thisParams.get(srcParam.getName());
      if (thisParam == null) {
        if (srcParam.isRequired()) {
          return false;
        }
      } else {
        // Check that each argument of the source type is assignable FROM the corresponding
        // argument of this type. This is because the parameter types are constraints; assignability
        // of a template type is only possible when the constraints of the from-type are narrower.
        if (!srcParam.getType().isAssignableFromInternal(thisParam.getType(), unknownPolicy)) {
          return false;
        }
      }
    }
    return this.getContentKind().isAssignableFrom(srcTemplate.getContentKind());
  }

  @Override
  public final String toString() {
    return stringRepresentation(getParameters(), getContentKind(), getReservedAttributes());
  }

  static String stringRepresentation(
      Iterable<Parameter> parameters,
      TemplateContentKind contentKind,
      ImmutableSet<String> reservedAttributes) {
    StringBuilder sb = new StringBuilder();
    sb.append("(");
    boolean first = true;
    for (Parameter parameter : parameters) {
      if (first) {
        first = false;
      } else {
        sb.append(", ");
      }
      String name = parameter.getName();
      if (parameter.getKind() == ParameterKind.ATTRIBUTE) {
        name = "@" + Parameter.paramToAttrName(name);
      }
      sb.append(name);
      if (!parameter.isRequired()) {
        sb.append("?");
      }
      sb.append(": ");
      sb.append(parameter.getType());
    }
    if (!reservedAttributes.isEmpty()) {
      if (!first) {
        sb.append(",");
      }
      sb.append("*-{")
          .append(reservedAttributes.stream().map(a -> "@" + a).collect(joining(", ")))
          .append("}");
    }
    sb.append(") => ");
    sb.append(contentKind.asAttributeValue());
    return sb.toString();
  }

  @Override
  final void doToProto(SoyTypeP.Builder builder) {
    SoyTypeP.TemplateTypeP.Builder templateBuilder = builder.getTemplateBuilder();
    for (Parameter parameter : getParameters()) {
      templateBuilder.addParameter(
          ParameterP.newBuilder()
              .setName(parameter.getName())
              .setKind(parameter.getKind().toProto())
              .setType(parameter.getType().toProto())
              .setRequired(parameter.isRequired())
              .setImplicit(parameter.isImplicit())
              .build());
    }
    SoyTypeP returnType = templateContentKindToType(getContentKind()).toProto();
    if (getAllowExtraAttributes()) {
      returnType =
          SoyTypeP.newBuilder(returnType)
              .setHtml(
                  returnType.getHtml().toBuilder()
                      .setAllowExtraAttributes(true)
                      .addAllReservedAttributes(getReservedAttributes()))
              .build();
    }
    templateBuilder.setReturnType(returnType);
  }

  private static SoyType templateContentKindToType(TemplateContentKind kind) {
    if (kind instanceof ElementContentKind) {
      return ElementType.getInstance(((ElementContentKind) kind).getTagName());
    } else {
      return SanitizedType.getTypeForContentKind(kind.getSanitizedContentKind());
    }
  }

  @Override
  public final <T> T accept(SoyTypeVisitor<T> visitor) {
    return visitor.visit(this);
  }
}
