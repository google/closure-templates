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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;


/**
 * Represents a message in some language/locale. Contains information relevant to translation.
 *
 * @author Kai Huang
 */
public class SoyMsg {


  /** A unique id for this message (same across all translations). */
  private final long id;

  /** An alternate unique id for this message. */
  private final long altId;

  /** The language/locale string. */
  private final String localeString;

  /** The meaning string if set, otherwise null (usually null). */
  private final String meaning;

  /** The description for translators. */
  private final String desc;

  /** Whether this message should be hidden. */
  private final boolean isHidden;

  /** Content type of the document that this message will appear in. */
  private final String contentType;

  /** Location(s) of the source file(s) that this message comes from. */
  private Set<String> sourcePaths;

  /** Whether this is a plural/select message. */
  private final boolean isPlrselMsg;

  /** The parts that make up the message content. */
  private final List<SoyMsgPart> parts;


  /**
   * @param id A unique id for this message (same across all translations).
   * @param altId An alternate unique id for this message, or -1L if not applicable.
   * @param localeString The language/locale string, or null if unknown. Should only be null for
   *     messages newly extracted from source files. Should always be set for messages parsed from
   *     message files/resources.
   * @param meaning The meaning string, or null if not necessary (usually null). This is a string
   *     to create unique messages for two otherwise identical messages. This is usually done for
   *     messages used in different contexts. (For example, the same word can be used as a noun in
   *     one location and as a verb in another location, and the message texts would be the same
   *     but the messages would have meanings of "noun" and "verb".). May not be applicable to all
   *     message plugins.
   * @param desc The description for translators.
   * @param isHidden Whether this message should be hidden. May not be applicable to all message
   *     plugins.
   * @param contentType Content type of the document that this message will appear in
   *     (e.g. "{@code text/html}"). May not be applicable to all message plugins.
   * @param sourcePath Location of a source file that this message comes from. More sources can
   *     be added using {@code addSourcePath()}. May not be applicable to all message plugins.
   * @param isPlrselMsg Whether this is a plural/select message.
   * @param parts The parts that make up the message content.
   */
  public SoyMsg(
      long id, long altId, @Nullable String localeString, @Nullable String meaning,
      @Nullable String desc, boolean isHidden, @Nullable String contentType,
      @Nullable String sourcePath, boolean isPlrselMsg, List<SoyMsgPart> parts) {

    checkArgument(id >= 0L);
    checkArgument(altId >= -1L);
    this.id = id;
    this.altId = altId;
    this.localeString = localeString;
    this.meaning = meaning;
    this.desc = desc;
    this.isHidden = isHidden;
    this.contentType = contentType;
    this.sourcePaths = Sets.newHashSetWithExpectedSize(1);
    if (sourcePath != null) {
      this.sourcePaths.add(sourcePath);
    }
    this.isPlrselMsg = isPlrselMsg;
    this.parts = ImmutableList.copyOf(parts);
  }


  /**
   * @param id A unique id for this message (same across all translations).
   * @param localeString The language/locale string, or null if unknown. Should only be null for
   *     messages newly extracted from source files. Should always be set for messages parsed from
   *     message files/resources.
   * @param meaning The meaning string, or null if not necessary (usually null). This is a string
   *     to create unique messages for two otherwise identical messages. This is usually done for
   *     messages used in different contexts. (For example, the same word can be used as a noun in
   *     one location and as a verb in another location, and the message texts would be the same
   *     but the messages would have meanings of "noun" and "verb".). May not be applicable to all
   *     message plugins.
   * @param desc The description for translators.
   * @param isHidden Whether this message should be hidden. May not be applicable to all message
   *     plugins.
   * @param contentType Content type of the document that this message will appear in
   *     (e.g. "{@code text/html}"). May not be applicable to all message plugins.
   * @param sourcePath Location of a source file that this message comes from. More sources can
   *     be added using {@code addSourcePath()}. May not be applicable to all message plugins.
   * @param parts The parts that make up the message content.
   */
  public SoyMsg(
      long id, @Nullable String localeString, @Nullable String meaning, @Nullable String desc,
      boolean isHidden, @Nullable String contentType, @Nullable String sourcePath,
      List<SoyMsgPart> parts) {
    this(id, -1L, localeString, meaning, desc, isHidden, contentType, sourcePath, false, parts);
  }


  /** Returns the language/locale string. */
  public String getLocaleString() {
    return localeString;
  }

  /** Returns the unique id for this message (same across all translations). */
  public long getId() {
    return id;
  }

  /** Returns the alternate unique id for this message, or -1L if not applicable. */
  public long getAltId() {
    return altId;
  }

  /** Returns the meaning string if set, otherwise null (usually null). */
  public String getMeaning() {
    return meaning;
  }

  /** Returns the description for translators. */
  public String getDesc() {
    return desc;
  }

  /** Returns whether this message should be hiddens. */
  public boolean isHidden() {
    return isHidden;
  }

  /** Returns the content type of the document that this message will appear in. */
  public String getContentType() {
    return contentType;
  }

  /** @param sourcePath Location of a source file that this message comes from. */
  public void addSourcePath(String sourcePath) {
    sourcePaths.add(sourcePath);
  }

  /** Returns the location(s) of the source file(s) that this message comes from. */
  public Set<String> getSourcePaths() {
    return sourcePaths;
  }

  /** Returns whether this is a plural/select message. */
  public boolean isPlrselMsg() {
    return isPlrselMsg;
  }

  /** Returns the parts that make up the message content. */
  public List<SoyMsgPart> getParts() {
    return parts;
  }

}
