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

package com.google.template.soy.tofu.internal;

import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.sharedpasses.render.EvalVisitor.EvalVisitorFactory;
import com.google.template.soy.sharedpasses.render.RenderException;
import com.google.template.soy.sharedpasses.render.RenderVisitor;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.tofu.restricted.SoyTofuPrintDirective;

import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;


/**
 * Version of {@code RenderVisitor} for the Tofu backend.
 *
 * <p> Uses {@code SoyTofuFunction}s and {@code SoyTofuPrintDirective}s instead of
 * {@code SoyJavaRuntimeFunction}s and {@code SoyJavaRuntimePrintDirective}s.
 *
 */
class TofuRenderVisitor extends RenderVisitor {


  /** Map of all SoyTofuPrintDirectives (name to directive). */
  private final Map<String, SoyTofuPrintDirective> soyTofuDirectivesMap;


  /**
   * @param soyTofuDirectivesMap Map of all SoyTofuPrintDirectives (name to directive).
   * @param evalVisitorFactory Factory for creating an instance of EvalVisitor.
   * @param renderVisitorFactory Factory for creating an instance of EvalVisitor.
   * @param outputSb The StringBuilder to append the output to.
   * @param templateRegistry A registry of all templates. Should never be null (except in some unit
   *     tests).
   * @param data The current template data.
   * @param ijData The current injected data.
   * @param env The current environment, or null if this is the initial call.
   * @param activeDelPackageNames The set of active delegate package names. Allowed to be null when
   *     known to be irrelevant.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the
   *     Soy source.
   * @param cssRenamingMap The CSS renaming map, or null if not applicable.
   */
  protected TofuRenderVisitor(
      Map<String, SoyTofuPrintDirective> soyTofuDirectivesMap,
      EvalVisitorFactory evalVisitorFactory, RenderVisitorFactory renderVisitorFactory,
      StringBuilder outputSb, @Nullable TemplateRegistry templateRegistry,
      @Nullable SoyMapData data, @Nullable SoyMapData ijData,
      @Nullable Deque<Map<String, SoyData>> env, @Nullable Set<String> activeDelPackageNames,
      @Nullable SoyMsgBundle msgBundle, @Nullable SoyCssRenamingMap cssRenamingMap) {

    super(
        null, evalVisitorFactory, renderVisitorFactory, outputSb, templateRegistry, data, ijData,
        env, activeDelPackageNames, msgBundle, cssRenamingMap);

    this.soyTofuDirectivesMap = soyTofuDirectivesMap;
  }


  @Override protected String applyDirective(
      String directiveName, SoyData value, List<SoyData> args, PrintNode printNode) {

    // Get directive.
    SoyTofuPrintDirective directive = soyTofuDirectivesMap.get(directiveName);
    if (directive == null) {
      throw new RenderException(
          "Failed to find Soy print directive with name '" + directiveName + "'" +
          " (tag " + printNode.toSourceString() + ")");
    }

    // TODO: Add a pass to check num args at compile time.
    if (! directive.getValidArgsSizes().contains(args.size())) {
      throw new RenderException(
          "Print directive '" + directiveName + "' used with the wrong number of" +
          " arguments (tag " + printNode.toSourceString() + ").");
    }

    return directive.applyForTofu(value, args);
  }

}
