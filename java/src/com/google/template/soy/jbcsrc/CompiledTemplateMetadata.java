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

import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.COMPILED_TEMPLATE_FACTORY_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.SOY_RECORD_TYPE;

import com.google.auto.value.AutoValue;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.jbcsrc.restricted.ConstructorRef;
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
   * The {@link Method} signature of all generated constructors for the {@link CompiledTemplate}
   * classes.
   */
  private static final Method GENERATED_CONSTRUCTOR =
      new Method(
          "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, SOY_RECORD_TYPE, SOY_RECORD_TYPE));

  /**
   * The {@link Method} signature of the {@link CompiledTemplate#render(AdvisingAppendable,
   * RenderContext)} method.
   */
  private static final Method RENDER_METHOD;


  /**
   * The {@link Method} signature of the {@code static CompiledTemplate.Factory factory()} method.
   */
  private static final Method FACTORY_METHOD =
      new Method("factory", Type.getMethodDescriptor(COMPILED_TEMPLATE_FACTORY_TYPE));

  static {
    try {
      RENDER_METHOD =
          Method.getMethod(
              CompiledTemplate.class.getMethod(
                  "render", LoggingAdvisingAppendable.class, RenderContext.class));
    } catch (NoSuchMethodException | SecurityException e) {
      throw new RuntimeException(e);
    }
  }

  static CompiledTemplateMetadata create(String templateName, SoyFileKind kind) {
    String className = Names.javaClassNameFromSoyTemplateName(templateName);
    TypeInfo type = TypeInfo.createClass(className);
    return new AutoValue_CompiledTemplateMetadata(
        ConstructorRef.create(type, GENERATED_CONSTRUCTOR),
        MethodRef.createInstanceMethod(type, RENDER_METHOD).asNonNullable(),
        MethodRef.createStaticMethod(type, FACTORY_METHOD).asCheap(),
        type,
        kind);
  }

  /**
   * The template constructor.
   *
   * <p>The constructor has the same interface as {@link
   * com.google.template.soy.jbcsrc.shared.CompiledTemplate.Factory#create}
   */
  abstract ConstructorRef constructor();

  /** The {@link CompiledTemplate#render(AdvisingAppendable, RenderContext)} method. */
  abstract MethodRef renderMethod();

  /** The {@code static CompiledTemplate.Factory factory()} method. */
  abstract MethodRef factoryMethod();

  /** The name of the compiled template. */
  abstract TypeInfo typeInfo();

  /** The template file kind. */
  abstract SoyFileKind filekind();
}
