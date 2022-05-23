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
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.TypeInfo;
import com.google.template.soy.jbcsrc.shared.CompiledTemplate;
import com.google.template.soy.jbcsrc.shared.Names;
import com.google.template.soy.jbcsrc.shared.RenderContext;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.TemplateMetadata;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.types.TemplateType;
import java.util.Optional;
import java.util.stream.Stream;
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
   * The {@link Method} signature of the {@link
   * CompiledTemplate#render(SoyRecord,SoyRecord,AdvisingAppendable, RenderContext)} method.
   */
  static final Method createRenderMethod(String methodName) {
    return new Method(
        methodName,
        Type.getMethodDescriptor(
            BytecodeUtils.RENDER_RESULT_TYPE,
            BytecodeUtils.SOY_RECORD_TYPE,
            BytecodeUtils.SOY_RECORD_TYPE,
            BytecodeUtils.LOGGING_ADVISING_APPENDABLE_TYPE,
            BytecodeUtils.RENDER_CONTEXT_TYPE));
  }

  /** Generates a method signature for a positional style call to the given template. */
  private static Method createPositionalRenderMethod(String methodName, TemplateType templateType) {
    return new Method(
        methodName,
        Type.getMethodDescriptor(
            BytecodeUtils.RENDER_RESULT_TYPE,
            Stream.concat(
                    templateType.getActualParameters().stream()
                        .map(i -> BytecodeUtils.SOY_VALUE_PROVIDER_TYPE),
                    Stream.of(
                        BytecodeUtils.SOY_RECORD_TYPE,
                        BytecodeUtils.LOGGING_ADVISING_APPENDABLE_TYPE,
                        BytecodeUtils.RENDER_CONTEXT_TYPE))
                .toArray(Type[]::new)));
  }

  /** The {@link Method} signature of the {@code static CompiledTemplate template()} method. */
  private static final Method createTemplateMethod(String methodName) {
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
            && templateType.getTemplateKind() != TemplateType.TemplateKind.DELTEMPLATE;
    String methodName = Names.renderMethodNameFromSoyTemplateName(templateName);
    return new AutoValue_CompiledTemplateMetadata(
        MethodRef.createStaticMethod(type, createRenderMethod(methodName)).asNonNullable(),
        Optional.ofNullable(
            hasPositionalSignature
                ? MethodRef.createStaticMethod(
                        type, createPositionalRenderMethod(methodName, templateType))
                    .asNonNullable()
                : null),
        MethodRef.createStaticMethod(type, createTemplateMethod(methodName))
            .asCheap()
            .asNonNullable(),
        templateType,
        type);
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

  /** The {@code static CompiledTemplate template()} method. */
  abstract MethodRef templateMethod();

  boolean hasPositionalSignature() {
    return positionalRenderMethod().isPresent();
  }

  abstract TemplateType templateType();

  /** The name of the compiled template class. */
  abstract TypeInfo typeInfo();
}
