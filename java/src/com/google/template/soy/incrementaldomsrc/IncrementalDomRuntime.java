/*
 * Copyright 2017 Google Inc.
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
package com.google.template.soy.incrementaldomsrc;

import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.dsl.GoogRequire;

/**
 * Common runtime symbols for incrementaldom.
 *
 * <p>Unlike jssrc, incrementaldom declares {@code goog.module}s and therefore uses aliased {@code
 * goog.require} statements.
 */
final class IncrementalDomRuntime {
  private static final GoogRequire INCREMENTAL_DOM =
      GoogRequire.createWithAlias("incrementaldom", "incrementalDom");
  static final GoogRequire SOY_IDOM = GoogRequire.createWithAlias("soy.idom", "soyIdom");

  public static final CodeChunk.WithValue INCREMENTAL_DOM_ELEMENT_OPEN =
      INCREMENTAL_DOM.dotAccess("elementOpen");

  public static final CodeChunk.WithValue INCREMENTAL_DOM_ELEMENT_CLOSE =
      INCREMENTAL_DOM.dotAccess("elementClose");

  public static final CodeChunk.WithValue INCREMENTAL_DOM_ELEMENT_VOID =
      INCREMENTAL_DOM.dotAccess("elementVoid");

  public static final CodeChunk.WithValue INCREMENTAL_DOM_ELEMENT_OPEN_START =
      INCREMENTAL_DOM.dotAccess("elementOpenStart");

  public static final CodeChunk.WithValue INCREMENTAL_DOM_ELEMENT_OPEN_END =
      INCREMENTAL_DOM.dotAccess("elementOpenEnd");

  public static final CodeChunk.WithValue INCREMENTAL_DOM_TEXT = INCREMENTAL_DOM.dotAccess("text");

  public static final CodeChunk.WithValue INCREMENTAL_DOM_ATTR = INCREMENTAL_DOM.dotAccess("attr");

  public static final CodeChunk.WithValue SOY_IDOM_RENDER_DYNAMIC_CONTENT =
      SOY_IDOM.dotAccess("renderDynamicContent");

  public static final CodeChunk.WithValue SOY_IDOM_PRINT = SOY_IDOM.dotAccess("print");

  private IncrementalDomRuntime() {}
}
