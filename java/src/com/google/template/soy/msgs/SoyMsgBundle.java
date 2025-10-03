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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.msgs.restricted.SoyMsg;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
import com.google.template.soy.msgs.restricted.SoyMsgRawParts;
import com.google.template.soy.msgs.restricted.SoyMsgRawTextPart;
import com.google.template.soy.msgs.restricted.SoyMsgViewerGrammaticalGenderPart;
import com.ibm.icu.util.ULocale;
import java.util.Iterator;
import javax.annotation.Nullable;

/**
 * Represents a full set of messages in some language/locale.
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
   * @deprecated Use {@link #getMsgPartsForRendering} instead.
   */
  @Nullable
  @Deprecated
  public String getBasicTranslation(long msgId) {
    return getBasicTranslation(msgId, GrammaticalGender.UNSPECIFIED);
  }

  /** Returns the plain translated text of a message with no placeholders. */
  @Nullable
  public String getBasicTranslation(long msgId, GrammaticalGender viewerGrammaticalGender) {
    SoyMsg msg = getMsg(msgId);
    if (msg == null) {
      return null;
    }
    return ((SoyMsgRawTextPart) findPartsForGender(msg.getParts(), viewerGrammaticalGender).get(0))
        .getRawText();
  }

  /**
   * Returns the message parts, or an empty array if there is no such message.
   *
   * @deprecated Use {@link #getMsgPartsForRendering} with the GrammaticalGender parameter instead.
   */
  @Nullable
  @Deprecated
  public SoyMsgRawParts getMsgPartsForRendering(long msgId) {
    return getMsgPartsForRendering(msgId, GrammaticalGender.UNSPECIFIED);
  }

  /**
   * Returns the message parts, or an empty array if there is no such message.
   *
   * <p>This is useful for rendering only use cases when the rest of the {@link SoyMsg} doesn't
   * matter. The default implementation is just {@link SoyMsg#getParts} but some subclasses may have
   * more efficient implementations
   */
  @Nullable
  public SoyMsgRawParts getMsgPartsForRendering(
      long msgId, GrammaticalGender viewerGrammaticalGender) {
    SoyMsg msg = getMsg(msgId);
    // This will be slow, but all callers should use the RenderOnlySoyMsgBundleImpl which will be
    // fast.
    if (msg == null) {
      return null;
    }
    return SoyMsgRawParts.fromMsgParts(findPartsForGender(msg.getParts(), viewerGrammaticalGender));
  }

  @Nullable
  protected ImmutableList<SoyMsgPart> findPartsForGender(
      ImmutableList<SoyMsgPart> parts, GrammaticalGender viewerGrammaticalGender) {
    var firstPart = parts.get(0);
    if (firstPart instanceof SoyMsgViewerGrammaticalGenderPart) {
      // If there's a gender part, it will include the whole message and be the only part.
      Preconditions.checkState(parts.size() == 1);
      return ((SoyMsgViewerGrammaticalGenderPart) firstPart)
          .getPartsForGender(viewerGrammaticalGender);
    }
    return parts;
  }

  /** Returns {@code true} if the message with the given id exists. */
  public boolean hasMsg(long msgId) {
    return getMsg(msgId) != null;
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
  public static final SoyMsgBundle EMPTY = empty(ULocale.ENGLISH);

  public static SoyMsgBundle empty(ULocale locale) {
    return new SoyMsgBundle() {

      @Override
      public String getLocaleString() {
        return locale.toString();
      }

      @Override
      @Nullable
      public ULocale getLocale() {
        return locale;
      }

      @Nullable
      @Override
      public SoyMsg getMsg(long msgId) {
        return null;
      }

      @Nullable
      @Override
      public SoyMsgRawParts getMsgPartsForRendering(
          long msgId, GrammaticalGender viewerGrammaticalGender) {
        return null;
      }

      @Override
      public boolean isRtl() {
        return locale.isRightToLeft();
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
}
