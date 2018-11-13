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
package com.google.template.soy.exprtree;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;

/**
 * A node representing the {@code ve_data($ve, $data)} expression, to create a VE, with metedata
 * attached, for VE logging.
 */
public final class VeDataLiteralNode extends AbstractParentExprNode {

  public VeDataLiteralNode(SourceLocation loc) {
    super(loc);
  }

  private VeDataLiteralNode(VeDataLiteralNode orig, CopyState copyState) {
    super(orig, copyState);
  }

  @Override
  public Kind getKind() {
    return Kind.VE_DATA_LITERAL_NODE;
  }

  @Override
  public String toSourceString() {
    return "ve_data(" + getChild(0).toSourceString() + ", " + getChild(1).toSourceString() + ")";
  }

  @Override
  public ExprNode copy(CopyState copyState) {
    return new VeDataLiteralNode(this, copyState);
  }
}
