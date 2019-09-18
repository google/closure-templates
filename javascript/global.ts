/**
 * @fileoverview
 *
 * Getters for Soy Idom runtime. Required because we don't want to incur a
 * runtime cost for requiring incrementaldom directly.
 */

import {assertInstanceof} from 'goog:goog.asserts';  // from //javascript/closure/asserts
import {IjData} from 'goog:goog.soy';  // from //javascript/closure/soy

import {SoyElement} from './element_lib_idom';

declare global {
  interface Node {
    __soy: SoyElement<{}, {}>|null;
    __soy_tagged_for_skip: boolean;
  }
}

interface ElementCtor<TElement extends SoyElement<{}|null, {}>> {
  // tslint:disable-next-line:no-any Real parameter type is only used privately.
  new(data: any, ijData: IjData): TElement;
}

/**
 * Retrieves the Soy element in a type-safe way.
 *
 * <p>Requires that the node has been rendered by this element already. Will
 * throw an Error if this is not true.
 */
export function getSoy<TElement extends SoyElement<{}|null, {}>>(
    node: Node, elementCtor: ElementCtor<TElement>, message?: string) {
  const soyEl = assertInstanceof(getSoyUntyped(node), elementCtor, message);
  // We disable state syncing by default when elements are accessed on the
  // theory that the application wants to take control now.
  soyEl.setSyncState(false);
  return soyEl;
}

/** Retrieves the Soy element in a type-safe way, or null if it doesn't exist */
export function getSoyOptional<TElement extends SoyElement<{}, {}>>(
    node: Node, elementCtor: ElementCtor<TElement>) {
  if (!node.__soy) return null;
  return getSoy(node, elementCtor);
}

/**
 * When rehydrating a Soy element, tag the element so that rehydration stops at
 * the Soy element boundary.
 */
export function tagForSkip(node: Node) {
  node.__soy_tagged_for_skip = true;
}

/**
 * Once a soy element has been tagged, reset the tag.
 */
export function isTaggedForSkip(node: Node) {
  const isTaggedForSkip = node.__soy_tagged_for_skip;
  node.__soy_tagged_for_skip = false;
  return isTaggedForSkip;
}

/** Retrieves an untyped Soy element, or null if it doesn't exist. */
export function getSoyUntyped(node: Node) {
  return node.__soy;
}
