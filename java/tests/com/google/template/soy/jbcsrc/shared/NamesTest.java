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

package com.google.template.soy.jbcsrc.shared;

import static com.google.common.truth.Truth.assertThat;
import static com.google.template.soy.jbcsrc.shared.Names.javaClassNameFromSoyNamespace;
import static com.google.template.soy.jbcsrc.shared.Names.javaClassNameFromSoyTemplateName;
import static com.google.template.soy.jbcsrc.shared.Names.javaFileName;
import static com.google.template.soy.jbcsrc.shared.Names.renderMethodNameFromSoyTemplateName;
import static org.junit.Assert.assertThrows;

import com.google.template.soy.internal.exemptions.NamespaceExemptions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link Names}. */
@RunWith(JUnit4.class)
public class NamesTest {

  // Sanity check, we will use these template namespaces throughout this file
  @Test
  public void testKnownDuplicateIsExempted() {
    assertThat(NamespaceExemptions.isKnownDuplicateNamespace("testing.duplicate.namespaces"))
        .isTrue();
    assertThat(NamespaceExemptions.isKnownDuplicateNamespace("testing.namespace")).isFalse();
  }

  @Test
  public void testJavaClassNameFromSoyTemplateName() {
    assertThat(javaClassNameFromSoyTemplateName("testing.namespace.foo"))
        .isEqualTo("com.google.template.soy.jbcsrc.gen.testing.namespace");

    // for namespaces in the list of duplicates, the fullyqualified template name becomes the class
    // name.
    assertThat(javaClassNameFromSoyTemplateName("testing.duplicate.namespaces.foo"))
        .isEqualTo("com.google.template.soy.jbcsrc.gen.testing.duplicate.namespaces.foo");
  }

  @Test
  public void testJavaClassNameFromSoyNamespace() {
    assertThat(javaClassNameFromSoyNamespace("testing.namespace"))
        .isEqualTo("com.google.template.soy.jbcsrc.gen.testing.namespace");

    // for namespaces in the list of duplicates, this method is illegal.
    assertThrows(
        IllegalArgumentException.class,
        () -> javaClassNameFromSoyNamespace("testing.duplicate.namespaces"));
  }

  @Test
  public void testRenderMethodNameFromSoyTemplateName() {
    assertThat(renderMethodNameFromSoyTemplateName("testing.namespace.foo")).isEqualTo("foo");

    // for namespaces in the list of duplicates, the fullyqualified template name becomes the class
    // name.
    assertThat(renderMethodNameFromSoyTemplateName("testing.duplicate.namespaces.foo"))
        .isEqualTo("render");
  }

  @Test
  public void testJavaFileName() {
    assertThat(javaFileName("testing.namespace.foo", "foo.soy"))
        .isEqualTo("com/google/template/soy/jbcsrc/gen/testing/namespace/foo.soy");

    // for namespaces in the list of duplicates, the java file is a peer of the class generated for
    // the template.
    assertThat(javaFileName("testing.duplicate.namespaces.foo", "foo.soy"))
        .isEqualTo("com/google/template/soy/jbcsrc/gen/testing/duplicate/namespaces/foo.soy");
  }
}
