package com.google.common.html.types;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

/** Factory methods to create {@link SafeScript} consisting of JSON-marshalled data. */
public class SafeScriptsForJson {

  private static final Gson GSON = new Gson();

  /** Returns a {@link SafeScript} consisting of the JSON-marshalled {@code object}. */
  public static SafeScript fromJsonElement(JsonElement object) {
    return SafeScripts.create(GSON.toJson(object));
  }

  public SafeScriptsForJson() {}
}
