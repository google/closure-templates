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

package com.google.template.soy.jbcsrc;

import com.google.template.soy.exprtree.DataAccessNode;
import com.google.template.soy.exprtree.VarRefNode;

/**
 * A static analyzer for how templates will access variables.
 *
 * <p>This class contains a generic method for constructing a control flow graph through a given Soy
 * template. We then use this graph to answer questions about the template.
 *
 * <p>Supported queries
 *
 * <ul>
 *   <li>{@link #isResolved(DataAccessNode)} can tell us whether or not a particular variable or
 *       field reference has already been referenced at a given point and therefore {code
 *       SoyValueProvider#status()} has already returned {@link
 *       com.google.template.soy.jbcsrc.api.RenderResult#done()}.
 * </ul>
 *
 * <p>TODO(lukes): consider adding the following
 *
 * <ul>
 *   <li>Identify the last use of a variable. Currently we use variable scopes to decide when to
 *       stop saving/restoring variables, but if we knew they were no longer in use we could save
 *       generating save/restore logic.
 *   <li>Identify the last render of a variable. We could use this to save temporary buffers. See
 *       b/63530876.
 * </ul>
 */
interface TemplateAnalysis {

  /**
   * Returns true if this variable reference is definitely not the first reference to the variable
   * within a given template.
   */
  boolean isResolved(VarRefNode ref);

  /**
   * Returns true if this data access is definitely not the first reference to the field or item
   * within a given template.
   */
  boolean isResolved(DataAccessNode ref);
}
