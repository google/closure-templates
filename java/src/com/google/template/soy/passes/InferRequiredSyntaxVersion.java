/*
 * Copyright 2013 Google Inc.
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

package com.google.template.soy.passes;

import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.HeaderParam;
import com.google.template.soy.soytree.defn.TemplateParam;

/**
 * Infers the minimum required syntax version based on the used features.
 *
 * <p>Currently the only inference rules are based on whether or not any template uses strict
 * params.
 *
 * <p>Version inference has been an unmitigated disaster. Do not add new rules here without deeply
 * considering how angry it will make future maintainers.
 *
 */
final class InferRequiredSyntaxVersion {

  /** The highest known required syntax version so far (during a pass). */
  public static SyntaxVersion infer(SoyFileNode node) {
    SyntaxVersion knownRequiredSyntaxVersion = SyntaxVersion.V1_0;
    for (TemplateNode template : node.getChildren()) {
      if (knownRequiredSyntaxVersion.num < SyntaxVersion.V2_3.num) {
        for (TemplateParam param : template.getParams()) {
          if (param instanceof HeaderParam) {
            knownRequiredSyntaxVersion = SyntaxVersion.V2_3;
            break;
          }
        }
      }
      if (knownRequiredSyntaxVersion.num < SyntaxVersion.V2_4.num
          && !template.getInjectedParams().isEmpty()) {
        knownRequiredSyntaxVersion = SyntaxVersion.V2_4;
      }
    }
    return knownRequiredSyntaxVersion;
  }
}
