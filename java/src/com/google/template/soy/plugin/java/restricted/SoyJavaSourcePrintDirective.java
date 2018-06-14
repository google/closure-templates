/*
 * Copyright 2018 Google Inc.
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

package com.google.template.soy.plugin.java.restricted;

import com.google.template.soy.plugin.restricted.SoySourcePrintDirective;
import java.util.List;

/** A {@link SoySourcePrintDirective} that generates code to be called at Java render time. */
public interface SoyJavaSourcePrintDirective extends SoySourcePrintDirective {
  /** Instructs Soy as to how to render the directive. */
  JavaValue applyForJavaSrc(JavaValueFactory factory, List<JavaValue> args, JavaPluginContext ctx);
}
