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

package com.google.template.soy.passes;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.ProtoInitNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.SoyTypeRegistry;

/**
 * A {@link CompilerFilePass} that looks at {@link FunctionNode}s and determines whether they should
 * be {@link ProtoInitNode}s instead.
 *
 * <p>TODO(user): This only exists because ExpressionParser doesn't have access to the type
 * registry. Once ExprParser gets rolled into the SoyFileParser, eliminate this pass and do it in
 * the parser.
 */
final class RewriteFunctionsPass extends CompilerFilePass {

  private final SoyTypeRegistry typeRegistry;

  RewriteFunctionsPass(SoyTypeRegistry typeRegistry) {
    this.typeRegistry = typeRegistry;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (FunctionNode fnNode : SoyTreeUtils.getAllNodesOfType(file, FunctionNode.class)) {
      // Function / proto ambiguity only happens for calls without params
      if (fnNode.numChildren() > 0) {
        continue;
      }

      SoyType type = typeRegistry.getType(fnNode.getFunctionName());
      if (type != null && type.getKind() == Kind.PROTO) {
        ProtoInitNode pNode =
            new ProtoInitNode(
                fnNode.getFunctionName(), ImmutableList.<String>of(), fnNode.getSourceLocation());

        fnNode.getParent().replaceChild(fnNode, pNode);
      }
    }
  }
}
