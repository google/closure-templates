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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.testing.AbstractSoyPrintDirectiveTestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for ChangeNewlineToBrDirective.
 *
 */
@RunWith(JUnit4.class)
public class ChangeNewlineToBrDirectiveTest extends AbstractSoyPrintDirectiveTestCase {
  @Test
  public void testApplyForTofu() {
    ChangeNewlineToBrDirective directive = new ChangeNewlineToBrDirective();
    assertTofuOutput("", "", directive);
    assertTofuOutput("a<br>b", "a\rb", directive);
    assertTofuOutput("a<br>b", "a\nb", directive);
    assertTofuOutput("a<br>b", "a\r\nb", directive);
    assertTofuOutput("abc<br>def<br>xyz", "abc\rdef\nxyz", directive);
  }
  @Test
  public void testApplyForJsSrc() {
    ChangeNewlineToBrDirective directive = new ChangeNewlineToBrDirective();
    JsExpr dataRef = new JsExpr("opt_data.myKey", Integer.MAX_VALUE);
    assertThat(directive.applyForJsSrc(dataRef, ImmutableList.of()).getText())
        .isEqualTo("soy.$$changeNewlineToBr(opt_data.myKey)");
  }
  @Test
  public void testApplyForPySrc() {
    ChangeNewlineToBrDirective directive = new ChangeNewlineToBrDirective();
    PyExpr data = new PyExpr("'data'", Integer.MAX_VALUE);
    assertThat(directive.applyForPySrc(data, ImmutableList.of()).getText())
        .isEqualTo("sanitize.change_newline_to_br('data')");
  }
}
