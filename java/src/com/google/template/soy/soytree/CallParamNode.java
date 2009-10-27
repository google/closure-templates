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

package com.google.template.soy.soytree;

import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.exprparse.ExpressionParser;
import com.google.template.soy.exprparse.ParseException;
import com.google.template.soy.exprparse.TokenMgrError;
import com.google.template.soy.exprtree.DataRefKeyNode;
import com.google.template.soy.exprtree.DataRefNode;


/**
 * Abstract node representing a 'param'.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public abstract class CallParamNode extends AbstractSoyCommandNode {


  /**
   * @param id The id for this node.
   * @param commandText The command text.
   */
  public CallParamNode(String id, String commandText) {
    super(id, "param", commandText);
  }


  /**
   * Helper used by subclass constructors to parse the 'key' attribute.
   * @param keyAttribute The 'key' attribute.
   * @return The key in the given 'key' attribute.
   * @throws SoySyntaxException If a syntax error is found.
   */
  protected String parseKeyHelper(String keyAttribute) throws SoySyntaxException {

    DataRefNode dataRef;
    try {
      dataRef = (new ExpressionParser("$" + keyAttribute)).parseDataReference().getChild(0);
    } catch (TokenMgrError tme) {
      throw createExceptionForInvalidKey(tme);
    } catch (ParseException pe) {
      throw createExceptionForInvalidKey(pe);
    }

    if (dataRef.numChildren() > 1) {
      throw new SoySyntaxException("The key in 'param' command text \"" + getCommandText() +
                                   "\" is not top-level (i.e. contains multiple keys).");
    }
    // First key should always be a DataRefKeyNode.
    return ((DataRefKeyNode) dataRef.getChild(0)).getKey();
  }


  /**
   * Private helper for parseKeyHelper().
   * @param cause The underlying exception.
   * @return The SoySyntaxException to be thrown.
   */
  private SoySyntaxException createExceptionForInvalidKey(Throwable cause) {
    //noinspection ThrowableInstanceNeverThrown
    return new SoySyntaxException(
        "Invalid key in 'param' command text \"" + getCommandText() + "\".", cause);
  }


  /**
   * Returns the param key.
   */
  public abstract String getKey();

}
