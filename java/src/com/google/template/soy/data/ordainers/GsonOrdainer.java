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

package com.google.template.soy.data.ordainers;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import java.lang.reflect.Type;

/**
 * Ordainer for creating SanitizedContent objects with Gson data.
 *
 */
public final class GsonOrdainer {

  private GsonOrdainer() {
    // Not instantiable
  }

  /**
   * Generate sanitized js content using a default Gson serializer.
   *
   * @param obj The object to render as gson.
   * @return SanitizedContent containing the object rendered as a json string.
   */
  public static SanitizedContent serializeObject(Object obj) {
    return serializeObject(new Gson(), obj);
  }

  /**
   * Generate sanitized js content with provided Gson serializer.
   *
   * @param gson A Gson serializer.
   * @param obj The object to render as gson.
   * @return SanitizedContent containing the object rendered as a json string.
   */
  public static SanitizedContent serializeObject(Gson gson, Object obj) {
    return ordainJson(gson.toJson(obj));
  }

  /**
   * Generate sanitized js content with provided Gson serializer and object type.
   *
   * @param gson A Gson serializer.
   * @param obj The object to render as gson.
   * @param type the type of {@code obj}.
   * @return SanitizedContent containing the object rendered as a json string.
   */
  public static SanitizedContent serializeObject(Gson gson, Object obj, Type type) {
    return ordainJson(gson.toJson(obj, type));
  }

  /**
   * Serializes a JsonElement to string.
   *
   * @param element A Gson element.
   * @return SanitizedContent containing the object rendered as a json string.
   */
  public static SanitizedContent serializeElement(JsonElement element) {
    // NOTE: Because JsonElement doesn't have any particular mechanism preventing random classes
    // from impersonating it, this low-tech check prevents at least obvious misuse.
    Preconditions.checkArgument(
        element instanceof JsonArray
            || element instanceof JsonObject
            || element instanceof JsonNull
            || element instanceof JsonPrimitive);
    // JsonPrimitive(String).toString() is broken, so use Gson which uses a consistent set of
    // flags to produce embeddable JSON.
    return ordainJson(new Gson().toJson(element));
  }

  private static SanitizedContent ordainJson(String knownSafeJson) {
    return UnsafeSanitizedContentOrdainer.ordainAsSafe(knownSafeJson, ContentKind.JS);
  }
}
