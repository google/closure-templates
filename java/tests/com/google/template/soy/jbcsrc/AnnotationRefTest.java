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

import com.google.common.collect.ImmutableList;

import junit.framework.TestCase;

import org.objectweb.asm.Opcodes;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Tests for {@link AnnotationRef}.
 */
public class AnnotationRefTest extends TestCase {
  @Retention(RetentionPolicy.RUNTIME)
  @interface NoParams{}

  @Retention(RetentionPolicy.RUNTIME)
  @interface AParam{ String value() default ""; }
  
  @Retention(RetentionPolicy.RUNTIME)
  @interface ComplexParams {
    String value() default "";
    int[] ints() default {};
    String[] strings() default {};
    AParam aParam() default @AParam;
  }

  @NoParams
  public void testAnnotation_NoParams() throws Exception {
    runCurrentAnnotationTest();
  }
  
  @AParam("foo")
  public void testAnnotation_aParam() throws Exception {
    runCurrentAnnotationTest();
  }

  @AParam
  public void testAnnotation_aParam_default() throws Exception {
    runCurrentAnnotationTest();
  }
  
  @ComplexParams
  public void testAnnotation_complex_default() {
    runCurrentAnnotationTest();
  }

  @ComplexParams(value = "foo", ints = {1, 2, 3}, strings = {"a", "b", "c"}, aParam = @AParam("foo"))
  public void testAnnotation_complex() {
    runCurrentAnnotationTest();
  }

  private void runCurrentAnnotationTest() {
    Annotation[] annotations;
    try {
      annotations = getClass().getMethod(getName()).getAnnotations();
    } catch (NoSuchMethodException | SecurityException e) {
      throw new RuntimeException(e);
    }
    if (annotations.length != 1) {
      fail("There should only be one annotation on the test method");
    }
    Annotation ann = annotations[0];
    assertEquals(ann, createClassWithAnnotation(ann).getAnnotation(ann.annotationType()));
  }

  @SuppressWarnings("unchecked")
  private static <T extends Annotation> Class<?> createClassWithAnnotation(T ann) {
    TypeInfo generatedType = TypeInfo.create(
        AnnotationRefTest.class.getPackage().getName() + ".Tmp");
    SoyClassWriter cw =
        SoyClassWriter.builder(generatedType)
            .setAccess(Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_PUBLIC)
            .build();
    AnnotationRef.forType((Class<T>) ann.annotationType()).write(ann, cw);
    cw.visitEnd();
    ClassData cd = cw.toClassData();
    try {
      return new MemoryClassLoader(ImmutableList.of(cd)).loadClass(cd.type().className());
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);  // this should be impossible
    }
  }
}

