/*
 * Copyright 2019 Google Inc.
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

package com.google.template.soy.types;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.template.soy.testing.Example;
import com.google.template.soy.testing.ExampleExtendable;
import com.google.template.soy.testing.SomeNestedExtension.NestedExtension;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SoyProtoTypeTest {

  @Test
  public void testExtensionFieldNames() {
    SoyTypeRegistry typeRegistry =
        new SoyTypeRegistry.Builder()
            .addDescriptors(ImmutableList.of(Example.getDescriptor()))
            .build();

    ImmutableSet<FieldDescriptor> extensions =
        ImmutableSet.of(
            NestedExtension.someNestedExtensionField.getDescriptor(),
            Example.someBoolExtension.getDescriptor(),
            Example.someIntExtension.getDescriptor(),
            Example.listExtension.getDescriptor());

    SoyProtoType protoType =
        new SoyProtoType(typeRegistry, ExampleExtendable.getDescriptor(), extensions);

    assertThat(protoType.getFieldNames())
        .containsAtLeast(
            "someNestedExtensionField",
            "example.SomeNestedExtension.NestedExtension.someNestedExtensionField",
            "someBoolExtension",
            "example.someBoolExtension",
            "someIntExtension",
            "example.someIntExtension",
            "listExtensionList",
            "example.listExtensionList");
    assertThat(protoType.getExtensionFieldNames())
        .containsExactly(
            "example.SomeNestedExtension.NestedExtension.someNestedExtensionField",
            "example.someBoolExtension",
            "example.someIntExtension",
            "example.listExtensionList");
  }
}
