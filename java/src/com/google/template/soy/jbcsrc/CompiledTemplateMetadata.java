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
import com.google.template.soy.jbcsrc.api.AdvisingAppendable;
import com.google.template.soy.jbcsrc.api.CompiledTemplate;
import com.google.template.soy.jbcsrc.api.RenderContext;
import com.google.template.soy.jbcsrc.runtime.Names;
import com.google.template.soy.soytree.TemplateBasicNode;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;


/**
 * Information about a compiled template.
 *
 * <p>This should contain basic information about a single template that will be useful for
 * generating that template as well as calls to the template.
 */
@AutoValue abstract class CompiledTemplateMetadata {
  /**
   * The {@link Method} signature of all generated constructors for the {@link CompiledTemplate}
   * classes.
   */
  static final Method GENERATED_CONSTRUCTOR = new Method("<init>",
      Type.getMethodDescriptor(Type.VOID_TYPE, 
          Type.getType(SoyRecord.class), Type.getType(SoyRecord.class)));

  /** 
   * The {@link Method} signature of the 
   * {@link CompiledTemplate#render(AdvisingAppendable, RenderContext)}  
   * method. 
   */
  static final Method RENDER_METHOD;

  static {
    try {
      RENDER_METHOD =
          Method.getMethod(
              CompiledTemplate.class.getMethod(
                  "render", AdvisingAppendable.class, RenderContext.class));
    } catch (NoSuchMethodException | SecurityException e) {
      throw new RuntimeException(e);
    }
  }

  static CompiledTemplateMetadata create(String templateName, TemplateBasicNode node) {
    String className = Names.javaClassNameFromSoyTemplateName(templateName);
    String factoryClassName = className + "$Factory";
    return new AutoValue_CompiledTemplateMetadata(
        TypeInfo.create(className),
        node,
        TypeInfo.create(factoryClassName));
  }

  /** The name of the compiled template. */
  abstract TypeInfo typeInfo();

  /** The actual template. */
  abstract TemplateBasicNode node();

  /** 
   * The name of this templates {@link com.google.template.soy.jbcsrc.api.CompiledTemplate.Factory}.
   */
  abstract TypeInfo factory();
}
