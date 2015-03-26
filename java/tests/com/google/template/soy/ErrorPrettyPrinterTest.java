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

package com.google.template.soy;

import static com.google.template.soy.soytree.TemplateSubject.assertThatTemplateContent;

import com.google.common.base.Joiner;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.CommandTextAttributesParser;
import com.google.template.soy.soytree.LetNode;
import com.google.template.soy.soytree.LetValueNode;

import junit.framework.TestCase;

import java.io.IOException;

/**
 * Tests for {@link com.google.template.soy.base.internal.ErrorPrettyPrinter}.
 *
 * @author brndn@google.com (Brendan Linn)
 */
public final class ErrorPrettyPrinterTest extends TestCase {

  public void testMultipleErrorReports() throws IOException {
    String input = Joiner.on('\n').join(
            "{call /}",
            " {delcall 123 /}",
            " {let /}",
            "   {delcall foo.bar variant=1 foo=\"bar\" /}");

    assertThatTemplateContent(input)
        .causesError(CallBasicNode.MISSING_CALLEE_NAME)
        .at(1, 1);
    assertThatTemplateContent(input)
        .causesError(CallDelegateNode.INVALID_DELEGATE_NAME)
        .at(2, 2);
    assertThatTemplateContent(input)
        .causesError(LetNode.INVALID_COMMAND_TEXT)
        .at(3, 2);
    assertThatTemplateContent(input)
        .causesError(LetValueNode.SELF_ENDING_WITHOUT_VALUE)
        .at(3, 2);
    assertThatTemplateContent(input)
        .causesError(CommandTextAttributesParser.MALFORMED_ATTRIBUTES)
        .at(4, 4);
    assertThatTemplateContent(input)
        .causesError(CommandTextAttributesParser.UNSUPPORTED_ATTRIBUTE)
        .at(4, 4);
  }
}
