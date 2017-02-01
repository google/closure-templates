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

package com.google.template.soy.tofu.internal;

import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.sharedpasses.render.Environment;
import com.google.template.soy.sharedpasses.render.EvalVisitor;
import com.google.template.soy.sharedpasses.render.EvalVisitor.EvalVisitorFactory;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Implementation of EvalVisitorFactory for Tofu backend.
 *
 */
@Singleton
final class TofuEvalVisitorFactory implements EvalVisitorFactory {

  /** Instance of SoyValueConverter to use. */
  private final SoyValueConverter valueConverter;

  @Inject
  public TofuEvalVisitorFactory(SoyValueConverter valueConverter) {
    this.valueConverter = valueConverter;
  }

  @Override
  public EvalVisitor create(@Nullable SoyRecord ijData, Environment env) {
    return new TofuEvalVisitor(valueConverter, ijData, env);
  }
}
