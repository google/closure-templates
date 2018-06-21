/*
 * Copyright 2017 Google Inc.
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
package com.google.template.soy.data;

import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;
import com.google.protobuf.Message;
import com.google.protobuf.TextFormat;
import javax.annotation.Nullable;

/** The value of a {@code velog} statement. */
@AutoValue
public abstract class LogStatement {
  public static LogStatement create(long id, @Nullable Message data, boolean logOnly) {
    return new AutoValue_LogStatement(id, data, logOnly);
  }

  LogStatement() {} // prevent subclasses outside the package

  /** The id of the element being logged, as specified by {@link LoggableElement#getId()}. */
  public abstract long id();

  /**
   * An optional proto that is logged by the {@code data="<...>"} expression. The type will be what
   * is specifed by the corresponding {@link LoggableElement#getProtoType()}.
   */
  @Nullable
  public abstract Message data();

  /**
   * The value of the {@code logonly="<...>"} expression. Default is {@code false} if not specified.
   */
  public abstract boolean logOnly();

  @Override
  public String toString() {
    return MoreObjects.toStringHelper("velog")
        .omitNullValues()
        .add("id", id())
        .add(
            "data",
            data() == null
                ? null
                : data().getDescriptorForType().getFullName()
                    + "{"
                    + TextFormat.shortDebugString(data())
                    + "}")
        .addValue(logOnly() ? "logonly" : null)
        .toString();
  }
}
