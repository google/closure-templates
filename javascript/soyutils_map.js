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

/**
 * @fileoverview Interfaces and helper functions for Soy maps/proto maps/ES6
 *     maps.
 */
goog.module('soy.map');
goog.module.declareLegacyNamespace();

const UnsanitizedText = goog.require('goog.soy.data.UnsanitizedText');
const {assertString} = goog.require('goog.asserts');
const {shuffle} = goog.require('goog.array');

/**
 * Structural interface for representing Soy `map`s in JavaScript.
 *
 * <p>The Soy `map` type was originally represented in JavaScript by plain
 * objects (`Object<K,V>`). However, plain object access syntax (`obj['key']`)
 * is incompatible with the ES6 Map and jspb.Map APIs, both of which use
 * `map.get('key')`. In order to allow the Soy `map` type to interoperate with
 * ES6 Maps and proto maps, Soy now uses this interface to represent the `map`
 * type. (The Soy `legacy_object_literal_map` type continues to use plain
 * objects for backwards compatibility.)
 *
 * <p>This is a structural interface -- ES6 Map and jspb.Map implicitly
 * implement it without declaring that they do.
 *
 * @record
 * @template K, V
 */
class SoyMap {
  /**
   * @param {K} k
   * @return {V|undefined}
   */
  get(k) {}

  /**
   * Set method is required for the runtime method that copies the content of a
   * ES6 map to jspb map.
   * @param {K} k
   * @param {V} v
   * @return {!SoyMap<K, V>}
   */
  set(k, v) {}

  /**
   * @return {!IteratorIterable<K>} An iterator that contains the keys for each
   *     element in this map.
   */
  keys() {}

  /**
   * Returns an iterator over the [key, value] pair entries of this map.
   *
   * TODO(b/69049599): structural interfaces defeat property renaming.
   * This could cause anything in the compilation unit that has get() and
   * entries() methods to no longer rename entries(). If that increases code
   * size too much, we could use the keys() method instead in
   * $$mapToLegacyObjectMap. Not renaming "keys" is presumably ~43% less bad
   * than not renaming "entries".
   *
   * @return {!IteratorIterable<!Array<K|V>>}
   */
  entries() {}
}

/**
 * Converts an ES6 Map or jspb.Map into an equivalent legacy object map.
 * N.B.: although ES6 Maps and jspb.Maps allow many values to serve as map keys,
 * legacy object maps allow only string keys.
 * @param {!SoyMap<?, V>} map
 * @return {!Object<V>}
 * @template V
 */
function $$mapToLegacyObjectMap(map) {
  const obj = {};
  for (const [k, v] of map.entries()) {
    obj[assertString(k)] = v;
  }
  return obj;
}

/**
 * Gets the keys in a map as an array. There are no guarantees on the order.
 * @param {!SoyMap<K, V>} map The map to get the keys of.
 * @return {!Array<K>} The array of keys in the given map.
 * @template K, V
 */
function $$getMapKeys(map) {
  const keys = Array.from(map.keys());
  // The iteration order of Soy map keys and proto maps is documented as
  // undefined. But the iteration order of ES6 Maps is specified as insertion
  // order. In debug mode, shuffle the keys to hopefully catch callers that are
  // making assumptions about iteration order.
  if (goog.DEBUG) {
    shuffle(keys);
  }
  return keys;
}

/**
 * Saves the contents of a SoyMap to another SoyMap.
 * This is used for proto initialization in Soy. Protocol buffer in JS does not
 * generate setters for map fields. To construct a proto map field, we use this
 * help method to save the content of map literal to proto.
 * @param {!SoyMap<K, V>} jspbMap
 * @param {!SoyMap<K, V>} map
 * @template K, V
 */
function $$populateMap(jspbMap, map) {
  for (const [k, v] of map.entries()) {
    jspbMap.set(k, v);
  }
}

/**
 * SoyMaps, like ES6 Maps and proto maps, allow non-string values as map keys.
 * But UnsanitizedText keys still need to be coerced to strings so that
 * instances with identical textual content are considered identical for map
 * lookups.
 * @param {?} key The key that is being inserted into or looked up in the map.
 * @return {?} The key, coerced to a string if it is an UnsanitizedText object.
 */
function $$maybeCoerceKeyToString(key) {
  return key instanceof UnsanitizedText ? key.getContent() : key;
}

/**
 * Determines if the argument matches the soy.map.Map interface.
 * @param {?} map The object to check.
 * @return {boolean} True if it is a soy.map.Map, false otherwise.
 */
function $$isSoyMap(map) {
  return goog.isObject(map) && goog.isFunction(map.get) &&
      goog.isFunction(map.set) && goog.isFunction(map.keys) &&
      goog.isFunction(map.entries);
}

exports = {
  $$mapToLegacyObjectMap,
  $$maybeCoerceKeyToString,
  $$populateMap,
  $$getMapKeys,
  $$isSoyMap,
  // This is declared as SoyMap instead of Map to avoid shadowing ES6 Map, which
  // is used by $$legacyObjectMapToMap. But the external name can still be Map.
  Map: SoyMap,
};
