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

package com.google.template.soy.sharedpasses.render;

import com.google.common.util.concurrent.Futures;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValueConverterUtility;

/**
 * Unit tests for EvalVisitor.
 *
 */
public class EvalVisitorFutureTest extends EvalVisitorTest {
  @Override
  protected SoyRecord createTestData() {
    SoyList tri = SoyValueConverterUtility.newList(Futures.immediateFuture(1), 3, 6, 10, 15, 21);
    return SoyValueConverterUtility.newDict(
        "boo", Futures.immediateFuture(8),
        "foo.bar", Futures.immediateFuture("baz"),
        "foo.goo2", Futures.immediateFuture(tri),
        "goo", Futures.immediateFuture(tri),
        "moo", Futures.immediateFuture(3.14),
        "t", Futures.immediateFuture(true),
        "f", Futures.immediateFuture(false),
        "n", Futures.immediateFuture(null),
        "map0", Futures.immediateFuture(SoyValueConverterUtility.newDict()),
        "list0", Futures.immediateFuture(SoyValueConverterUtility.newList()),
        "longNumber", Futures.immediateFuture(1000000000000000001L),
        "floatNumber", Futures.immediateFuture(1.5D));
  }
}
