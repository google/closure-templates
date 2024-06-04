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

import com.google.auto.value.AutoValue;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.MethodRef.MethodPureness;
import com.google.template.soy.jbcsrc.restricted.TypeInfo;
import com.google.template.soy.jbcsrc.shared.CompiledTemplate;
import com.google.template.soy.jbcsrc.shared.Names;
import com.google.template.soy.jbcsrc.shared.RenderContext;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.TemplateMetadata;
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

  static CompiledTemplateMetadata create(TemplateNode node) {
    return create(node.getTemplateName(), TemplateMetadata.buildTemplateType(node));
  }

  static CompiledTemplateMetadata create(CallBasicNode callNode) {
    return create(callNode.getCalleeName(), callNode.getStaticType());
  }

  private static CompiledTemplateMetadata create(String templateName, TemplateType templateType) {
    String className = Names.javaClassNameFromSoyTemplateName(templateName);
    TypeInfo type = TypeInfo.createClass(className);
    // Decide whether or not to use a positional style call signature.
    // Positional parameters are not possible to do if there are indirect calls since those may
    // require parameters not declared in our signature.
    // If there are no parameters, then there is no value in exploding into multiple functions
    // If the template is a Soy element, then we also need the `opt_data` object.
    boolean hasPositionalSignature =
        templateType.getDataAllCallSituations().isEmpty()
            // Skip positional signatures when there are no parameters.  This is not necessary for
            // correctness however in this case there is very little benefit in the positional
            // overload and so we can generate one fewer method by just not generating it.
            && !templateType.getActualParameters().isEmpty()
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
