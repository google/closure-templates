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
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.msgs.restricted.SoyMsg;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
import com.ibm.icu.util.ULocale;
import java.util.Iterator;
import javax.annotation.Nullable;

/**
 * Represents a full set of messages in some language/locale.
 *
 */
public abstract class SoyMsgBundle implements Iterable<SoyMsg> {
  /**
   * Gets the language/locale string of this bundle of messages.
   *
   * @return The language/locale string of the messages provided by this bundle.
   */
  @Nullable
  public abstract String getLocaleString();

  /**
   * Returns {@code true} if this is an RTL (right-to-left) locale. Subclasses are encouraged to
   * override this to provide efficient implementations.
   */
  public boolean isRtl() {
    return BidiGlobalDir.forStaticLocale(getLocaleString()) == BidiGlobalDir.RTL;
  }

  /**
   * Returns the {@link ULocale} of this message bundle. Subclasses are encouraged to override this
   * to provide efficient implementations.
   */
  @Nullable
  public ULocale getLocale() {
    return getLocaleString() == null ? null : new ULocale(getLocaleString());
  }

  /**
   * Returns the message parts, or an empty array if there is no such message.
   *
   * <p>This is useful for rendering only usecases when the rest of the {@link SoyMsg} doesn't
   * matter. The default implementation is just {@link SoyMsg#getParts} but some subclasses may have
   * more efficient implementations
   */
  public ImmutableList<SoyMsgPart> getMsgParts(long msgId) {
    SoyMsg msg = getMsg(msgId);
    return msg == null ? ImmutableList.<SoyMsgPart>of() : msg.getParts();
  }

  /**
   * Retrieves a message by its unique message id.
   *
   * @param msgId The message id of the message to retrieve.
   * @return The corresponding message, or null if not found.
   */
  public abstract SoyMsg getMsg(long msgId);

  /**
   * Gets the number of messages in this bundle.
   *
   * @return The number of messages in this bundle.
   */
  public abstract int getNumMsgs();

  /**
   * Returns an iterator over all the messages.
   *
   * @return An iterator over all the messages.
   */
  @Override
  public abstract Iterator<SoyMsg> iterator();

  // -----------------------------------------------------------------------------------------------
  // Null object.

  /** Null object for SoyMsgBundle, assumes English Locale. */
  public static final SoyMsgBundle EMPTY =
      new SoyMsgBundle() {

        @Override
        public String getLocaleString() {
          return "en";
        }

        @Override
        @Nullable
        public ULocale getLocale() {
          return ULocale.ENGLISH;
        }

        @Override
        public SoyMsg getMsg(long msgId) {
          return null;
        }

        @Override
        public ImmutableList<SoyMsgPart> getMsgParts(long msgId) {
          return ImmutableList.of();
        }

        @Override
        public boolean isRtl() {
          return false;
        }

        @Override
        public int getNumMsgs() {
          return 0;
        }

        @Override
        public Iterator<SoyMsg> iterator() {
          return ImmutableList.<SoyMsg>of().iterator();
        }
      };
}
