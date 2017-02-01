/*
 * Copyright 2013 Google Inc.
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

package com.google.template.soy.msgs.restricted;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.template.soy.msgs.SoyMsgBundle;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.SortedMap;
import javax.annotation.Nullable;

/**
 * Represents all renderable messages in a locale.
 *
 * <p>This saves significant memory from the normal SoyMsgBundleImpl, but doesn't store details like
 * message descriptions. This also has small runtime performance penalties, such as using binary
 * search instead of hash tables, constructing wrapper objects on the fly, and computing properties
 * of the message instead of storing them.
 *
 */
final class RenderOnlySoyMsgBundleImpl implements SoyMsgBundle {

  /** The language/locale string of this bundle's messages. */
  private final String localeString;

  /**
   * Sorted array of message ID's that can be binary searched.
   *
   * <p>Importantly, this doesn't use any generic List type, to avoid wrapper Long objects.
   */
  private final long[] idArray;

  /**
   * Array containing either SoyMsgPart or ImmutableList<SoyMsgPart> in the same order as idArray.
   */
  private final Object[] valueArray;

  /**
   * Constructs a map of render-only soy messages. This implementation saves memory but doesn't
   * store all fields necessary during extraction.
   *
   * @param localeString The language/locale string of this bundle of messages, or null if unknown.
   *     Should only be null for bundles newly extracted from source files. Should always be set for
   *     bundles parsed from message files/resources.
   * @param msgs The list of messages. List order will become the iteration order. Duplicate message
   *     ID's are not permitted.
   */
  public RenderOnlySoyMsgBundleImpl(@Nullable String localeString, Iterable<SoyMsg> msgs) {

    this.localeString = localeString;

    // First, build a sorted map from message ID to the message representation.
    SortedMap<Long, Object> partsMap = Maps.newTreeMap();
    for (SoyMsg msg : msgs) {
      checkArgument(Objects.equals(msg.getLocaleString(), localeString));
      checkArgument(
          msg.getAltId() < 0, "RenderOnlySoyMsgBundleImpl doesn't support alternate ID's.");
      long msgId = msg.getId();
      checkArgument(
          !partsMap.containsKey(msgId),
          "Duplicate messages are not permitted in the render-only impl.");

      List<SoyMsgPart> parts = msg.getParts();
      checkArgument(
          MsgPartUtils.hasPlrselPart(parts) == msg.isPlrselMsg(),
          "Message's plural/select status is inconsistent -- internal compiler bug.");
      // Save memory: don't store the list if there's only one item.
      if (parts.size() == 1) {
        partsMap.put(msgId, parts.get(0));
      } else {
        partsMap.put(msgId, ImmutableList.copyOf(parts));
      }
    }

    // Using parallel long[] and Object[] arrays saves memory versus using a Map, because it avoids
    // having to wrap the longs in a new Long(), and avoids wrapping the key/value pair in an
    // Entry. Also, using a sorted array utilizes memory better, since unlike a hash table, you
    // need neither a linked list nor empty spaces in the hash table.
    idArray = new long[partsMap.size()];
    valueArray = new Object[partsMap.size()];

    // Build the arrays in the same order as the sorted map. Note we can't use toArray() since it
    // won't create a primitive long[] (only Long wrappers).
    int index = 0;
    for (Map.Entry<Long, Object> entry : partsMap.entrySet()) {
      idArray[index] = entry.getKey();
      valueArray[index] = entry.getValue();
      index++;
    }
    checkState(index == partsMap.size());
  }

  /** Brings a message back to life from only its ID and parts. */
  @SuppressWarnings("unchecked") // The constructor guarantees the type of ImmutableList.
  private SoyMsg resurrectMsg(long id, Object value) {
    // Remember: If there's only one message part, we don't store the list wrapper.
    ImmutableList<SoyMsgPart> parts =
        (value instanceof SoyMsgPart)
            ? ImmutableList.of((SoyMsgPart) value)
            : ((ImmutableList<SoyMsgPart>) value);
    return new SoyMsg(id, localeString, MsgPartUtils.hasPlrselPart(parts), parts);
  }

  @Override
  public String getLocaleString() {
    return localeString;
  }

  @Override
  public SoyMsg getMsg(long msgId) {
    int index = Arrays.binarySearch(idArray, msgId);
    return index >= 0 ? resurrectMsg(msgId, valueArray[index]) : null;
  }

  @Override
  public int getNumMsgs() {
    return idArray.length;
  }

  @Override
  public Iterator<SoyMsg> iterator() {
    return new Iterator<SoyMsg>() {
      int index = 0;

      @Override
      public boolean hasNext() {
        return index < idArray.length;
      }

      @Override
      public SoyMsg next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        SoyMsg result = resurrectMsg(idArray[index], valueArray[index]);
        index++;
        return result;
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException("Iterator is immutable");
      }
    };
  }
}
