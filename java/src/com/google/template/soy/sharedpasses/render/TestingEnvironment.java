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

package com.google.template.soy.sharedpasses.render;

import com.google.common.annotations.VisibleForTesting;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.soytree.ConstNode;
import com.google.template.soy.soytree.defn.ConstVar;
import java.util.Map;

/** An {@link Environment} for testing expressions. */
public final class TestingEnvironment extends Environment {

  /**
   * Creates an environment that should only be used in testing environments. Allows variables to be
   * resolved against a predefined set but doesn't allow binding new variable definitions.
   */
  @VisibleForTesting
  public static Environment createForTest(
      final SoyRecord params, final Map<String, SoyValueProvider> locals) {
    return new TestingEnvironment(params, locals);
  }

  private final SoyRecord params;
  private final Map<String, SoyValueProvider> locals;

  private TestingEnvironment(SoyRecord params, Map<String, SoyValueProvider> locals) {
    this.params = params;
    this.locals = locals;
  }

  @Override
  void bind(VarDefn var, SoyValueProvider value) {
    throw new UnsupportedOperationException();
  }

  @Override
  void bindLoopPosition(VarDefn loopVar, SoyValueProvider value, int index, boolean isLast) {
    throw new UnsupportedOperationException();
  }

  @Override
  boolean hasVar(VarDefn var) {
    return params.hasField(var.name());
  }

  @Override
  SoyValue getVar(VarDefn var) {
    return getVarProvider(var).resolve();
  }

  @Override
  boolean isLast(VarDefn loopVar) {
    return doGetProvider(loopVar.name() + "__isLast").resolve().booleanValue();
  }

  @Override
  int getIndex(VarDefn loopVar) {
    return doGetProvider(loopVar.name() + "__index").resolve().integerValue();
  }

  @Override
  SoyValueProvider getVarProvider(VarDefn var) {
    return doGetProvider(var.name());
  }

  @Override
  ConstNode lookupConst(ConstVar defnDecl) {
    return null;
  }

  private SoyValueProvider doGetProvider(String name) {
    SoyValueProvider provider = locals.get(name);
    if (provider == null) {
      provider = params.getFieldProvider(name);
      if (provider == null) {
        provider = UndefinedData.INSTANCE;
      }
    }
    return provider;
  }
}
