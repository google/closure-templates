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

const {Message} = goog.require('jspb');
const {shuffle} = goog.require('goog.array');
/**
 * Required to fix declareLegacyNamespace, since soy is also
 * declareLegacyNamespace.
 * @suppress{extraRequire}
 */
goog.require('soy');

/**
 * Converts an ES6 Map or jspb.Map into an equivalent legacy object map.
 * N.B.: although ES6 Maps and jspb.Maps allow many values to serve as map keys,
 * legacy object maps allow only string keys.
 * @param {!ReadonlyMap<?, V>} map
 * @return {!Object<V>}
 * @template V
 */
function $$mapToLegacyObjectMap(map) {
  return Object.fromEntries(map.entries());
}

/**
 * Gets the keys in a map as an array. There are no guarantees on the order.
 * @param {!ReadonlyMap<K, V>} map The map to get the keys of.
 * @return {!Array<K>} The array of keys in the given map.
 * @template K, V
 */
function $$getMapKeys(map) {
  const keys = Array.from(map.keys());
  // The iteration order of Soy map keys and proto maps is documented as
  // undefined. But the iteration order of ES6 Maps is specified as insertion
  // order. In debug mode, shuffle the keys to hopefully catch callers that are
  // making assumptions about iteration order.
  // TODO(b/346022289): Delete?
  if (goog.DEBUG) {
    shuffle(keys);
  }
  return keys;
}


/**
 * @param {!ReadonlyMap<?, ?>} mapOne
 * @param {!ReadonlyMap<?, ?>} mapTwo
 * @return {!Map<?,?>}
 */
function $$concatMaps(mapOne, mapTwo) {
  const m = new Map();
  for (const [k, v] of mapOne.entries()) {
    m.set(k, v);
  }
  for (const [k, v] of mapTwo.entries()) {
    m.set(k, v);
  }
  return m;
}


/**
 * Gets the values in a map as an array. There are no guarantees on the order.
 * @param {!ReadonlyMap<K, V>} map The map to get the values of.
 * @return {!Array<V>} The array of values in the given map.
 * @template K, V
 */
function $$getMapValues(map) {
  const values = Array.from(map.values());
  if (goog.DEBUG) {
    shuffle(values);
  }
  return values;
}


/**
 * Gets the values in a map as an array. There are no guarantees on the order.
 * @param {!ReadonlyMap<?, ?>} map The map to get the values of.
 * @return {!Array<?>} The array of values in the given map.
 */
function $$getMapEntries(map) {
  const entries = [];
  for (const [k, v] of map.entries()) {
    entries.push({'key': k, 'value': v});
  }
  return entries;
}


/**
 * Gets the size of a map.
 * @param {!ReadonlyMap<?, ?>} map The map to get the values of.
 * @return {number} The number of keys in the map.
 * @suppress {missingProperties}
 */
function $$getMapLength(map) {
  if (typeof map.getLength === 'function') {
    // jspb.Map
    return map.getLength();
  } else if (typeof map.size === 'number') {
    return map.size;
  } else {
    throw new Error('Not a Map or jsbp.Map: ' + map);
  }
}


/**
 * Returns whether a proto is equal to the default instance of its type.
 * @param {!Message} proto A proto.
 * @return {boolean}
 */
function $$isProtoDefault(proto) {
  return Message.equals(proto, new proto.constructor());
}


/**
 * Returns whether two protos are equals.
 * @param {!Message} p1 A proto.
 * @param {!Message} p2 Another proto.
 * @return {boolean}
 */
function $$protoEquals(p1, p2) {
  return Message.equals(p1, p2);
}


exports = {
  $$mapToLegacyObjectMap,
  $$getMapKeys,
  $$isProtoDefault,
  $$protoEquals,
  $$getMapValues,
  $$getMapEntries,
  $$getMapLength,
  $$concatMaps,
};
