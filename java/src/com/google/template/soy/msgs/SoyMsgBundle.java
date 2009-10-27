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

import com.google.common.base.Objects;
import static com.google.common.base.Preconditions.checkArgument;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.template.soy.msgs.restricted.SoyMsg;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

import javax.annotation.Nullable;


/**
 * Represents a full set of messages in some language/locale.
 *
 * @author Kai Huang
 */
public class SoyMsgBundle implements Iterable<SoyMsg> {


  /** The language/locale string of this bundle's messages. */
  private final String localeString;

  /** Map from unique message id to message. Iteration order is sorted order of message id. */
  private final Map<Long, SoyMsg> msgMap;


  /**
   * Important: Do not use this constructor from non-Soy code! This constructor being public is only
   * a temporary state.
   *
   * Note: If there exist duplicate message ids in the {@code msgs} list, the first one wins.
   * However, the source paths from subsequent duplicates will be added to the source paths for the
   * message.
   *
   * @param localeString The language/locale string of this bundle of messages, or null if unknown.
   *     Should only be null for bundles newly extracted from source files. Should always be set
   *     for bundles parsed from message files/resources.
   * @param msgs The list of messages. List order will become the iteration order.
   */
  public SoyMsgBundle(@Nullable String localeString, List<SoyMsg> msgs) {

    this.localeString = localeString;

    SortedMap<Long, SoyMsg> tempMsgMap = Maps.newTreeMap();
    for (SoyMsg msg : msgs) {
      checkArgument(Objects.equal(msg.getLocaleString(), localeString));
      long msgId = msg.getId();

      if (!tempMsgMap.containsKey(msgId)) {  // new message id
        tempMsgMap.put(msgId, msg);

      } else {  // duplicate message id
        SoyMsg existingMsg = tempMsgMap.get(msgId);
        for (String source : msg.getSourcePaths()) {
          existingMsg.addSourcePath(source);
        }
      }
    }

    msgMap = ImmutableMap.copyOf(tempMsgMap);
  }


  /**
   * Gets the language/locale string of this bundle of messages (see note in
   * {@link SoyMsgBundle} class doc).
   * @return The language/locale string of the messages provided by this bundle.
   */
  public String getLocaleString() {
    return localeString;
  }


  /**
   * Retrieves a message by its unique message id.
   * @param msgId The message id of the message to retrieve.
   * @return The corresponding message, or null if not found.
   */
  public SoyMsg getMsg(long msgId) {
    return msgMap.get(msgId);
  }


  /**
   * Gets the number of messages in this bundle.
   * @return The number of messages in this bundle.
   */
  public int getNumMsgs() {
    return msgMap.size();
  }


  /**
   * Returns an iterator over all the messages.
   * @return An iterator over all the messages.
   */
  @Override public Iterator<SoyMsg> iterator() {
    return msgMap.values().iterator();
  }

}
