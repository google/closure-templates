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
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.shared.restricted.SoyJavaRuntimePrintDirective;
import com.google.template.soy.sharedpasses.render.EvalVisitor.EvalVisitorFactory;
import com.google.template.soy.sharedpasses.render.RenderVisitor.RenderVisitorFactory;
import com.google.template.soy.soytree.TemplateRegistry;

import java.util.Deque;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;
import javax.inject.Singleton;


/**
 * Default implementation of RenderVisitorFactory.
 *
 */
@Singleton
public class RenderVisitorFactoryImpl implements RenderVisitorFactory {


  /** Map of all SoyJavaruntimePrintDirectives (name to directive). */
  private final Map<String, SoyJavaRuntimePrintDirective> soyJavaRuntimeDirectivesMap;

  /** Factory for creating an instance of EvalVisitor. */
  private final EvalVisitorFactory evalVisitorFactory;


  @Inject
  public RenderVisitorFactoryImpl(
      Map<String, SoyJavaRuntimePrintDirective> soyJavaRuntimeDirectivesMap,
      EvalVisitorFactory evalVisitorFactory) {
    this.soyJavaRuntimeDirectivesMap = soyJavaRuntimeDirectivesMap;
    this.evalVisitorFactory = evalVisitorFactory;
  }


  @Override
  public RenderVisitor create(
      StringBuilder outputSb, TemplateRegistry templateRegistry,
      @Nullable SoyMapData data, @Nullable SoyMapData ijData,
      @Nullable Deque<Map<String, SoyData>> env, @Nullable Set<String> activeDelPackageNames,
      @Nullable SoyMsgBundle msgBundle, @Nullable SoyCssRenamingMap cssRenamingMap) {

    return new RenderVisitor(
        soyJavaRuntimeDirectivesMap, evalVisitorFactory, this, outputSb, templateRegistry,
        data, ijData, env, activeDelPackageNames, msgBundle, cssRenamingMap);
  }

}
