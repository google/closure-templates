/**
 * Copyright 2022 Google Inc.
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
 *
 * @fileoverview Module for selective usage of the custom-element-shim.
 */

let implementation = (element: HTMLElement) => {};

/**
 * Custom Elements shim for upgrading an element. Implementation needs to be
 * set with `setUpgradeElement` prior to usage, otherwise defaults to a no-op.
 */
export function upgradeElement(element: HTMLElement) {
  implementation(element);
}

/**
 * Set the implementation of `upgradeElement` exported from this module.
 */
export function setUpgradeElement(
    upgradeFunction: (element: HTMLElement) => void) {
  implementation = upgradeFunction;
}
