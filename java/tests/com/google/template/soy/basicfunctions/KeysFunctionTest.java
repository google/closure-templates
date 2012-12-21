/*
 * Copyright 2011 Google Inc.
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

package com.google.template.soy.basicfunctions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.SoyListData;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.javasrc.restricted.JavaExpr;
import com.google.template.soy.jssrc.restricted.JsExpr;

import junit.framework.TestCase;

import java.util.Set;


/**
 * Unit tests for KeysFunction.
 *
 * @author Kai Huang
 */
public class KeysFunctionTest extends TestCase {


  public void testComputeForTofu() {

    KeysFunction keysFunction = new KeysFunction();
    SoyData map = new SoyMapData("boo", "bar", "foo", 2, "goo", new SoyMapData("moo", 4));
    SoyData result = keysFunction.computeForTofu(ImmutableList.of(map));

    assertTrue(result instanceof SoyListData);
    SoyListData resultAsList = (SoyListData) result;
    assertEquals(3, resultAsList.length());

    Set<String> resultItems = Sets.newHashSet();
    for (SoyData item : resultAsList) {
      resultItems.add(item.stringValue());
    }
    assertEquals(Sets.newHashSet("boo", "foo", "goo"), resultItems);
  }


  public void testComputeForJsSrc() {

    KeysFunction keysFunction = new KeysFunction();
    JsExpr expr = new JsExpr("JS_CODE", Integer.MAX_VALUE);
    assertEquals(
        new JsExpr("soy.$$getMapKeys(JS_CODE)", Integer.MAX_VALUE),
        keysFunction.computeForJsSrc(ImmutableList.of(expr)));
  }


  public void testComputeForJavaSrc() {

    KeysFunction keysFunction = new KeysFunction();

    JavaExpr expr = new JavaExpr("JAVA_CODE", SoyMapData.class, Integer.MAX_VALUE);
    assertEquals(
        new JavaExpr(
            "new com.google.template.soy.data.SoyListData((JAVA_CODE).getKeys())",
            IntegerData.class, Integer.MAX_VALUE),
        keysFunction.computeForJavaSrc(ImmutableList.of(expr)));

    expr = new JavaExpr("JAVA_CODE", SoyData.class, Integer.MAX_VALUE);
    assertEquals(
        new JavaExpr(
            "new com.google.template.soy.data.SoyListData(" + 
                "((com.google.template.soy.data.SoyMapData) JAVA_CODE).getKeys())",
            IntegerData.class, Integer.MAX_VALUE),
        keysFunction.computeForJavaSrc(ImmutableList.of(expr)));
  }

}
