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

package com.google.template.soy.msgs;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.msgs.restricted.SoyMsg;

import java.util.Iterator;


/**
 * Represents a full set of messages in some language/locale.
 *
 */
public interface SoyMsgBundle extends Iterable<SoyMsg> {


  /**
   * Gets the language/locale string of this bundle of messages.
   * @return The language/locale string of the messages provided by this bundle.
   */
  public String getLocaleString();


  /**
   * Retrieves a message by its unique message id.
   * @param msgId The message id of the message to retrieve.
   * @return The corresponding message, or null if not found.
   */
  public SoyMsg getMsg(long msgId);


  /**
   * Gets the number of messages in this bundle.
   * @return The number of messages in this bundle.
   */
  public int getNumMsgs();


  /**
   * Returns an iterator over all the messages.
   * @return An iterator over all the messages.
   */
  @Override public Iterator<SoyMsg> iterator();


  // -----------------------------------------------------------------------------------------------
  // Null object.


  /** Null object for SoyMsgBundle. */
  public static SoyMsgBundle EMPTY =
      new SoyMsgBundle() {

        @Override public String getLocaleString() {
          return null;
        }

        @Override public SoyMsg getMsg(long msgId) {
          return null;
        }

        @Override public int getNumMsgs() {
          return 0;
        }

        @Override public Iterator<SoyMsg> iterator() {
          return ImmutableList.<SoyMsg>of().iterator();
        }
      };

}
