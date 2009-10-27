/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.exprtree;


/**
 * Node representing a data reference.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * The children of the node are the parts of the data reference. Each child may be a DataRefKeyNode,
 * DataRefIndexNode, or any type of ExprNode. The first child must be a DataRefKeyNode.
 *
 * @author Kai Huang
 */
public class DataRefNode extends AbstractParentExprNode {


  @Override public String toSourceString() {

    StringBuilder sourceSb = new StringBuilder();

    boolean isFirst = true;
    for (ExprNode child : getChildren()) {

      if (isFirst) {
        sourceSb.append('$').append(child.toSourceString());
        isFirst = false;

      } else {
        if (child instanceof DataRefKeyNode || child instanceof DataRefIndexNode) {
          sourceSb.append('.').append(child.toSourceString());
        } else {
          sourceSb.append('[').append(child.toSourceString()).append(']');
        }
      }
    }

    return sourceSb.toString();
  }

}
