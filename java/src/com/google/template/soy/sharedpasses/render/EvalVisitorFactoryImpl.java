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

package com.google.template.soy.sharedpasses.render;

import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValueHelper;
import com.google.template.soy.shared.internal.SharedModule.Shared;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.sharedpasses.render.EvalVisitor.EvalVisitorFactory;

import java.util.Map;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Default implementation of EvalVisitorFactory.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
@Singleton
public class EvalVisitorFactoryImpl implements EvalVisitorFactory {


  /** Instance of SoyValueHelper to use. */
  private final SoyValueHelper valueHelper;

  /** Map of all SoyJavaFunctions (name to function). */
  private final Map<String, SoyJavaFunction> soyJavaFunctionsMap;


  @Inject
  public EvalVisitorFactoryImpl(
      SoyValueHelper valueHelper, @Shared Map<String, SoyJavaFunction> soyJavaFunctionsMap) {
    this.valueHelper = valueHelper;
    this.soyJavaFunctionsMap = soyJavaFunctionsMap;
  }


  @Override public EvalVisitor create(@Nullable SoyRecord ijData, Environment env) {

    return new EvalVisitor(valueHelper, soyJavaFunctionsMap, ijData, env);
  }

}
