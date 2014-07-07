/*
 * Copyright 2014 Google Inc.
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

package com.google.template.soy.shared.internal;

import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;

import junit.framework.TestCase;

import java.util.Set;


/**
 * Unit tests for FindCalleesNotInFileVisitor.
 *
 */
public class FindCalleesNotInFileVisitorTest extends TestCase {


  public void testFindCalleesNotInFile() {

    String testFileContent = "" +
        "{namespace boo.foo autoescape=\"deprecated-noncontextual\"}\n" +
        "\n" +
        "/** Test template 1. */\n" +
        "{template .goo}\n" +
        "  {call .goo data=\"all\" /}\n" +
        "  {call .moo data=\"all\" /}\n" +
        "  {call boo.woo.hoo data=\"all\" /}\n" +  // not defined in this file
        "{/template}\n" +
        "\n" +
        "/** Test template 2. */\n" +
        "{template .moo}\n" +
        "  {for $i in range(8)}\n" +
        "    {call boo.foo.goo data=\"all\" /}\n" +
        "    {call .too data=\"all\" /}\n" +  // not defined in this file
        "    {call .goo}" +
        "      {param a}{call .moo /}{/param}" +
        "      {param b}{call .zoo /}{/param}" +  // not defined in this file
        "    {/call}" +
        "  {/for}\n" +
        "{/template}\n" +
        "\n" +
        "/** Test template 3. */\n" +
        "{deltemplate booHoo}\n" +
        "  {call .goo data=\"all\" /}\n" +
        "  {call .moo data=\"all\" /}\n" +
        "  {call boo.hoo.roo data=\"all\" /}\n" +  // not defined in this file
        "{/deltemplate}\n";


    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(testFileContent);
    SoyFileNode soyFile = soyTree.getChild(0);

    Set<String> calleesNotInFile = (new FindCalleesNotInFileVisitor()).exec(soyFile);
    assertFalse(calleesNotInFile.contains("boo.foo.goo"));
    assertFalse(calleesNotInFile.contains("boo.foo.moo"));
    assertTrue(calleesNotInFile.contains("boo.woo.hoo"));
    assertTrue(calleesNotInFile.contains("boo.foo.too"));
    assertTrue(calleesNotInFile.contains("boo.foo.zoo"));
    assertTrue(calleesNotInFile.contains("boo.hoo.roo"));
  }

}
