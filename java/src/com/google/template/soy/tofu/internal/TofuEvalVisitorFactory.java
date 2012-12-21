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

import com.google.inject.Inject;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.sharedpasses.render.EvalVisitor;
import com.google.template.soy.sharedpasses.render.EvalVisitor.EvalVisitorFactory;
import com.google.template.soy.tofu.restricted.SoyTofuFunction;

import java.util.Deque;
import java.util.Map;

import javax.annotation.Nullable;
import javax.inject.Singleton;


/**
 * Implementation of EvalVisitorFactory for Tofu backend.
 *
 * @author Mark Knichel
 * @author Kai Huang
 */
@Singleton
class TofuEvalVisitorFactory implements EvalVisitorFactory {


  /** Map of all SoyTofuFunctions (name to function). */
  private final Map<String, SoyTofuFunction> soyTofuFunctionsMap;


  @Inject
  public TofuEvalVisitorFactory(Map<String, SoyTofuFunction> soyTofuFunctionsMap) {
    this.soyTofuFunctionsMap = soyTofuFunctionsMap;
  }


  @Override
  public EvalVisitor create(
      SoyMapData data, @Nullable SoyMapData ijData, Deque<Map<String, SoyData>> env) {

    return new TofuEvalVisitor(soyTofuFunctionsMap, data, ijData, env);
  }

}
