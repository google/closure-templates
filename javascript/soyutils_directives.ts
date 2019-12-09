/*
 * Copyright 2018 Google Inc.
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

import SanitizedContentKind from 'goog:goog.soy.data.SanitizedContentKind'; // from //javascript/closure/soy:data
import * as soy from 'goog:soy';  // from //javascript/template/soy:soy_usegoog_js
import {isAttribute} from 'goog:soy.checks';  // from //javascript/template/soy:checks
import {ordainSanitizedHtml} from 'goog:soydata.VERY_UNSAFE';  // from //javascript/template/soy:soy_usegoog_js

import {IncrementalDomRenderer} from './api_idom';
import {IdomFunction} from './element_lib_idom';


function isIdomFunctionType(
// tslint:disable-next-line:no-any
    value: any, type: SanitizedContentKind): value is IdomFunction {
  return value && value.isInvokableFn &&
      (value as IdomFunction).contentKind === type;
}

/**
 * Specialization of filterHtmlAttributes for Incremental DOM that can handle
 * attribute functions gracefully. In any other situation, this delegates to
 * the regular escaping directive.
 */
// tslint:disable-next-line:no-any
function filterHtmlAttributes(value: any) {
  if (isIdomFunctionType(value, SanitizedContentKind.ATTRIBUTES) ||
      isAttribute(value)) {
    return value;
  }
  return soy.$$filterHtmlAttributes(value);
}

/**
 * Specialization of escapeHtml for Incremental DOM that can handle
 * html functions gracefully. In any other situation, this delegates to
 * the regular escaping directive.
 */
// tslint:disable-next-line:no-any
function escapeHtml(value: any, renderer: IncrementalDomRenderer) {
  if (isIdomFunctionType(value, SanitizedContentKind.HTML)) {
    return ordainSanitizedHtml(value.toString(renderer));
  }
  return soy.$$escapeHtml(value);
}

/**
 * Specialization of bidiUnicodeWrap for Incremental DOM that can handle
 * html functions gracefully. In any other situation, this delegates to
 * the regular escaping directive.
 */
function bidiUnicodeWrap(
    // tslint:disable-next-line:no-any
    bidiGlobalDir: number, value: any, renderer: IncrementalDomRenderer) {
  if (isIdomFunctionType(value, SanitizedContentKind.HTML)) {
    return soy.$$bidiUnicodeWrap(
        bidiGlobalDir, ordainSanitizedHtml(value.toString(renderer)));
  }
  return soy.$$bidiUnicodeWrap(bidiGlobalDir, value);
}

export {
  filterHtmlAttributes as $$filterHtmlAttributes,
  escapeHtml as $$escapeHtml,
  bidiUnicodeWrap as $$bidiUnicodeWrap,
  isIdomFunctionType as $$isIdomFunctionType,
};
