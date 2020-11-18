/**
 * @fileoverview
 *
 * Getters for Soy Idom runtime. Required because we don't want to incur a
 * runtime cost for requiring incrementaldom directly.
 */

import {assert, assertInstanceof} from 'goog:goog.asserts';  // from //third_party/javascript/closure/asserts
import {IjData} from 'goog:goog.soy';  // from //third_party/javascript/closure/soy
import {isDataInitialized} from 'incrementaldom';  // from //third_party/javascript/incremental_dom:incrementaldom

import {SoyElement} from './element_lib_idom';

declare global {
  interface Node {
    // tslint:disable-next-line: enforce-name-casing
    __soy: SoyElement<{}, {}>|null;
    // tslint:disable-next-line: enforce-name-casing
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
    node: Node, elementCtor: ElementCtor<TElement>, message: string = '') {
  assert(isDataInitialized(node), `${message}

The DOM node was not rendered by idom.  If it's in a Wiz Component, make sure to
set 'use_incremental_dom = True'.  Otherwise, use IdomPatcherService or set up a
hydration model.
        `.trim());

  const untypedEl = getSoyUntyped(node);
  assert(untypedEl, `${message}

Did not find an {element} on the idom-rendered DOM node. Make sure that the node
is at the root of the {element}.
      `.trim());
  const soyEl = assertInstanceof(untypedEl, elementCtor, message && message + `

The DOM node has an {element} of type ${untypedEl!.constructor.name}.`);
  // We disable state syncing by default when elements are accessed on the
  // theory that the application wants to take control now.
  soyEl.setSyncState(false);
  return soyEl;
}

/** Retrieves the Soy element in a type-safe way, or null if it doesn't exist */
export function getSoyOptional<TElement extends SoyElement<{}, {}>>(
    node: Node, elementCtor: ElementCtor<TElement>, message?: string) {
  if (!node.__soy) return null;
  return getSoy(node, elementCtor, message);
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
