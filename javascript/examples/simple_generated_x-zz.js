// This file was automatically generated from simple.soy.
// Please don't edit this file by hand.

if (typeof soy == 'undefined') { var soy = {}; }
if (typeof soy.examples == 'undefined') { soy.examples = {}; }
if (typeof soy.examples.simple == 'undefined') { soy.examples.simple = {}; }


soy.examples.simple.helloWorld = function(opt_data, opt_sb) {
  var output = opt_sb || new soy.StringBuilder();
  output.append('Zhello zworld!');
  if (!opt_sb) return output.toString();
};


soy.examples.simple.helloName = function(opt_data, opt_sb) {
  var output = opt_sb || new soy.StringBuilder();
  if (opt_data != null && opt_data.name) {
    output.append('Zhello ', soy.$$escapeHtml(opt_data.name), '!');
  } else {
    soy.examples.simple.helloWorld(null, output);
  }
  if (!opt_sb) return output.toString();
};


soy.examples.simple.helloNames = function(opt_data, opt_sb) {
  var output = opt_sb || new soy.StringBuilder();
  var nameList15 = opt_data.names;
  var nameListLen15 = nameList15.length;
  if (nameListLen15 > 0) {
    for (var nameIndex15 = 0; nameIndex15 < nameListLen15; nameIndex15++) {
      var nameData15 = nameList15[nameIndex15];
      soy.examples.simple.helloName({name: nameData15}, output);
      output.append((! (nameIndex15 == nameListLen15 - 1)) ? '<br>' : '');
    }
  } else {
    soy.examples.simple.helloWorld(null, output);
  }
  if (!opt_sb) return output.toString();
};
