/*
 * Copyright 2018 Google Inc.
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

package com.google.template.soy.soytree;

import static com.google.common.truth.Truth.assertThat;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyError;
import com.google.template.soy.soytree.defn.HeaderParam;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.soytree.defn.TemplateStateVar;
import com.google.template.soy.types.AnyType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.ast.NamedTypeNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link TemplateNodeBuilder}.
 *
 */
@RunWith(JUnit4.class)
public class TemplateNodeBuilderTest {
  private static final SourceLocation sourceLocation = SourceLocation.UNKNOWN;
  private static final SoyType soyType = AnyType.getInstance();
  private static final NamedTypeNode paramTypeNode =
      NamedTypeNode.create(sourceLocation, "paramType");
  private static final NamedTypeNode stateVarTypeNode =
      NamedTypeNode.create(sourceLocation, "stateVarType");

  @Test
  public void checkDuplicateHeadersNoDuplicatesHasNoErrors() {
    ErrorReporter testErrorReporter = ErrorReporter.createForTest();
    ImmutableList<TemplateParam> params =
        ImmutableList.of(createParamWithName("p1"), createParamWithName("p2"));
    ImmutableList<TemplateStateVar> stateVars =
        ImmutableList.of(createStateVarWithName("s1"), createStateVarWithName("s2"));
    TemplateNodeBuilder.checkDuplicateHeaderVars(params, stateVars, testErrorReporter);
    assertFalse(testErrorReporter.hasErrors());
  }

  @Test
  public void checkDuplicateHeadersReportsErrorsDupParamState() {
    ErrorReporter testErrorReporter = ErrorReporter.createForTest();
    ImmutableList<TemplateParam> params = ImmutableList.of(createParamWithName("s"));
    ImmutableList<TemplateStateVar> stateVars = ImmutableList.of(createStateVarWithName("s"));
    TemplateNodeBuilder.checkDuplicateHeaderVars(params, stateVars, testErrorReporter);
    assertThat(testErrorReporter.getErrors()).hasSize(1);
    SoyError dupError = testErrorReporter.getErrors().get(0);
    assertEquals("Param 's' is a duplicate of state var 's'.", dupError.message());
  }

  private static HeaderParam createParamWithName(String name) {
    return new HeaderParam(name, sourceLocation, soyType, paramTypeNode, true, false, null);
  }

  private static TemplateStateVar createStateVarWithName(String name) {
    return new TemplateStateVar(name, soyType, stateVarTypeNode, null, sourceLocation);
  }
}
