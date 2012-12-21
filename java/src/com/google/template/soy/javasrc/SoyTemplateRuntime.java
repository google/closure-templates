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

package com.google.template.soy.javasrc;

import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.shared.SoyCssRenamingMap;


/**
 * A compiled Soy template.
 *
 * @author Mike Samuel
 */
public interface SoyTemplateRuntime {

  /**
   * @param data The data to pass to the template.
   * @return {@code this}
   */
  SoyTemplateRuntime setData(SoyMapData data);


  /**
   * @param ijData The injected data for the template.
   * @return {@code this}
   */
  SoyTemplateRuntime setIjData(SoyMapData ijData);


  /**
   * @param cssRenamingMap Used to rename the selector text in <code>{css ...}</code> commands.
   * @return {@code this}
   */
  SoyTemplateRuntime setCssRenamingMap(SoyCssRenamingMap cssRenamingMap);


  /**
   * Applies the template to the given data to append output to the given buffer.
   *
   * @param out A buffer that receives the template output.
   */
  void render(StringBuilder out);


  /**
   * Applies the template to the given data to produce an output.
   *
   * @return The template output.
   */
  String render();
}
