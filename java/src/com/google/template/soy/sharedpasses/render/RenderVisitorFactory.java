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

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.shared.SoyIdRenamingMap;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import com.google.template.soy.sharedpasses.render.EvalVisitor.EvalVisitorFactory;
import com.google.template.soy.soytree.TemplateRegistry;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Default implementation of RenderVisitorFactory.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
@Singleton
public final class RenderVisitorFactory {

  /** Map of all SoyJavaPrintDirectives (name to directive). */
  private final ImmutableMap<String, ? extends SoyJavaPrintDirective> soyJavaDirectivesMap;

  /** Factory for creating an instance of EvalVisitor. */
  private final EvalVisitorFactory evalVisitorFactory;

  @Inject
  public RenderVisitorFactory(
      ImmutableMap<String, ? extends SoyJavaPrintDirective> soyJavaDirectivesMap,
      EvalVisitorFactory evalVisitorFactory) {
    this.soyJavaDirectivesMap = soyJavaDirectivesMap;
    this.evalVisitorFactory = evalVisitorFactory;
  }

  // TODO(user): change this to a builder (maybe @AutoValue.Builder).
  /**
   * Creates a RenderVisitor.
   *
   * @param outputBuf The Appendable to append the output to.
   * @param templateRegistry A registry of all templates.
   * @param data The current template data.
   * @param ijData The current injected data.
   * @param activeDelPackageSelector The predicate for testing whether a given delpackage is active.
   *     Allowed to be null when known to be irrelevant, i.e. when not using delegates feature.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the Soy
   *     source.
   * @param xidRenamingMap The 'xid' renaming map, or null if not applicable.
   * @param cssRenamingMap The CSS renaming map, or null if not applicable.
   * @param debugSoyTemplateInfo If true, renders additional HTML comments for debug usages.
   * @return The newly created RenderVisitor instance.
   */
  public RenderVisitor create(
      Appendable outputBuf,
      TemplateRegistry templateRegistry,
      SoyRecord data,
      @Nullable SoyRecord ijData,
      @Nullable Predicate<String> activeDelPackageSelector,
      @Nullable SoyMsgBundle msgBundle,
      @Nullable SoyIdRenamingMap xidRenamingMap,
      @Nullable SoyCssRenamingMap cssRenamingMap,
      boolean debugSoyTemplateInfo) {

    return new RenderVisitor(
        soyJavaDirectivesMap,
        evalVisitorFactory,
        outputBuf,
        templateRegistry,
        data,
        ijData,
        activeDelPackageSelector,
        msgBundle,
        xidRenamingMap,
        cssRenamingMap,
        debugSoyTemplateInfo);
  }
}
