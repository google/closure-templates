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

// Usage of the features examples.
// Author: Kai Huang


var GLOBAL_STR = 'This is a render-time global.';


function _renderFeaturesUsage() {

  var sb = new soy.StringBuilder();

  var numExamples = 0;

  function appendExampleHeader(exampleName) {
    numExamples++;
    sb.append(soy.examples.features.exampleHeader(
        {exampleNum: numExamples, exampleName: exampleName}));
  }

  if (soy.examples.features.bidiGlobalDir() < 0) {
    document.body.setAttribute('dir', 'rtl');
  }

  // ---------------------------------------------------------------------------
  appendExampleHeader('demoComments');
  sb.append(soy.examples.features.demoComments());

  // ---------------------------------------------------------------------------
  appendExampleHeader('demoLineJoining');
  sb.append(soy.examples.features.demoLineJoining());

  // ---------------------------------------------------------------------------
  appendExampleHeader('demoRawTextCommands');
  sb.append(soy.examples.features.demoRawTextCommands());

  // ---------------------------------------------------------------------------
  appendExampleHeader('demoPrint');
  sb.append(soy.examples.features.demoPrint({boo: 'Boo!', two: 2}));

  // ---------------------------------------------------------------------------
  appendExampleHeader('demoPrintDirectives');
  sb.append(soy.examples.features.demoPrintDirectives(
      {longVarName: 'thisIsSomeRidiculouslyLongVariableName',
       elementId: 'my_element_id',
       cssClass: 'my_css_class'}));

  // ---------------------------------------------------------------------------
  appendExampleHeader('demoAutoescapeTrue');
  sb.append(soy.examples.features.demoAutoescapeTrue(
      {italicHtml: '<i>italic</i>'}));

  // ---------------------------------------------------------------------------
  appendExampleHeader('demoAutoescapeFalse');
  sb.append(soy.examples.features.demoAutoescapeFalse(
      {italicHtml: '<i>italic</i>'}));

  // ---------------------------------------------------------------------------
  appendExampleHeader('demoMsg');
  sb.append(soy.examples.features.demoMsg(
      {name: 'Ed', labsUrl: 'http://labs.google.com/'}));

  // ---------------------------------------------------------------------------
  appendExampleHeader('demoIf');
  sb.append(soy.examples.features.demoIf({pi: 3.14159}));
  sb.append(soy.examples.features.demoIf({pi: 2.71828}));
  sb.append(soy.examples.features.demoIf({pi: 1.61803}));

  // ---------------------------------------------------------------------------
  appendExampleHeader('demoSwitch');
  sb.append(soy.examples.features.demoSwitch({name: 'Fay'}));
  sb.append(soy.examples.features.demoSwitch({name: 'Go'}));
  sb.append(soy.examples.features.demoSwitch({name: 'Hal'}));
  sb.append(soy.examples.features.demoSwitch({name: 'Ivy'}));

  // ---------------------------------------------------------------------------
  appendExampleHeader('demoForeach');
  sb.append(soy.examples.features.demoForeach(
      {persons: [
           {name: 'Jen', numWaffles: 1},
           {name: 'Kai', numWaffles: 3},
           {name: 'Lex', numWaffles: 1},
           {name: 'Mel', numWaffles: 2}
      ]}));
  sb.append(soy.examples.features.demoForeach({persons: []}));

  // ---------------------------------------------------------------------------
  appendExampleHeader('demoFor');
  sb.append(soy.examples.features.demoFor({numLines: 3}));

  // ---------------------------------------------------------------------------
  appendExampleHeader('demoCallWithoutParam');
  sb.append(soy.examples.features.demoCallWithoutParam(
      {name: 'Neo', tripInfo: {name: 'Neo', destination: 'The Matrix'}}));

  // ---------------------------------------------------------------------------
  appendExampleHeader('demoCallWithParam');
  sb.append(soy.examples.features.demoCallWithParam(
      {name: 'Oz',
       companionName: 'Pip',
       destinations: ['Gillikin Country', 'Munchkin Country',
                      'Quadling Country', 'Winkie Country']}));

  // ---------------------------------------------------------------------------
  appendExampleHeader('demoCallWithParamBlock');
  sb.append(soy.examples.features.demoCallWithParamBlock({name: 'Quo'}));

  // ---------------------------------------------------------------------------
  appendExampleHeader('demoExpressions');
  sb.append(soy.examples.features.demoExpressions(
      {students: [
           {name: 'Rob', major: 'Physics', year: 1999},
           {name: 'Sha', major: 'Finance', year: 1980},
           {name: 'Tim', major: 'Engineering', year: 2005},
           {name: 'Uma', major: 'Biology', year: 1972}
       ],
       currentYear: 2008
      }));

  // ---------------------------------------------------------------------------
  appendExampleHeader('demoDoubleBraces');
  sb.append(soy.examples.features.demoDoubleBraces(
      {setName: 'prime numbers', setMembers: [2, 3, 5, 7, 11, 13]}));

  // ---------------------------------------------------------------------------
  appendExampleHeader('demoBidiSupport');
  sb.append(soy.examples.features.demoBidiSupport(
      {title: '2008: A BiDi Odyssey',
       author: 'John Doe, Esq.',
       year: 1973,
       keywords: ['Bi(Di)', '2008 (\u05E9\u05E0\u05D4)','2008 (year)']}));

  // ---------------------------------------------------------------------------
  appendExampleHeader('demoParamWithKindAttribute');
  sb.append(soy.examples.features.demoParamWithKindAttribute(
      {message: 'I <3 XSS',
       list: [
           'Thing One',
           'This & That',
           'Ceci n\'est pas un tag <script>.']}));

  // ---------------------------------------------------------------------------
  sb.append('<hr><b>The end</b>');

  return sb.toString();
}
