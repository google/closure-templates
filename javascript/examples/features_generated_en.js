// This file was automatically generated from features.soy.
// Please don't edit this file by hand.

if (typeof soy == 'undefined') { var soy = {}; }
if (typeof soy.examples == 'undefined') { soy.examples = {}; }
if (typeof soy.examples.features == 'undefined') { soy.examples.features = {}; }


soy.examples.features.demoComments = function(opt_data, opt_sb) {
  var output = opt_sb || new soy.StringBuilder();
  output.append('blah blah<br>http://www.google.com<br>');
  if (!opt_sb) return output.toString();
};


soy.examples.features.demoLineJoining = function(opt_data, opt_sb) {
  var output = opt_sb || new soy.StringBuilder();
  output.append('First second.<br><i>First</i>second.<br>Firstsecond.<br><i>First</i> second.<br>Firstsecond.<br>');
  if (!opt_sb) return output.toString();
};


soy.examples.features.demoRawTextCommands = function(opt_data, opt_sb) {
  var output = opt_sb || new soy.StringBuilder();
  output.append('<pre>Space       : AA BB<br>Empty string: AABB<br>New line    : AA\nBB<br>Carriage ret: AA\rBB<br>Tab         : AA\tBB<br>Left brace  : AA{BB<br>Right brace : AA}BB<br>Literal     : AA\tBB { CC\n  DD } EE {sp}{\\n}{rb} FF</pre>');
  if (!opt_sb) return output.toString();
};


soy.examples.features.demoPrint = function(opt_data, opt_sb) {
  var output = opt_sb || new soy.StringBuilder();
  output.append('Boo!<br>Boo!<br>', soy.$$escapeHtml(1 + 2), '<br>', soy.$$escapeHtml(opt_data.boo), '<br>', soy.$$escapeHtml(1 + opt_data.two), '<br>', soy.$$escapeHtml(GLOBAL_STR), '<br>These are compile-time globals: ', soy.$$escapeHtml(26), ', ', soy.$$escapeHtml(true), '.<br>');
  if (!opt_sb) return output.toString();
};


soy.examples.features.demoPrintDirectives = function(opt_data, opt_sb) {
  var output = opt_sb || new soy.StringBuilder();
  output.append('insertWordBreaks:<br><div style="width:150px; border:1px solid #00CC00">', soy.$$escapeHtml(opt_data.longVarName), '<br>', soy.$$insertWordBreaks(soy.$$escapeHtml(opt_data.longVarName), 5), '<br></div>id:<br><span id="', opt_data.elementId, '" class="', opt_data.cssClass, '" style="border:1px solid #000000">Hello</span>');
  if (!opt_sb) return output.toString();
};


soy.examples.features.demoAutoescapeTrue = function(opt_data, opt_sb) {
  var output = opt_sb || new soy.StringBuilder();
  output.append(soy.$$escapeHtml(opt_data.italicHtml), '<br>', opt_data.italicHtml, '<br>');
  if (!opt_sb) return output.toString();
};


soy.examples.features.demoAutoescapeFalse = function(opt_data, opt_sb) {
  var output = opt_sb || new soy.StringBuilder();
  output.append(opt_data.italicHtml, '<br>', soy.$$escapeHtml(opt_data.italicHtml), '<br>');
  if (!opt_sb) return output.toString();
};


soy.examples.features.demoMsg = function(opt_data, opt_sb) {
  var output = opt_sb || new soy.StringBuilder();
  output.append('Hello ', soy.$$escapeHtml(opt_data.name), '!<br>Click <a href="', soy.$$escapeHtml(opt_data.labsUrl), '">here</a> to access Labs.<br>Archive<br>Archive<br>');
  if (!opt_sb) return output.toString();
};


soy.examples.features.demoIf = function(opt_data, opt_sb) {
  var output = opt_sb || new soy.StringBuilder();
  output.append((Math.round(opt_data.pi * 100) / 100 == 3.14) ? soy.$$escapeHtml(opt_data.pi) + ' is a good approximation of pi.' : (Math.round(opt_data.pi) == 3) ? soy.$$escapeHtml(opt_data.pi) + ' is a bad approximation of pi.' : soy.$$escapeHtml(opt_data.pi) + ' is nowhere near the value of pi.', '<br>');
  if (!opt_sb) return output.toString();
};


soy.examples.features.demoSwitch = function(opt_data, opt_sb) {
  var output = opt_sb || new soy.StringBuilder();
  output.append('Dear ', soy.$$escapeHtml(opt_data.name), ', &nbsp;');
  switch (opt_data.name) {
    case 'Go':
      output.append('You\'ve been bad this year.');
      break;
    case 'Fay':
    case 'Ivy':
      output.append('You\'ve been good this year.');
      break;
    default:
      output.append('You don\'t really believe in me, do you?');
  }
  output.append('&nbsp; --Santa<br>');
  if (!opt_sb) return output.toString();
};


soy.examples.features.demoForeach = function(opt_data, opt_sb) {
  var output = opt_sb || new soy.StringBuilder();
  var personList130 = opt_data.persons;
  var personListLen130 = personList130.length;
  if (personListLen130 > 0) {
    for (var personIndex130 = 0; personIndex130 < personListLen130; personIndex130++) {
      var personData130 = personList130[personIndex130];
      output.append((personIndex130 == 0) ? 'First,' : (personIndex130 == personListLen130 - 1) ? 'Finally,' : 'Then', ' ', (personData130.numWaffles == 1) ? soy.$$escapeHtml(personData130.name) + ' ate 1 waffle.' : soy.$$escapeHtml(personData130.name) + ' ate ' + soy.$$escapeHtml(personData130.numWaffles) + ' waffles.', '<br>');
    }
  } else {
    output.append('Nobody here ate any waffles.<br>');
  }
  if (!opt_sb) return output.toString();
};


soy.examples.features.demoFor = function(opt_data, opt_sb) {
  var output = opt_sb || new soy.StringBuilder();
  var iLimit153 = opt_data.numLines;
  for (var i153 = 0; i153 < iLimit153; i153++) {
    output.append('Line ', soy.$$escapeHtml(i153 + 1), ' of ', soy.$$escapeHtml(opt_data.numLines), '.<br>');
  }
  for (var i159 = 2; i159 < 10; i159 += 2) {
    output.append(soy.$$escapeHtml(i159), '... ');
  }
  output.append('Who do we appreciate?<br>');
  if (!opt_sb) return output.toString();
};


soy.examples.features.demoCallWithoutParam = function(opt_data, opt_sb) {
  var output = opt_sb || new soy.StringBuilder();
  soy.examples.simple.helloWorld(null, output);
  output.append('<br>');
  soy.examples.features.tripReport_(null, output);
  output.append('<br>');
  soy.examples.features.tripReport_(opt_data, output);
  output.append('<br>');
  soy.examples.features.tripReport_(opt_data.tripInfo, output);
  output.append('<br>');
  if (!opt_sb) return output.toString();
};


soy.examples.features.demoCallWithParam = function(opt_data, opt_sb) {
  var output = opt_sb || new soy.StringBuilder();
  var destinationList173 = opt_data.destinations;
  var destinationListLen173 = destinationList173.length;
  for (var destinationIndex173 = 0; destinationIndex173 < destinationListLen173; destinationIndex173++) {
    var destinationData173 = destinationList173[destinationIndex173];
    soy.examples.features.tripReport_(soy.$$augmentData(opt_data, {destination: destinationData173}), output);
    output.append('<br>');
    if (destinationIndex173 % 2 == 0) {
      soy.examples.features.tripReport_({name: opt_data.companionName, destination: destinationData173}, output);
      output.append('<br>');
    }
  }
  if (!opt_sb) return output.toString();
};


soy.examples.features.demoCallWithParamBlock = function(opt_data, opt_sb) {
  var output = opt_sb || new soy.StringBuilder();
  var param187 = new soy.StringBuilder();
  switch (Math.floor(Math.random() * 3)) {
    case 0:
      param187.append('Boston');
      break;
    case 1:
      param187.append('Singapore');
      break;
    case 2:
      param187.append('Zurich');
      break;
  }
  soy.examples.features.tripReport_({name: opt_data.name, destination: param187.toString()}, output);
  output.append('<br>');
  if (!opt_sb) return output.toString();
};


soy.examples.features.tripReport_ = function(opt_data, opt_sb) {
  var output = opt_sb || new soy.StringBuilder();
  output.append((! (opt_data != null && opt_data.name)) ? 'A trip was taken.' : (! opt_data.destination) ? soy.$$escapeHtml(opt_data.name) + ' took a trip.' : soy.$$escapeHtml(opt_data.name) + ' took a trip to ' + soy.$$escapeHtml(opt_data.destination) + '.');
  if (!opt_sb) return output.toString();
};


soy.examples.features.demoExpressions = function(opt_data, opt_sb) {
  var output = opt_sb || new soy.StringBuilder();
  output.append('First student\'s major: ', soy.$$escapeHtml(opt_data.students[0].major), '<br>Last student\'s year: ', soy.$$escapeHtml(opt_data.students[opt_data.students.length - 1].year), '<br>Random student\'s major: ', soy.$$escapeHtml(opt_data.students[Math.floor(Math.random() * opt_data.students.length)].major), '<br>');
  var studentList219 = opt_data.students;
  var studentListLen219 = studentList219.length;
  for (var studentIndex219 = 0; studentIndex219 < studentListLen219; studentIndex219++) {
    var studentData219 = studentList219[studentIndex219];
    output.append(soy.$$escapeHtml(studentData219.name), ':', (studentIndex219 == 0) ? ' First.' : (studentIndex219 == studentListLen219 - 1) ? ' Last.' : (studentIndex219 == Math.ceil(opt_data.students.length / 2) - 1) ? ' Middle.' : '', (studentIndex219 % 2 == 1) ? ' Even.' : '', ' ', soy.$$escapeHtml(studentData219.major), '.', (studentData219.major == 'Physics' || studentData219.major == 'Biology') ? ' Scientist.' : '', (opt_data.currentYear - studentData219.year < 10) ? ' Young.' : '', ' ', soy.$$escapeHtml(studentData219.year < 2000 ? Math.round((studentData219.year - 1905) / 10) * 10 + 's' : '00s'), '. ', (studentData219.year < 2000) ? soy.$$escapeHtml(Math.round((studentData219.year - 1905) / 10) * 10) : '00', 's.<br>');
  }
  if (!opt_sb) return output.toString();
};


soy.examples.features.demoDoubleBraces = function(opt_data, opt_sb) {
  var output = opt_sb || new soy.StringBuilder();
  output.append('The set of ', soy.$$escapeHtml(opt_data.setName), ' is {');
  soy.examples.features.buildCommaSeparatedList_({items: opt_data.setMembers}, output);
  output.append(', ...}.');
  if (!opt_sb) return output.toString();
};


soy.examples.features.buildCommaSeparatedList_ = function(opt_data, opt_sb) {
  var output = opt_sb || new soy.StringBuilder();
  var itemList260 = opt_data.items;
  var itemListLen260 = itemList260.length;
  for (var itemIndex260 = 0; itemIndex260 < itemListLen260; itemIndex260++) {
    var itemData260 = itemList260[itemIndex260];
    output.append((! (itemIndex260 == 0)) ? ', ' : '', soy.$$escapeHtml(itemData260));
  }
  if (!opt_sb) return output.toString();
};


soy.examples.features.demoBidiSupport = function(opt_data, opt_sb) {
  var output = opt_sb || new soy.StringBuilder();
  output.append('<div id="title1" style="font-variant:small-caps" ', soy.$$escapeHtml(soy.$$bidiDirAttr(1, opt_data.title)), '>', soy.$$escapeHtml(opt_data.title), '</div><div id="title2" style="font-variant:small-caps">', soy.$$bidiSpanWrap(1, soy.$$escapeHtml(opt_data.title)), '</div>by ', soy.$$bidiSpanWrap(1, soy.$$escapeHtml(opt_data.author)), ' (', soy.$$escapeHtml(opt_data.year), ')<div id="choose_a_keyword">Your favorite keyword: <select>');
  var keywordList286 = opt_data.keywords;
  var keywordListLen286 = keywordList286.length;
  for (var keywordIndex286 = 0; keywordIndex286 < keywordListLen286; keywordIndex286++) {
    var keywordData286 = keywordList286[keywordIndex286];
    output.append('<option value="', soy.$$escapeHtml(keywordData286), '">', soy.$$bidiUnicodeWrap(1, soy.$$escapeHtml(keywordData286)), '</option>');
  }
  output.append('</select></div><a href="#" style="float:right">Help</a><br>');
  if (!opt_sb) return output.toString();
};


soy.examples.features.bidiGlobalDir = function(opt_data, opt_sb) {
  var output = opt_sb || new soy.StringBuilder();
  output.append(soy.$$escapeHtml(1));
  if (!opt_sb) return output.toString();
};


soy.examples.features.exampleHeader = function(opt_data, opt_sb) {
  var output = opt_sb || new soy.StringBuilder();
  output.append('<hr><b>', soy.$$escapeHtml(opt_data.exampleNum), '. ', soy.$$escapeHtml(opt_data.exampleName), '</b><br>');
  if (!opt_sb) return output.toString();
};
