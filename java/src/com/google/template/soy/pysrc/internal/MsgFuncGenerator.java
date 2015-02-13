/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.pysrc.internal;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.google.template.soy.msgs.internal.MsgUtils;
import com.google.template.soy.msgs.internal.MsgUtils.MsgPartsAndIds;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
import com.google.template.soy.msgs.restricted.SoyMsgRawTextPart;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyFunctionExprBuilder;
import com.google.template.soy.pysrc.restricted.PyStringExpr;
import com.google.template.soy.soytree.MsgNode;

/**
 * Class to generate python code for one {@link MsgNode}.
 *
 */
final class MsgFuncGenerator {

  static interface MsgFuncGeneratorFactory {
    // TODO(steveyang@): add local translation var map once it is implemented.
    public MsgFuncGenerator create(MsgNode node);
  }

  /** The msg node to generate the function calls from. */
  private final MsgNode msgNode;

  /** The generated msg id with the same algorithm for translation service. */
  private final long msgId;

  private final ImmutableList<SoyMsgPart> msgParts;

  /** The function builder for the prepare_*() method **/
  private final PyFunctionExprBuilder prepareFunc;

  /** The function builder for the render_*() method **/
  private final PyFunctionExprBuilder renderFunc;


  @AssistedInject
  MsgFuncGenerator(@Assisted MsgNode msgNode) {
    this.msgNode = msgNode;
    if (this.msgNode.isRawTextMsg()) {
      this.prepareFunc = new PyFunctionExprBuilder("prepare_literal");
      this.renderFunc = new PyFunctionExprBuilder("render_literal");
    } else {
      throw new UnsupportedOperationException();
    }

    MsgPartsAndIds msgPartsAndIds = MsgUtils.buildMsgPartsAndComputeMsgIdForDualFormat(msgNode);
    Preconditions.checkNotNull(msgPartsAndIds);

    this.msgId = msgPartsAndIds.id;
    this.msgParts = msgPartsAndIds.parts;

    Preconditions.checkState(!msgParts.isEmpty());
  }

  /**
   * Return the PyStringExpr for the render function call, because we know render always return
   * a string in Python runtime.
   */

  PyStringExpr getPyExpr() {
    addMsgAttributesToPrepare();
    if (this.msgNode.isRawTextMsg()) {
      return pyFuncForRawTextMsg();
    } else {
      // This branch will never be reached as the constructor will throw this error,
      // it is here to silent java compiler's failure for "error: missing return statement".
      throw new UnsupportedOperationException();
    }
  }

  private PyStringExpr pyFuncForRawTextMsg() {
    SoyMsgRawTextPart rawTextPart = (SoyMsgRawTextPart) msgParts.get(0);

    prepareFunc
        .addArg(new PyExpr(String.valueOf(msgId), Integer.MAX_VALUE))
        .addArg(new PyStringExpr("'" + rawTextPart.getRawText() + "'"));
    return renderFunc
        .addArg(prepareFunc.asPyExpr())
        .asPyStringExpr();
  }

  private void addMsgAttributesToPrepare() {
    if (this.msgNode.getMeaning() != null) {
      prepareFunc.addKwarg("meaning",
          new PyStringExpr("'" + this.msgNode.getMeaning() + "'"));
    }

    if (this.msgNode.getDesc() != null) {
      prepareFunc.addKwarg("desc",
          new PyStringExpr("'" + this.msgNode.getDesc() + "'"));
    }
  }
}
