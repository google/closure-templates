/**
 * @fileoverview
 * Contains types and objects necessary for Soy-Idom runtime.
 */

import {assert} from 'goog:goog.asserts';  // from //javascript/closure/asserts
import SanitizedContentKind from 'goog:goog.soy.data.SanitizedContentKind'; // from //javascript/closure/soy:data
import {IjData} from 'goog:soy';  // from //javascript/template/soy:soy_usegoog_js
import {IdomFunctionMembers} from 'goog:soydata';  // from //javascript/template/soy:soy_usegoog_js
import * as incrementaldom from 'incrementaldom';  // from //third_party/javascript/incremental_dom:incrementaldom

import {IncrementalDomRenderer} from './api_idom';

/** Function that executes Idom instructions */
export type PatchFunction = (a?: {}) => void;

/** Function that executes before a patch and determines whether to proceed. */
export type SkipHandler = <T>(prev: T, next: T) => boolean;

/** Base class for a Soy element. */
export abstract class SoyElement<TData extends {}|null, TInterface extends {}> {
  // Node in which this object is stashed.
  private node: HTMLElement|null = null;
  private skipHandler:
      ((prev: TInterface, next: TInterface) => boolean)|null = null;

  constructor(protected data: TData, protected ijData?: IjData) {}

  /**
   * Patches the current dom node.
   * @param renderer Allows injecting a subclass of IncrementalDomRenderer
   *                 to customize the behavior of patches.
   */
  render(renderer = new IncrementalDomRenderer()) {
    assert(this.node);
    incrementaldom.patchOuter(this.node!, () => {
      // If there are parameters, they must already be specified.
      this.renderInternal(renderer, this.data!, true);
    });
  }

  /**
   * Stores the given node in this element object. Invoked (in the
   * generated idom JS) when rendering the open element of a template.
   */
  protected setNodeInternal(node: HTMLElement|undefined) {
    /**
     * This is null because it is possible that no DOM has been generated
     * for this Soy element
     * (see http://go/soy/reference/velog#the-logonly-attribute)
     */
    if (!node) {
      return;
    }
    this.node = node;
    // tslint:disable-next-line:no-any
    (node as any).__soy = this;
  }

  setSkipHandler(skipHandler: (prev: TInterface, next: TInterface) => boolean) {
    assert(!this.skipHandler, 'Only one skip handler is allowed.');
    this.skipHandler = skipHandler;
  }

  /**
   * Makes idom patch calls, inside of a patch context.
   * This returns true if the skip handler runs (after initial render) and
   * returns true.
   */
  protected renderInternal(
      renderer: IncrementalDomRenderer, data: TData,
      ignoreSkipHandler = false) {
    const newNode = new (
        this.constructor as
        {new (a: TData): SoyElement<TData, TInterface>})(data);
    if (!ignoreSkipHandler && this.node && this.skipHandler &&
        this.skipHandler(
            this as unknown as TInterface, newNode as unknown as TInterface)) {
      this.data = newNode.data;
      // This skips over the current node.
      renderer.alignWithDOM(
          this.node.localName, incrementaldom.getKey(this.node) as string);
      return true;
    }
    this.data = newNode.data;
    return false;
  }
}

/**
 * Type for transforming idom functions into functions that can be coerced
 * to strings.
 */
export interface IdomFunction extends IdomFunctionMembers {
  (idom: IncrementalDomRenderer): void;
  contentKind: SanitizedContentKind;
  toString: (renderer?: IncrementalDomRenderer) => string;
  toBoolean: () => boolean;
}
