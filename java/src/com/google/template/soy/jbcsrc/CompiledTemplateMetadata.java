/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.jbcsrc;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.auto.value.AutoValue;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.MethodRef.MethodPureness;
import com.google.template.soy.jbcsrc.restricted.TypeInfo;
import com.google.template.soy.jbcsrc.shared.CompiledTemplate;
import com.google.template.soy.jbcsrc.shared.Names;
import com.google.template.soy.jbcsrc.shared.RenderContext;
import com.google.template.soy.passes.IndirectParamsCalculator;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.FileSetMetadata;
import com.google.template.soy.soytree.Metadata;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.Visibility;
import com.google.template.soy.types.TemplateType;
import java.util.Arrays;
import java.util.Optional;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

/**
 * Information about a compiled template.
 *
 * <p>This should contain basic information about a single template that will be useful for
 * generating that template as well as calls to the template.
 */
@AutoValue
abstract class CompiledTemplateMetadata {

  /**
   * For modifiable templates, this is appended to the default implementation and the associated
   * template method.
   */
  public static final String DEFAULT_IMPL_JBC_CLASS_SUFFIX = "__modifiable_default_impl__";

  /**
   * The {@link Method} signature of the {@link CompiledTemplate#render(ParamStore,ParamStore,
   * AdvisingAppendable, RenderContext)} method.
   */
  static Method createRenderMethod(String methodName) {
    return new Method(
        methodName,
        Type.getMethodDescriptor(
            BytecodeUtils.STACK_FRAME_TYPE,
            BytecodeUtils.STACK_FRAME_TYPE,
            BytecodeUtils.PARAM_STORE_TYPE,
            BytecodeUtils.LOGGING_ADVISING_APPENDABLE_TYPE,
            BytecodeUtils.RENDER_CONTEXT_TYPE));
  }

  /** Generates a method signature for a positional style call to the given template. */
  private static Method createPositionalRenderMethod(String methodName, TemplateType templateType) {
    Type[] parameters = new Type[templateType.getActualParameters().size() + 3];
    parameters[0] = BytecodeUtils.STACK_FRAME_TYPE;
    Arrays.fill(parameters, 1, parameters.length - 2, BytecodeUtils.SOY_VALUE_PROVIDER_TYPE);
    parameters[parameters.length - 2] = BytecodeUtils.LOGGING_ADVISING_APPENDABLE_TYPE;
    parameters[parameters.length - 1] = BytecodeUtils.RENDER_CONTEXT_TYPE;
    return new Method(
        methodName, Type.getMethodDescriptor(BytecodeUtils.STACK_FRAME_TYPE, parameters));
  }

  static boolean isPrivateCall(CallBasicNode call) {
    return call.getNearestAncestor(SoyFileNode.class).getTemplates().stream()
        .anyMatch(
            t ->
                t.getVisibility() == Visibility.PRIVATE
                    && t.getTemplateName().equals(call.getCalleeName()));
  }

  static boolean isPrivateReference(SoyNode callContext, TemplateLiteralNode call) {
    return callContext.getNearestAncestor(SoyFileNode.class).getTemplates().stream()
        .anyMatch(
            t ->
                t.getVisibility() == Visibility.PRIVATE
                    && t.getTemplateName().equals(call.getResolvedName()));
  }

  /** The {@link Method} signature of the {@code static CompiledTemplate template()} method. */
  static Method createTemplateMethod(String methodName) {
    return new Method(methodName, Type.getMethodDescriptor(BytecodeUtils.COMPILED_TEMPLATE_TYPE));
  }

  static CompiledTemplateMetadata create(TemplateNode node, FileSetMetadata fileSetMetadata) {
    return create(node.getTemplateName(), Metadata.buildTemplateType(node), fileSetMetadata);
  }

  static CompiledTemplateMetadata create(CallBasicNode callNode, FileSetMetadata fileSetMetadata) {
    return create(callNode.getCalleeName(), callNode.getStaticType(), fileSetMetadata);
  }

  private static CompiledTemplateMetadata create(
      String templateName, TemplateType templateType, FileSetMetadata fileSetMetadata) {
    String className = Names.javaClassNameFromSoyTemplateName(templateName);
    TypeInfo type = TypeInfo.createClass(className);
    var params =
        new IndirectParamsCalculator(fileSetMetadata).calculateIndirectParams(templateType);
    // Check if there is any indirect param that is not declared by this template.
    boolean hasUndeclaredIndirectParams;
    if (params.indirectParams.isEmpty()) {
      hasUndeclaredIndirectParams = false;
    } else {
      var declaredParamNames =
          templateType.getActualParameters().stream()
              .map(p -> p.getName())
              .collect(toImmutableSet());
      hasUndeclaredIndirectParams =
          params.indirectParams.keySet().stream().anyMatch(p -> !declaredParamNames.contains(p));
    }

    // Decide whether or not to use a positional style call signature.
    // Positional parameters are not possible to do if there are any indirect parameters that are
    // not declared by this template.  To satisfy those we must allow passing a ParamStore.
    // We could allow for those but it would require changing the TemplateType to declare all
    // indirect parameters as optional in the template signature.
    boolean hasPositionalSignature =
        !params.mayHaveExternalParams()
            && !hasUndeclaredIndirectParams
            // only basic/element templates are supported for now.
            // deltemplates require the object style to support the relatively weak type checking we
            // perform on them.
            && templateType.getTemplateKind() != TemplateType.TemplateKind.DELTEMPLATE
            // Modifiable template functions must uniformly implement the CompiledTemplates
            // functional interface that is used in the implementation selection map.
            && !templateType.isModifiable()
            && !templateType.isModifying();
    String methodName = Names.renderMethodNameFromSoyTemplateName(templateName);
    return builder()
        .setRenderMethod(
            MethodRef.createStaticMethod(
                    type,
                    createRenderMethod(
                        methodName
                            + (templateType.isModifiable() ? DEFAULT_IMPL_JBC_CLASS_SUFFIX : "")),
                    MethodPureness.NON_PURE)
                .asNonJavaNullable())
        .setPositionalRenderMethod(
            Optional.ofNullable(
                hasPositionalSignature
                    ? MethodRef.createStaticMethod(
                            type,
                            createPositionalRenderMethod(methodName, templateType),
                            MethodPureness.NON_PURE)
                        .asNonJavaNullable()
                    : null))
        .setModifiableSelectMethod(
            Optional.ofNullable(
                templateType.isModifiable()
                    ? MethodRef.createStaticMethod(
                            type, createRenderMethod(methodName), MethodPureness.NON_PURE)
                        .asCheap()
                        .asNonJavaNullable()
                    : null))
        .setTemplateMethod(
            MethodRef.createStaticMethod(
                    type, createTemplateMethod(methodName), MethodPureness.PURE)
                .asCheap()
                .asNonJavaNullable())
        .setDefaultModTemplateMethod(
            Optional.ofNullable(
                templateType.isModifiable()
                    ? MethodRef.createStaticMethod(
                            type,
                            createTemplateMethod(methodName + DEFAULT_IMPL_JBC_CLASS_SUFFIX),
                            MethodPureness.NON_PURE)
                        .asCheap()
                        .asNonJavaNullable()
                    : null))
        .setTemplateType(templateType)
        .setTypeInfo(type)
        .build();
  }

  /**
   * The {@code static RenderResult render(SoyRecord,SoyRecord, LoggingAdvisingAppendable,
   * RenderContext)} method.
   */
  abstract MethodRef renderMethod();

  /**
   * The {@code static RenderResult render(SoyValueProvider,...,SoyValueProvider,SoyRecord,
   * LoggingAdvisingAppendable, RenderContext)} overload method.
   */
  abstract Optional<MethodRef> positionalRenderMethod();

  abstract Optional<MethodRef> modifiableSelectMethod();

  /**
   * The main {@code static CompiledTemplate template()} method. For modifiable templates, this will
   * point to the implementation selection shim.
   */
  abstract MethodRef templateMethod();

  /** For modifiable templates, will point to the default implementation method. */
  abstract Optional<MethodRef> defaultModTemplateMethod();

  boolean hasPositionalSignature() {
    return positionalRenderMethod().isPresent();
  }

  abstract TemplateType templateType();

  /** The name of the compiled template class. */
  abstract TypeInfo typeInfo();

  static Builder builder() {
    return new AutoValue_CompiledTemplateMetadata.Builder();
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setRenderMethod(MethodRef value);

    abstract Builder setPositionalRenderMethod(Optional<MethodRef> value);

    abstract Builder setModifiableSelectMethod(Optional<MethodRef> value);

    abstract Builder setTemplateMethod(MethodRef value);

    abstract Builder setDefaultModTemplateMethod(Optional<MethodRef> value);

    abstract Builder setTemplateType(TemplateType value);

    abstract Builder setTypeInfo(TypeInfo value);

    abstract CompiledTemplateMetadata build();
  }
}
