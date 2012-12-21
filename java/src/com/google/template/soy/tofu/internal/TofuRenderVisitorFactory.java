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
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.tofu.restricted.SoyTofuPrintDirective;

import java.util.Deque;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;
import javax.inject.Singleton;


/**
 * Implementation of RenderVisitorFactory for Tofu backend.
 *
 * @author Mark Knichel
 * @author Kai Huang
 */
@Singleton
class TofuRenderVisitorFactory {


  /** Map of all SoyTofuPrintDirectives (name to directive). */
  private final Map<String, SoyTofuPrintDirective> soyTofuDirectivesMap;

  /** Factory for creating an instance of TofuEvalVisitor. */
  private final TofuEvalVisitorFactory tofuEvalVisitorFactory;


  @Inject
  public TofuRenderVisitorFactory(
      Map<String, SoyTofuPrintDirective> soyTofuDirectivesMap,
      TofuEvalVisitorFactory tofuEvalVisitorFactory) {
    this.soyTofuDirectivesMap = soyTofuDirectivesMap;
    this.tofuEvalVisitorFactory = tofuEvalVisitorFactory;
  }


  /**
   * Creates a TofuRenderVisitor.
   *
   * @param outputBuf The Appendable to append the output to.
   * @param templateRegistry A registry of all templates.
   * @param data The current template data.
   * @param ijData The current injected data.
   * @param env The current environment, or null if this is the initial call.
   * @param activeDelPackageNames The set of active delegate package names. Allowed to be null
   *     when known to be irrelevant, i.e. when not using delegates feature.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the
   *     Soy source.
   * @param cssRenamingMap The CSS renaming map, or null if not applicable.
   * @return The newly created TofuRenderVisitor instance.
   */
  public TofuRenderVisitor create(
      Appendable outputBuf, TemplateRegistry templateRegistry, SoyMapData data,
      @Nullable SoyMapData ijData, @Nullable Deque<Map<String, SoyData>> env,
      @Nullable Set<String> activeDelPackageNames, @Nullable SoyMsgBundle msgBundle,
      @Nullable SoyCssRenamingMap cssRenamingMap) {

    return new TofuRenderVisitor(
        soyTofuDirectivesMap, tofuEvalVisitorFactory, outputBuf, templateRegistry, data, ijData,
        env, activeDelPackageNames, msgBundle, cssRenamingMap);
  }

}
