/*
 * Copyright 2016 Google Inc.
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

package com.google.template.soy.incrementaldomsrc;

import com.google.template.soy.jssrc.internal.DelTemplateNamer;
import javax.inject.Inject;

/**
 * Names del templates using a modified namespace so that they can coexist with output from
 * GenJsCodeVisitor.
 */
final class IncrementalDomDelTemplateNamer extends DelTemplateNamer {
  @Inject
  public IncrementalDomDelTemplateNamer() {}

  @Override
  protected String getDelegateName(String delTemplateName) {
    return delTemplateName + ".idom";
  }
}
