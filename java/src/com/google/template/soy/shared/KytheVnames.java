/*
 * Copyright 2022 Google Inc.
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

package com.google.template.soy.shared;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SourceLogicalPath;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.TemplateNode;

/**
 * Contains logic related to Kythe VNames that is shared between the various code generators in the
 * Soy compiler and the Soy kythe indexer.
 */
public class KytheVnames {

  private KytheVnames() {}

  public static final String LANG = "soy";

  public static String getPath(SourceLogicalPath path) {
    return path.path();
  }

  public static String getPath(SourceLocation sourceLocation) {
    return sourceLocation.getFilePath().path();
  }

  public static String getTemplateSignature(String templateFqn) {
    return templateFqn;
  }

  public static String getTemplateSignature(TemplateNode template) {
    return template.getTemplateName(); // template FQN
  }

  public static String getNamespaceSignature(SoyFileNode file) {
    return getNamespaceSignature();
  }

  public static String getNamespaceSignature() {
    return "#namespace#";
  }

  public static String getParamSignature(TemplateNode template, String paramName) {
    return getParamSignature(template.getTemplateName(), paramName);
  }

  public static String getParamSignature(String templateFqn, String paramName) {
    return templateFqn + "&" + paramName;
  }
}
