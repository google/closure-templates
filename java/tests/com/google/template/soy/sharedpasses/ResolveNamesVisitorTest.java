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

package com.google.template.soy.sharedpasses;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.shared.internal.SharedTestUtils;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeProvider;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.primitive.UnknownType;

import junit.framework.TestCase;

/**
 * Unit tests for ResolveNamesVisitor.
 *
 * @author Talin
 */
public class ResolveNamesVisitorTest extends TestCase {


  private static final SoyTypeProvider typeProvider =
      new SoyTypeProvider() {
        @Override
        public SoyType getType(String typeName, SoyTypeRegistry typeRegistry) {
          if (typeName.equals("unknown")) {
            return UnknownType.getInstance();
          }
          return null;
        }
      };

  private static final SoyTypeRegistry typeRegistry =
      new SoyTypeRegistry(ImmutableSet.of(typeProvider));


  private static ResolveNamesVisitor createResolveNamesVisitorForMaxSyntaxVersion() {
    return createResolveNamesVisitor(SyntaxVersion.V9_9);
  }


  private static ResolveNamesVisitor createResolveNamesVisitor(
      SyntaxVersion declaredSyntaxVersion) {
    return new ResolveNamesVisitor(declaredSyntaxVersion);
  }


  public void testParamNameLookupSuccess() {
    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(constructTemplateSource(
        "{@param pa: bool}",
        "{$pa}"));
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
  }


  public void testLetNameLookupSuccess() {
    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(constructTemplateSource(
        "{let $pa: 1 /}",
        "{$pa}"));
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
  }


  public void testNameLookupFailure() {
    assertResolveNamesFails(
        "Undefined variable",
        constructTemplateSource("{$pa}"));
  }


  /**
   * Helper function that constructs a boilerplate template given a list of body
   * statements to insert into the middle of the template. The body statements will be
   * indented and separated with newlines.
   * @param body The body statements.
   * @return The combined template.
   */
  private String constructTemplateSource(String... body) {
    return "" +
        "{namespace ns}\n" +
        "/***/\n" +
        "{template .aaa}\n" +
        "  " + Joiner.on("\n   ").join(body) + "\n" +
        "{/template}\n";
  }


  /**
   * Assertions function that checks to make sure that name resolution fails with the
   * expected exception.
   * @param fileContent The template source.
   * @param expectedError The expected failure message (a substring).
   */
  private void assertResolveNamesFails(String expectedError, String fileContent) {
    SoyFileSetNode soyTree =
        SharedTestUtils.parseSoyFiles(typeRegistry, SyntaxVersion.V2_0, false, fileContent);
    try {
      createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
      fail("Expected SoySyntaxException");
    } catch (SoySyntaxException e) {
      assertTrue(e.getMessage().contains(expectedError));
    }
  }
}
