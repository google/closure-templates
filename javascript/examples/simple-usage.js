/*
 * Copyright 2008 Google Inc.
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

// Usage of the simple examples.
// Author: Kai Huang


var numExamples = 0;

function writeExampleHeader() {
  numExamples++;
  document.write('<hr><b>' + numExamples + '</b><br>');
}

// -----------------------------------------------------------------------------
writeExampleHeader();
document.write(soy.examples.simple.helloWorld());

// -----------------------------------------------------------------------------
writeExampleHeader();
document.write(soy.examples.simple.helloName({name: 'Ana'}));

// -----------------------------------------------------------------------------
writeExampleHeader();
document.write(
    soy.examples.simple.helloNames({names: ['Bob', 'Cid', 'Dee']}));

// -----------------------------------------------------------------------------
document.write('<hr><b>The end</b>');
