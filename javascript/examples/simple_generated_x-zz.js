// This file was automatically generated from simple.soy.
// Please don't edit this file by hand.

if (typeof soy == 'undefined') { var soy = {}; }
if (typeof soy.examples == 'undefined') { soy.examples = {}; }
if (typeof soy.examples.simple == 'undefined') { soy.examples.simple = {}; }


soy.examples.simple.helloWorld = function(opt_data, opt_ignored) {
  return 'Zhello zworld!';
};


soy.examples.simple.helloName = function(opt_data, opt_ignored) {
  opt_data = opt_data || {};
  return (opt_data.name) ? 'Zhello ' + soy.$$escapeHtml(opt_data.name) + '!' : soy.examples.simple.helloWorld(null);
};


soy.examples.simple.helloNames = function(opt_data, opt_ignored) {
  var output = '';
  var nameList16 = opt_data.names;
  var nameListLen16 = nameList16.length;
  if (nameListLen16 > 0) {
    for (var nameIndex16 = 0; nameIndex16 < nameListLen16; nameIndex16++) {
      var nameData16 = nameList16[nameIndex16];
      output += soy.examples.simple.helloName({name: nameData16}) + ((! (nameIndex16 == nameListLen16 - 1)) ? '<br>' : '');
    }
  } else {
    output += soy.examples.simple.helloWorld(null);
  }
  return output;
};
