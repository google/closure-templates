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


package com.google.template.soy.parseinfo.passes;

import static com.google.template.soy.parseinfo.passes.GenerateParseInfoVisitor.JavaClassNameSource.GENERIC;
import static com.google.template.soy.parseinfo.passes.GenerateParseInfoVisitor.JavaClassNameSource.SOY_FILE_NAME;
import static com.google.template.soy.parseinfo.passes.GenerateParseInfoVisitor.JavaClassNameSource.SOY_NAMESPACE_LAST_PART;

import com.google.template.soy.base.internal.IndentedLinesBuilder;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.soytree.SoyFileNode;

import junit.framework.TestCase;

/**
 * Unit tests for GenerateParseInfoVisitor.
 *
 * <p> Note: Testing of the actual code generation happens in {@code SoyFileSetTest}.
 *
 */
public class GenerateParseInfoVisitorTest extends TestCase {


  public void testJavaClassNameSource() {

    SoyFileNode soyFileNode = new SoyFileNode(0, SoyFileKind.SRC, null, "aaa.bbb.cccDdd", null);
    try {
      SOY_FILE_NAME.generateBaseClassName(soyFileNode);
      fail();
    } catch (IllegalArgumentException iae) {
      // Test passes.
    }

    soyFileNode.setFilePath("BooFoo.soy");
    assertEquals("BooFoo", SOY_FILE_NAME.generateBaseClassName(soyFileNode));
    soyFileNode.setFilePath("blah/bleh/boo_foo.soy");
    assertEquals("BooFoo", SOY_FILE_NAME.generateBaseClassName(soyFileNode));
    soyFileNode.setFilePath("boo-FOO.soy");
    assertEquals("BooFoo", SOY_FILE_NAME.generateBaseClassName(soyFileNode));
    soyFileNode.setFilePath("\\BLAH\\BOO_FOO.SOY");
    assertEquals("BooFoo", SOY_FILE_NAME.generateBaseClassName(soyFileNode));

    soyFileNode = new SoyFileNode(0, SoyFileKind.SRC, null, "cccDdd", null);
    assertEquals("CccDdd", SOY_NAMESPACE_LAST_PART.generateBaseClassName(soyFileNode));
    soyFileNode = new SoyFileNode(0, SoyFileKind.SRC, null, "aaa.bbb.cccDdd", null);
    assertEquals("CccDdd", SOY_NAMESPACE_LAST_PART.generateBaseClassName(soyFileNode));
    soyFileNode = new SoyFileNode(0, SoyFileKind.SRC, null, "aaa_bbb.ccc_ddd", null);
    assertEquals("CccDdd", SOY_NAMESPACE_LAST_PART.generateBaseClassName(soyFileNode));
    soyFileNode = new SoyFileNode(0, SoyFileKind.SRC, null, "CccDdd", null);
    assertEquals("CccDdd", SOY_NAMESPACE_LAST_PART.generateBaseClassName(soyFileNode));
    soyFileNode = new SoyFileNode(0, SoyFileKind.SRC, null, "aaa.bbb.ccc_DDD", null);
    assertEquals("CccDdd", SOY_NAMESPACE_LAST_PART.generateBaseClassName(soyFileNode));

    soyFileNode = new SoyFileNode(0, SoyFileKind.SRC, null, "aaa.bbb.cccDdd", null);
    soyFileNode.setFilePath("BooFoo.soy");
    assertEquals("File", GENERIC.generateBaseClassName(soyFileNode));
    soyFileNode = new SoyFileNode(0, SoyFileKind.SRC, null, "ccc_ddd", null);
    soyFileNode.setFilePath("blah/bleh/boo-foo.soy");
    assertEquals("File", GENERIC.generateBaseClassName(soyFileNode));
  }


  public void testAppendJavadoc() {

    String doc = "" +
        "Blah blah blah blah blah blah blah blah blah blah blah blah blah blah blah blah blah" +
        " blah blah blah blah blah blah blahblahblahblahblahblahblahblahblahblahblahblahblah" +
        "blahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblah.";
    String expectedJavadoc = "" +
        "    /**\n" +
        "     * Blah blah blah blah blah blah blah blah blah blah blah blah blah blah blah blah" +
        " blah blah\n" +
        "     * blah blah blah blah blah\n" +
        "     * blahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblah" +
        "blahblahb\n" +
        "     * lahblahblahblahblahblahblahblahblahblahblahblah.\n" +
        "     */\n";
    IndentedLinesBuilder ilb = new IndentedLinesBuilder(2, 4);
    GenerateParseInfoVisitor.appendJavadoc(ilb, doc, false, true);
    assertEquals(expectedJavadoc, ilb.toString());
  }

}
