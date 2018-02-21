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

package com.google.template.soy.jssrc.internal;

import com.google.common.base.Supplier;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.internal.GenJsExprsVisitor.GenJsExprsVisitorFactory;

/**
 * Utilities for unit tests in the Js Src backend.
 *
 */
final class JsSrcTestUtils {

  private JsSrcTestUtils() {}

  static GenJsExprsVisitorFactory createGenJsExprsVisitorFactory(SoyJsSrcOptions options) {
    return createObjects(options).factory;
  }

  static GenCallCodeUtils createGenCallCodeUtils(SoyJsSrcOptions options) {
    return createObjects(options).utils.get();
  }

  private static Objects createObjects(final SoyJsSrcOptions options) {
    final DelTemplateNamer delTemplateNamer = new DelTemplateNamer();
    final IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor =
        new IsComputableAsJsExprsVisitor();
    class GenCallCodeUtilsSupplier implements Supplier<GenCallCodeUtils> {
      GenJsExprsVisitorFactory factory;

      @Override
      public GenCallCodeUtils get() {
        return new GenCallCodeUtils(
            options, delTemplateNamer, isComputableAsJsExprsVisitor, factory);
      }
    }
    GenCallCodeUtilsSupplier supplier = new GenCallCodeUtilsSupplier();
    GenJsExprsVisitorFactory genJsExprsVisitorFactory =
        new GenJsExprsVisitorFactory(options, supplier, isComputableAsJsExprsVisitor);
    supplier.factory = genJsExprsVisitorFactory;

    return new Objects(supplier, genJsExprsVisitorFactory);
  }

  private static final class Objects {
    final Supplier<GenCallCodeUtils> utils;
    final GenJsExprsVisitorFactory factory;

    Objects(Supplier<GenCallCodeUtils> utils, GenJsExprsVisitorFactory genJsExprsVisitorFactory) {
      this.utils = utils;
      this.factory = genJsExprsVisitorFactory;
    }
  }
}
