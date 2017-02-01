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

import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Ordainer for creating SanitizedContent objects with Json data.
 *
 */
public final class JsonOrdainer {

  private JsonOrdainer() {
    // Not instantiable
  }

  /** Generate sanitized js content from a JSONObject. */
  public static SanitizedContent serializeJsonObject(JSONObject json) {
    return ordainJson(json.toString());
  }

  /** Generate sanitized js content from a JSONArray. */
  public static SanitizedContent serializeJsonArray(JSONArray json) {
    return ordainJson(json.toString());
  }

  private static SanitizedContent ordainJson(String knownSafeJson) {
    return UnsafeSanitizedContentOrdainer.ordainAsSafe(knownSafeJson, ContentKind.JS);
  }
}
