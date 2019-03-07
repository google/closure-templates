/**
 * @fileoverview
 *
 * Getters for Soy Idom runtime. Required because we don't want to incur a
 * runtime cost for requiring incrementaldom directly.
 */

import {assertInstanceof} from 'goog:goog.asserts';  // from //javascript/closure/asserts
import {IjData} from 'goog:soy';  // from //javascript/template/soy:soy_usegoog_js

import {SoyElement} from './element_lib_idom';

declare global {
  interface Node {
    __soy: SoyElement<{}, {}>|null;
  }
}

interface ElementCtor<TElement extends SoyElement<{}|null, {}>> {
  // tslint:disable-next-line:no-any Real parameter type is only used privately.
  new(data: any, ijData: IjData): TElement;
}

/** Retrieves the Soy element in a type-safe way. */
export function getSoy<TElement extends SoyElement<{}|null, {}>>(
    node: Node, elementCtor: ElementCtor<TElement>) {
  return assertInstanceof(node.__soy, elementCtor);
}

/** Retrieves the Soy element in a type-safe way, or null if it doesn't exist */
export function getSoyOptional<TElement extends SoyElement<{}, {}>>(
    node: Node, elementCtor: ElementCtor<TElement>) {
  if (!node.__soy) return null;
  return assertInstanceof(node.__soy, elementCtor);
}

/** Retrieves an untyped Soy element, or null if it doesn't exist. */
export function getSoyUntyped(node: Node) {
  return node.__soy;
}
