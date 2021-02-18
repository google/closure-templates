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
  static final Method RENDER_METHOD =
      new Method(
          "render",
          Type.getMethodDescriptor(
              BytecodeUtils.RENDER_RESULT_TYPE,
              BytecodeUtils.SOY_RECORD_TYPE,
              BytecodeUtils.SOY_RECORD_TYPE,
              BytecodeUtils.LOGGING_ADVISING_APPENDABLE_TYPE,
              BytecodeUtils.RENDER_CONTEXT_TYPE));

  /** The {@link Method} signature of the {@code static CompiledTemplate template()} method. */
  private static final Method TEMPLATE_METHOD =
      new Method("template", Type.getMethodDescriptor(BytecodeUtils.COMPILED_TEMPLATE_TYPE));

  static CompiledTemplateMetadata create(String templateName) {
    String className = Names.javaClassNameFromSoyTemplateName(templateName);
    TypeInfo type = TypeInfo.createClass(className);
    return new AutoValue_CompiledTemplateMetadata(
        MethodRef.createStaticMethod(type, RENDER_METHOD).asNonNullable(),
        MethodRef.createStaticMethod(type, TEMPLATE_METHOD).asCheap().asNonNullable(),
        type);
  }

  /**
   * The {@link static RenderResult render(SoyRecord,SoyRecord, LoggingAdvisingAppendable,
   * RenderContext)} method.
   */
  abstract MethodRef renderMethod();

  /** The {@code static CompiledTemplate template()} method. */
  abstract MethodRef templateMethod();

  /** The name of the compiled template class. */
  abstract TypeInfo typeInfo();
}
