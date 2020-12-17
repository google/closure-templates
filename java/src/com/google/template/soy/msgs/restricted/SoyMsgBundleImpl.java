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

package com.google.template.soy.msgs.restricted;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.ibm.icu.util.ULocale;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import javax.annotation.Nullable;

/**
 * Represents a full set of messages in some language/locale.
 *
 * <p>Important: Only use this class from message plugins!
 *
 */
@Immutable
public class SoyMsgBundleImpl extends SoyMsgBundle {
  /** The language/locale string of this bundle's messages. */
  private final String localeString;
  private final ULocale locale;
  private final boolean isRtl;

  /** Map from unique message id to message. Iteration order is sorted order of message id. */
  private final ImmutableMap<Long, SoyMsg> msgMap;

  /**
   * Note: If there exist duplicate message ids in the {@code msgs} list, an exception will be
   * thrown. If this is not desired, call the constructor that takes a merging policy.
   *
   * @param localeString The language/locale string of this bundle of messages, or null if unknown.
   *     Should only be null for bundles newly extracted from source files. Should always be set for
   *     bundles parsed from message files/resources.
   * @param msgs The list of messages. List order will become the iteration order.
   */
  public SoyMsgBundleImpl(@Nullable String localeString, List<SoyMsg> msgs) {
    this(
        localeString,
        msgs,
        (m1, m2) -> {
          throw new IllegalStateException("Found 2 messages with id: " + m1.getId());
        });
  }
  /**
   * Note: If there exist duplicate message ids in the {@code msgs} list, the first one wins.
   * However, the source paths from subsequent duplicates will be added to the source paths for the
   * message.
   *
   * @param localeString The language/locale string of this bundle of messages, or null if unknown.
   *     Should only be null for bundles newly extracted from source files. Should always be set for
   *     bundles parsed from message files/resources.
   * @param msgs The list of messages. List order will become the iteration order.
   * @param merger A function that describes how to merge messages with identical ids.
   */
  public SoyMsgBundleImpl(
      @Nullable String localeString,
      List<SoyMsg> msgs,
      BiFunction<SoyMsg, SoyMsg, Optional<SoyMsg>> merger) {

    this.localeString = localeString;
    this.locale = localeString == null ? null : new ULocale(localeString);
    this.isRtl = BidiGlobalDir.forStaticLocale(localeString) == BidiGlobalDir.RTL;

    // Preserve the ordering of the input.
    Map<Long, SoyMsg> tempMsgMap = new LinkedHashMap<>();
    for (SoyMsg msg : msgs) {
      checkArgument(Objects.equals(msg.getLocaleString(), localeString));
      long msgId = msg.getId();

      SoyMsg existingMsg = tempMsgMap.get(msgId);
      if (existingMsg == null) { // new message id
        tempMsgMap.put(msgId, msg);

      } else { // duplicate message id, delegate to merging algorithm
        merger.apply(existingMsg, msg).ifPresent(merged -> tempMsgMap.put(msgId, merged));
      }
    }

    msgMap = ImmutableMap.copyOf(tempMsgMap);
  }


  @Override
  public String getLocaleString() {
    return localeString;
  }

  @Override
  @Nullable
  public ULocale getLocale() {
    return locale;
  }

  @Override
  public boolean isRtl() {
    return isRtl;
  }

  @Override
  public SoyMsg getMsg(long msgId) {
    return msgMap.get(msgId);
  }

  @Override
  public int getNumMsgs() {
    return msgMap.size();
  }

  @Override
  public Iterator<SoyMsg> iterator() {
    return msgMap.values().iterator();
  }
}
