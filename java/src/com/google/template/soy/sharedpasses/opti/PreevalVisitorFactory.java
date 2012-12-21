/*
 * Copyright 2011 Google Inc.
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

package com.google.template.soy.sharedpasses.opti;

import com.google.common.base.Preconditions;
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
 * A factory for creating PreevalVisitor objects.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
@Singleton
public class PreevalVisitorFactory implements EvalVisitorFactory {


  /** Map of all SoyJavaRuntimeFunctions (name to function). */
  private final Map<String, SoyJavaRuntimeFunction> soyJavaRuntimeFunctionsMap;


  /**
   * @param soyJavaRuntimeFunctionsMap Map of all SoyJavaRuntimeFunctions (name to function).
   */
  @Inject
  public PreevalVisitorFactory(Map<String, SoyJavaRuntimeFunction> soyJavaRuntimeFunctionsMap) {
    this.soyJavaRuntimeFunctionsMap = soyJavaRuntimeFunctionsMap;
  }


  public PreevalVisitor create(SoyMapData data, Deque<Map<String, SoyData>> env) {

    return new PreevalVisitor(soyJavaRuntimeFunctionsMap, data, env);
  }


  @Override
  public PreevalVisitor create(
      SoyMapData data, @Nullable SoyMapData ijData, Deque<Map<String, SoyData>> env) {

    // PreevalVisitor cannot handle ijData references.
    Preconditions.checkArgument(ijData == null);

    return new PreevalVisitor(soyJavaRuntimeFunctionsMap, data, env);
  }

}
