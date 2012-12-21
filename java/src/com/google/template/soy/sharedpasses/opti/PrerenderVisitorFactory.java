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

import com.google.inject.Inject;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.shared.restricted.SoyJavaRuntimePrintDirective;
import com.google.template.soy.soytree.TemplateRegistry;

import java.util.Deque;
import java.util.Map;

import javax.annotation.Nullable;
import javax.inject.Singleton;


/**
 * A factory for creating PrerenderVisitor objects.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
@Singleton
public class PrerenderVisitorFactory {


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


  /**
   * Creates a PrerenderVisitor.
   *
   * @param outputBuf The Appendable to append the output to.
   * @param templateRegistry A registry of all templates.
   * @param data The current template data.
   * @param env The current environment, or null if this is the initial call.
   * @return The newly created PrerenderVisitor instance.
   */
  public PrerenderVisitor create(
      Appendable outputBuf, TemplateRegistry templateRegistry, SoyMapData data,
      @Nullable Deque<Map<String, SoyData>> env) {

    return new PrerenderVisitor(
        soyJavaRuntimeDirectivesMap, preevalVisitorFactory, outputBuf, templateRegistry, data, env);
  }

}
