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

package com.google.template.soy.pysrc.restricted;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;


/**
 * Unit tests for CodeBuilder.
 *
 */
public final class PyExprUtilsTest extends TestCase {

  public void testConcatPyExprs() {
    List<PyExpr> exprs = new ArrayList<PyExpr>();

    // Empty Array.
    assertEquals("''", PyExprUtils.concatPyExprs(exprs).getText());

    // Single Value.
    PyExpr foo = new PyExpr("foo", Integer.MAX_VALUE);
    exprs.add(foo);
    assertEquals("str(foo)", PyExprUtils.concatPyExprs(exprs).getText());

    // Multiple values use the runtime library.
    PyExpr bar = new PyExpr("bar", Integer.MAX_VALUE);
    PyExpr baz = new PyExpr("baz", Integer.MAX_VALUE);
    exprs.add(bar);
    exprs.add(baz);
    assertEquals("''.join([str(foo),str(bar),str(baz)])",
        PyExprUtils.concatPyExprs(exprs).getText());
  }
}
