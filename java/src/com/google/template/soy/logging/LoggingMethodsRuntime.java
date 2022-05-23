/*
 * Copyright 2020 Google Inc.
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

package com.google.template.soy.logging;

import com.google.template.soy.data.SoyVisualElement;
import com.google.template.soy.data.SoyVisualElementData;

/** Runtime implementations of the Soy VE metadata methods. */
public final class LoggingMethodsRuntime {

  public static LoggableElementMetadata getMetadata(SoyVisualElement ve) {
    return ve.metadata();
  }

  public static LoggableElementMetadata getVeMetadata(SoyVisualElementData veData) {
    return getMetadata(veData.ve());
  }

  private LoggingMethodsRuntime() {}
}
