/**
 * @fileoverview
 *
 * Setters for optional Soy idom skip handlers. This is for code
 * that needs to run in hybrid idom + non-idom runtime. This allows setting
 * a skip handler if available in the Idom runtime.
 */

declare global {
  interface Node {
    __soy_skip_handler: (<T>(prev: T, next: T) => boolean)|undefined;
  }
}

/**
 * Setter for skip handler
 * @param el Dom node that is the root of a Soy element. The DOM node should be
 *           an {element} even if Incremental DOM isn't being used.
 * @param fn A function that corresponds to the skip handler of the Soy element.
 *           Because this is to be used in contexts without Incremental DOM,
 *           there is some loss in type information.
 * T should correspond to the corresponding interface for the Soy element.
 */
export function setSkipHandler(
    el: Element, fn: <T>(prev: T, next: T) => boolean) {
  el.__soy_skip_handler = fn;
}
