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

package com.google.template.soy.jbcsrc.restricted;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.jbcsrc.internal.ClassData;
import com.google.template.soy.jbcsrc.internal.MemoryClassLoader;
import com.google.template.soy.jbcsrc.internal.SoyClassWriter;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.objectweb.asm.Opcodes;

/** Tests for {@link com.google.template.soy.jbcsrc.restricted.AnnotationRef}. */
@RunWith(JUnit4.class)
public class AnnotationRefTest {
  @Rule public final TestName testName = new TestName();

  @Retention(RetentionPolicy.RUNTIME)
  @interface NoParams {}

  @Retention(RetentionPolicy.RUNTIME)
  @interface AParam {
    String value() default "";
  }

  @Retention(RetentionPolicy.RUNTIME)
  @interface ComplexParams {
    String value() default "";

    int[] ints() default {};

    String[] strings() default {};

    AParam aParam() default @AParam;

    AParam[] params() default {};
  }

  @NoParams
  @Test
  public void testAnnotation_NoParams() throws Exception {
    runCurrentAnnotationTest();
  }

  @AParam("foo")
  @Test
  public void testAnnotation_aParam() throws Exception {
    runCurrentAnnotationTest();
  }

  @AParam
  @Test
  public void testAnnotation_aParam_default() throws Exception {
    runCurrentAnnotationTest();
  }

  @ComplexParams
  @Test
  public void testAnnotation_complex_default() {
    runCurrentAnnotationTest();
  }

  @ComplexParams(
      value = "foo",
      ints = {1, 2, 3},
      strings = {"a", "b", "c"},
      aParam = @AParam("foo"),
      params = {@AParam("foo1"), @AParam("foo2")})
  @Test
  public void testAnnotation_complex() {
    runCurrentAnnotationTest();
  }

  private void runCurrentAnnotationTest() {
    Annotation[] annotations;
    try {
      annotations = getClass().getMethod(testName.getMethodName()).getAnnotations();
    } catch (NoSuchMethodException | SecurityException e) {
      throw new RuntimeException(e);
    }
    if (annotations.length != 2) {
      fail("There should only be one non @Test annotation on the test method");
    }
    Annotation ann =
        annotations[0].annotationType() == Test.class ? annotations[1] : annotations[0];
    assertThat(createClassWithAnnotation(ann).getAnnotation(ann.annotationType())).isEqualTo(ann);
  }

  @SuppressWarnings("unchecked")
  private static <T extends Annotation> Class<?> createClassWithAnnotation(T ann) {
    TypeInfo generatedType =
        TypeInfo.create(AnnotationRefTest.class.getPackage().getName() + ".Tmp");
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
      throw new RuntimeException(e); // this should be impossible
    }
  }
}
