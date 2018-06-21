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

import static com.google.template.soy.jbcsrc.shared.Names.javaClassNameFromSoyTemplateName;
import static com.google.template.soy.jbcsrc.shared.Names.soyTemplateNameFromJavaClassName;
import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link Names}. */
@RunWith(JUnit4.class)
public class NamesTest {

  @Test
  public void testMangleName() {
    assertEquals(
        "com.google.template.soy.jbcsrc.gen.foo.bar.Baz",
        Names.javaClassNameFromSoyTemplateName("foo.bar.Baz"));
    assertEquals(
        "foo.bar.Baz",
        soyTemplateNameFromJavaClassName(javaClassNameFromSoyTemplateName("foo.bar.Baz")));
  }
}
