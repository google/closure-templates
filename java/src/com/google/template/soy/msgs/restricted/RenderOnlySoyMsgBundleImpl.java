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

import static java.util.Comparator.comparing;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.ibm.icu.util.ULocale;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Represents all renderable messages in a locale.
 *
 * <p>This saves significant memory from the normal SoyMsgBundleImpl, but doesn't store details like
 * message descriptions. This also has small runtime performance penalties, such as using binary
 * search instead of hash tables, constructing wrapper objects on the fly, and computing properties
 * of the message instead of storing them.
 */
public final class RenderOnlySoyMsgBundleImpl extends SoyMsgBundle {

  /** A minimal {@link SoyMsg} representation that is required for this implementation. */
  @AutoValue
  public abstract static class RenderOnlySoyMsg {
    public static RenderOnlySoyMsg create(long id, SoyMsgRawParts parts) {
      return new AutoValue_RenderOnlySoyMsgBundleImpl_RenderOnlySoyMsg(id, parts);
    }

    @Nullable
    public static RenderOnlySoyMsg create(SoyMsg msg) {
      if (isValidMsgPartsForSoyRendering(msg.getParts())) {
        return new AutoValue_RenderOnlySoyMsgBundleImpl_RenderOnlySoyMsg(
            msg.getId(), SoyMsgRawParts.fromMsgParts(msg.getParts()));
      }

      return null;
    }

    abstract long id();

    abstract SoyMsgRawParts parts();

    RenderOnlySoyMsg() {}
  }

  /** The language/locale string of this bundle's messages. */
  private final String localeString;

  private final ULocale locale;
  private final boolean isRtl;

  private final RenderOnlyMsgIndex.Accessor accesor;
  private final int size;

  /**
   * Constructs a map of render-only soy messages. This implementation saves memory but doesn't
   * store all fields necessary during extraction.
   *
   * @param localeString The language/locale string of this bundle of messages, or null if unknown.
   *     Should only be null for bundles newly extracted from source files. Should always be set for
   *     bundles parsed from message files/resources.
   * @param bundle The list of messages. List order will become the iteration order. Duplicate
   *     message ID's are not permitted.
   */
  @VisibleForTesting
  RenderOnlySoyMsgBundleImpl(
      RenderOnlyMsgIndex messageIndex, @Nullable String localeString, SoyMsgBundle bundle) {
    this(
        messageIndex,
        localeString,
        Iterables.filter(Iterables.transform(bundle, RenderOnlySoyMsg::create), m -> m != null));
  }

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
  public RenderOnlySoyMsgBundleImpl(
      RenderOnlyMsgIndex messageIndex,
      @Nullable String localeString,
      Iterable<RenderOnlySoyMsg> msgs) {

    this.localeString = localeString;
    this.locale = localeString == null ? null : new ULocale(localeString);
    this.isRtl = BidiGlobalDir.forStaticLocale(localeString) == BidiGlobalDir.RTL;

    var copy = ImmutableList.copyOf(msgs);
    this.accesor = messageIndex.buildAccessor(copy);
    this.size = copy.size();
  }

  /** Copies a RenderOnlySoyMsgBundleImpl, replacing only the localeString. */
  public RenderOnlySoyMsgBundleImpl(
      @Nullable String localeString, RenderOnlySoyMsgBundleImpl exemplar) {

    this.localeString = localeString;
    this.locale = localeString == null ? null : new ULocale(localeString);
    this.isRtl = BidiGlobalDir.forStaticLocale(localeString) == BidiGlobalDir.RTL;
    this.accesor = exemplar.accesor;
    this.size = exemplar.size;
  }

  /** Brings a message back to life from only its ID and parts. */
  // The constructor guarantees the type of ImmutableList.
  private SoyMsg resurrectMsg(long id, SoyMsgRawParts rawParts) {
    return SoyMsg.builder()
        .setId(id)
        .setLocaleString(localeString)
        .setIsPlrselMsg(rawParts.isPlrselMsg())
        .setParts(rawParts.toSoyMsgParts())
        .build();
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


  @Nullable
  @Override
  public SoyMsg getMsg(long msgId) {
    var value = getMsgPartsForRendering(msgId);
    return value != null ? resurrectMsg(msgId, value) : null;
  }

  @Override
  public boolean hasMsg(long msgId) {
    return this.accesor.has(msgId);
  }

  @Nullable
  @Override
  public SoyMsgRawParts getMsgPartsForRendering(long msgId) {
    return this.accesor.getParts(msgId);
  }

  @Override
  @Nullable
  public String getBasicTranslation(long msgId) {
    return this.accesor.getBasicTranslation(msgId);
  }

  @Override
  public int getNumMsgs() {
    return size;
  }

  @Override
  public Iterator<SoyMsg> iterator() {
    // This is lame, but we are supposed to be sorted, so just copy and sort all the messages.
    // We could do something more efficient, but this is not a hot path. In fact this method is
    // never called in production.
    List<SoyMsg> msgs = new ArrayList<>(size);
    accesor.forEach((id, parts) -> msgs.add(resurrectMsg(id, parts)));
    Collections.sort(msgs, comparing(SoyMsg::getId));
    return msgs.iterator();
  }

  /**
   * Not every message that we might parse out of an XTB is valid for soy renderings. Notably we
   * only allow a single top level plural or select clause and our memory optimization relies on it.
   *
   * <p>Such messages are allowed by the spec but are not used by soy so we can safely ignore them.
   */
  static boolean isValidMsgPartsForSoyRendering(ImmutableList<SoyMsgPart> parts) {
    var numPlrSel =
        parts.stream()
            .filter(part -> part instanceof SoyMsgPluralPart || part instanceof SoyMsgSelectPart)
            .count();
    if (numPlrSel > 1) {
      return false;
    }
    if (numPlrSel == 1) {
      if (parts.size() == 1) {
        if (parts.get(0) instanceof SoyMsgPluralPart) {
          var pluralPart = (SoyMsgPluralPart) parts.get(0);
          for (var casePart : pluralPart.getCases()) {
            if (!isValidMsgPartsForSoyRendering(casePart.parts())) {
              return false;
            }
          }
          return true;
        } else if (parts.get(0) instanceof SoyMsgSelectPart) {
          var selectPart = (SoyMsgSelectPart) parts.get(0);
          for (var casePart : selectPart.getCases()) {
            if (!isValidMsgPartsForSoyRendering(casePart.parts())) {
              return false;
            }
          }
          return true;
        }
      } else {
        // if there is a plr/sel part, it should be the only part.
        return false;
      }
    }
    return true;
  }
}
