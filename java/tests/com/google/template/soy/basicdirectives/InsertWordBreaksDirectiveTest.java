/*
 * Copyright 2009 Google Inc.
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

package com.google.template.soy.basicdirectives;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.shared.AbstractSoyPrintDirectiveTestCase;


/**
 * Unit tests for InsertWordBreaksDirective.
 *
 * @author Kai Huang
 */
public class InsertWordBreaksDirectiveTest extends AbstractSoyPrintDirectiveTestCase {


  public void testApplyForTofu() {
    int maxCharsBetweenBreaks = 8;

    InsertWordBreaksDirective insertWordBreaksDirective = new InsertWordBreaksDirective();
    assertTofuOutput("", "", insertWordBreaksDirective, maxCharsBetweenBreaks);
    assertTofuOutput(
        "blah blahblah<wbr>blah", "blah blahblahblah", insertWordBreaksDirective,
        maxCharsBetweenBreaks);
    assertTofuOutput(
        "blah<br>&lt;bla<wbr>hblah", "blah<br>&lt;blahblah", insertWordBreaksDirective,
        maxCharsBetweenBreaks);
  }


  public void testApplyForJsSrc() {

    InsertWordBreaksDirective insertWordBreaksDirective = new InsertWordBreaksDirective();
    JsExpr dataRef = new JsExpr("opt_data.myKey", Integer.MAX_VALUE);
    JsExpr arg = new JsExpr("8", Integer.MAX_VALUE);
    assertEquals(
        "soy.$$insertWordBreaks(opt_data.myKey, 8)",
        insertWordBreaksDirective.applyForJsSrc(dataRef, ImmutableList.of(arg)).getText());
  }

}
