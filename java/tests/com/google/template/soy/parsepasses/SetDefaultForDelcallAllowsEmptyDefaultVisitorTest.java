/*
 * Copyright 2013 Google Inc.
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

package com.google.template.soy.parsepasses;

import static com.google.common.truth.Truth.assertThat;

import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.SoyFileSetNode;

import junit.framework.TestCase;

/**
 * Unit tests for SetDefaultForDelcallAllowsEmptyDefaultVisitor.
 *
 */
public class SetDefaultForDelcallAllowsEmptyDefaultVisitorTest extends TestCase {


  public void testDefaultValueForAllowsEmptyDefault() throws Exception {
    String testTemplateContent = "" +
        "  {delcall my.delegate.template /}\n";

    // Test with declared syntax version 2.1.
    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forTemplateContents(testTemplateContent)
        .declaredSyntaxVersion(SyntaxVersion.V2_1)
        .parse();
    CallDelegateNode delcall = (CallDelegateNode) soyTree.getChild(0).getChild(0).getChild(0);
    assertThat(delcall.allowsEmptyDefault()).isTrue();

    // Test with declared syntax version 2.2.
    soyTree = SoyFileSetParserBuilder.forTemplateContents(testTemplateContent)
        .declaredSyntaxVersion(SyntaxVersion.V2_2)
        .parse();
    delcall = (CallDelegateNode) soyTree.getChild(0).getChild(0).getChild(0);
    assertThat(delcall.allowsEmptyDefault()).isFalse();
  }

}
