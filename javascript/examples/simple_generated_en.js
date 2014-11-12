// This file was automatically generated from simple.soy.
// Please don't edit this file by hand.

/**
 * @fileoverview Templates in namespace soy.examples.simple.
 * @public
 */

if (typeof soy == 'undefined') { var soy = {}; }
if (typeof soy.examples == 'undefined') { soy.examples = {}; }
if (typeof soy.examples.simple == 'undefined') { soy.examples.simple = {}; }


soy.examples.simple.helloWorld = function(opt_data, opt_ignored) {
  return 'Hello world!';
};
if (goog.DEBUG) {
  soy.examples.simple.helloWorld.soyTemplateName = 'soy.examples.simple.helloWorld';
}


soy.examples.simple.helloName = function(opt_data, opt_ignored) {
  opt_data = opt_data || {};
  return '' + ((opt_data.name) ? 'Hello ' + soy.$$escapeHtml(opt_data.name) + '!' : soy.examples.simple.helloWorld(null));
};
if (goog.DEBUG) {
  soy.examples.simple.helloName.soyTemplateName = 'soy.examples.simple.helloName';
}


soy.examples.simple.helloNames = function(opt_data, opt_ignored) {
  var output = '';
  var nameList18 = opt_data.names;
  var nameListLen18 = nameList18.length;
  if (nameListLen18 > 0) {
    for (var nameIndex18 = 0; nameIndex18 < nameListLen18; nameIndex18++) {
      var nameData18 = nameList18[nameIndex18];
      output += soy.examples.simple.helloName({name: nameData18}) + ((! (nameIndex18 == nameListLen18 - 1)) ? '<br>' : '');
    }
  } else {
    output += soy.examples.simple.helloWorld(null);
  }
  return output;
};
if (goog.DEBUG) {
  soy.examples.simple.helloNames.soyTemplateName = 'soy.examples.simple.helloNames';
}
