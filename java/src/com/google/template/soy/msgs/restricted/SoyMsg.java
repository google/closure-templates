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
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.DoNotMock;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.base.SourceLocation;
import javax.annotation.Nullable;

/**
 * Represents a message in some language/locale. Contains information relevant to translation.
 *
 * <p>This class is heavily optimized for memory usage. In one major server using SoyTofu, Soy
 * messages comprised the single largest category of memory usage prior to optimization. Several
 * fields can be omitted entirely for render-only usage. ImmutableSet and ImmutableList are used
 * because their empty implementations are singletons.
 *
 */
@DoNotMock("use the builder() instead to construct a real instance")
@AutoValue
@Immutable
public abstract class SoyMsg {

  /** Returns a new builder for {@link SoyMsg}. */
  public static Builder builder() {
    return new Builder();
  }

  /** A builder for SoyMsg. */
  public static final class Builder {
    private long id;
    private long altId = -1;
    private @Nullable String localeString;
    private @Nullable String meaning;
    private @Nullable String desc;
    private boolean isHidden;
    private @Nullable String contentType;
    private final ImmutableSet.Builder<SourceLocation> sourceLocations = ImmutableSet.builder();
    private boolean isPlrselMsg;
    private ImmutableList<SoyMsgPart> parts;

    private Builder() {}

    /** @param id A unique id for this message (same across all translations). */
    public Builder setId(long id) {
      checkArgument(id >= 0L);
      this.id = id;
      return this;
    }

    /** @param altId An alternate unique id for this message. */
    public Builder setAltId(long altId) {
      checkArgument(altId >= 0L);
      this.altId = altId;
      return this;
    }

    /**
     * @param localeString The language/locale string, or null if unknown. Should only be null for
     *     messages newly extracted from source files. Should always be set for messages parsed from
     *     message files/resources
     */
    public Builder setLocaleString(String localeString) {
      this.localeString = checkNotNull(localeString);
      return this;
    }

    /**
     * @param meaning The meaning string, or null if not necessary (usually null). This is a string
     *     to create unique messages for two otherwise identical messages. This is usually done for
     *     messages used in different contexts. (For example, the same word can be used as a noun in
     *     one location and as a verb in another location, and the message texts would be the same
     *     but the messages would have meanings of "noun" and "verb".). May not be applicable to all
     *     message plugins.
     */
    public Builder setMeaning(String meaning) {
      this.meaning = checkNotNull(meaning);
      return this;
    }

    /** @param desc The description for translators. */
    public Builder setDesc(String desc) {
      this.desc = checkNotNull(desc);
      return this;
    }

    /**
     * @param isHidden Whether this message should be hidden. May not be applicable to all message
     *     plugins.
     */
    public Builder setIsHidden(boolean isHidden) {
      this.isHidden = isHidden;
      return this;
    }

    /**
     * @param contentType Content type of the document that this message will appear in (e.g.
     *     "{@code text/html}"). May not be applicable to all message plugins.
     */
    public Builder setContentType(String contentType) {
      this.contentType = checkNotNull(contentType);
      return this;
    }

    /** @param sourceLocation Location of a source file that this message comes from. */
    public Builder addSourceLocation(SourceLocation sourceLocation) {
      sourceLocations.add(checkNotNull(sourceLocation));
      return this;
    }

    /** @param sourceLocations Locations of source files that this message comes from. */
    public Builder addAllSourceLocations(Iterable<SourceLocation> sourceLocations) {
      this.sourceLocations.addAll(checkNotNull(sourceLocations));
      return this;
    }

    /** @param isPlrselMsg Whether this is a plural/select message. */
    public Builder setIsPlrselMsg(boolean isPlrselMsg) {
      this.isPlrselMsg = isPlrselMsg;
      return this;
    }

    /** @param parts The parts that make up the message content. */
    public Builder setParts(Iterable<? extends SoyMsgPart> parts) {
      this.parts = ImmutableList.copyOf(parts);
      checkArgument(!this.parts.isEmpty(), "Parts should never be empty");
      return this;
    }

    public SoyMsg build() {
      return new AutoValue_SoyMsg(
          localeString,
          id,
          altId,
          meaning,
          desc,
          isHidden,
          contentType,
          isPlrselMsg,
          parts,
          sourceLocations.build());
    }
  }

  SoyMsg() {
    // Prevent inheritance outside of the package.
  }

  /** Creates a new {@link Builder} based on the current instance. */
  Builder toBuilder() {
    Builder builder =
        builder()
            .setId(getId())
            .setIsHidden(isHidden())
            .setParts(getParts())
            .addAllSourceLocations(getSourceLocations())
            .setIsPlrselMsg(isPlrselMsg());
    if (getLocaleString() != null) {
      builder.setLocaleString(getLocaleString());
    }
    if (getMeaning() != null) {
      builder.setMeaning(getMeaning());
    }
    if (getDesc() != null) {
      builder.setDesc(getDesc());
    }
    if (getAltId() != -1) {
      builder.setAltId(getAltId());
    }
    if (getContentType() != null) {
      builder.setContentType(getContentType());
    }
    return builder;
  }

  /** Returns the language/locale string. */
  @Nullable
  public abstract String getLocaleString();

  /** Returns the unique id for this message (same across all translations). */
  public abstract long getId();

  /** Returns the alternate unique id for this message, or -1L if not applicable. */
  public abstract long getAltId();

  /** Returns the meaning string if set, otherwise null (usually null). */
  @Nullable
  public abstract String getMeaning();

  /** Returns the description for translators. */
  @Nullable
  public abstract String getDesc();

  /** Returns whether this message should be hidden. */
  public abstract boolean isHidden();

  /** Returns the content type of the document that this message will appear in. */
  @Nullable
  public abstract String getContentType();

  /** Returns whether this is a plural/select message. */
  public abstract boolean isPlrselMsg();

  /** Returns the parts that make up the message content. */
  public abstract ImmutableList<SoyMsgPart> getParts();

  /** Returns the location(s) of the source file(s) that this message comes from. */
  public abstract ImmutableSet<SourceLocation> getSourceLocations();
}
