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

import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.jssrc.internal.ExtractMsgVariablesVisitor;
import com.google.template.soy.soytree.HtmlContext;
import com.google.template.soy.soytree.MsgFallbackGroupNode;

/**
 * Extracts nodes that idom cannot handle as statements into variables. TODO(slaks): Extract
 * attribute values.
 */
final class IncrementalDomExtractMsgVariablesVisitor extends ExtractMsgVariablesVisitor {
  @Override
  protected void wrapMsgFallbackGroupNodeHelper(
      MsgFallbackGroupNode msgFbGrpNode, IdGenerator nodeIdGen) {
    if (msgFbGrpNode.getHtmlContext() != HtmlContext.HTML_PCDATA) {
      super.wrapMsgFallbackGroupNodeHelper(msgFbGrpNode, nodeIdGen);
    }
  }
}
