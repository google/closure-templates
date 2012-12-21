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

import com.google.inject.Inject;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.shared.restricted.SoyJavaRuntimeFunction;
import com.google.template.soy.sharedpasses.render.EvalVisitor.EvalVisitorFactory;

import java.util.Deque;
import java.util.Map;

import javax.annotation.Nullable;
import javax.inject.Singleton;


/**
 * Default implementation of EvalVisitorFactory.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Mark Knichel
 * @author Kai Huang
 */
@Singleton
public class EvalVisitorFactoryImpl implements EvalVisitorFactory {


  /** Map of all SoyJavaRuntimeFunctions (name to function). */
  private final Map<String, SoyJavaRuntimeFunction> soyJavaRuntimeFunctionsMap;


  @Inject
  public EvalVisitorFactoryImpl(Map<String, SoyJavaRuntimeFunction> soyJavaRuntimeFunctionsMap) {
    this.soyJavaRuntimeFunctionsMap = soyJavaRuntimeFunctionsMap;
  }


  @Override
  public EvalVisitor create(
      SoyMapData data, @Nullable SoyMapData ijData, Deque<Map<String, SoyData>> env) {

    return new EvalVisitor(soyJavaRuntimeFunctionsMap, data, ijData, env);
  }

}
