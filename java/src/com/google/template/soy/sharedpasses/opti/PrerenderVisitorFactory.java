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
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.shared.restricted.SoyJavaRuntimePrintDirective;
import com.google.template.soy.sharedpasses.render.RenderVisitor.RenderVisitorFactory;
import com.google.template.soy.soytree.TemplateRegistry;

import java.util.Deque;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;
import javax.inject.Singleton;


/**
 * A factory for creating PrerenderVisitor objects.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
@Singleton
public class PrerenderVisitorFactory implements RenderVisitorFactory {


  /** Map of all SoyJavaRuntimePrintDirectives (name to directive). */
  private final Map<String, SoyJavaRuntimePrintDirective> soyJavaRuntimeDirectivesMap;

  /** Factory for creating an instance of PreevalVisitor. */
  private final PreevalVisitorFactory preevalVisitorFactory;


  @Inject
  public PrerenderVisitorFactory(
      Map<String, SoyJavaRuntimePrintDirective> soyJavaRuntimeDirectivesMap,
      PreevalVisitorFactory preevalVisitorFactory) {
    this.soyJavaRuntimeDirectivesMap = soyJavaRuntimeDirectivesMap;
    this.preevalVisitorFactory = preevalVisitorFactory;
  }


  public PrerenderVisitor create(
      StringBuilder outputSb, TemplateRegistry templateRegistry,
      @Nullable SoyMapData data, @Nullable Deque<Map<String, SoyData>> env) {

    return new PrerenderVisitor(
        soyJavaRuntimeDirectivesMap, preevalVisitorFactory, this, outputSb, templateRegistry,
        data, env);
  }


  @Override
  public PrerenderVisitor create(Appendable outputSb,
      TemplateRegistry templateRegistry, @Nullable SoyMapData data,
      @Nullable SoyMapData ijData, @Nullable Deque<Map<String, SoyData>> env,
      @Nullable Set<String> activeDelPackageNames, @Nullable SoyMsgBundle msgBundle,
      @Nullable SoyCssRenamingMap cssRenamingMap) {

    // PrerenderVisitor cannot handle CallDelegateNode, MsgNode, and CssNode.
    Preconditions.checkArgument(activeDelPackageNames == null);
    Preconditions.checkArgument(msgBundle == null);
    Preconditions.checkArgument(cssRenamingMap == null);

    return new PrerenderVisitor(
        soyJavaRuntimeDirectivesMap, preevalVisitorFactory, this, outputSb, templateRegistry,
        data, env);
  }

}
