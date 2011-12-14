/*
 * Copyright 2010 Google Inc.
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

import com.google.template.soy.base.SoySyntaxException;

import junit.framework.TestCase;


/**
 * Unit tests for CallNode.
 *
 */
public class CallNodeTest extends TestCase {


  public void testCommandText() throws SoySyntaxException {

    checkCommandText("function=\"bar.foo\"");
    checkCommandText("foo");
    checkCommandText(".foo data=\"all\"");
    checkCommandText("name=\".baz\" data=\"$x\"", ".baz data=\"$x\"");

    try {
      checkCommandText(".foo.bar data=\"$x\"");
      fail();
    } catch (SoySyntaxException e) {
      // Test passes.
    }
  }


  private void checkCommandText(String commandText) {
    checkCommandText(commandText, commandText);
  }


  private void checkCommandText(String commandText, String expectedCommandText) {

    CallBasicNode callNode = new CallBasicNode(0, commandText, null);
    if (callNode.getCalleeName() == null) {
      callNode.setCalleeName("testNamespace" + callNode.getPartialCalleeName());
    }

    CallBasicNode normCallNode = new CallBasicNode(
        0, callNode.getCalleeName(), callNode.getPartialCalleeName(), false,
        callNode.isPassingData(), callNode.isPassingAllData(), callNode.getExprText(),
        callNode.getUserSuppliedPlaceholderName(), callNode.getSyntaxVersion());

    assertEquals(expectedCommandText, normCallNode.getCommandText());

    assertEquals(callNode.getSyntaxVersion(), normCallNode.getSyntaxVersion());
    assertEquals(callNode.getCalleeName(), normCallNode.getCalleeName());
    assertEquals(callNode.isPassingData(), normCallNode.isPassingData());
    assertEquals(callNode.isPassingAllData(), normCallNode.isPassingAllData());
    assertEquals(callNode.getExprText(), normCallNode.getExprText());
  }

}
