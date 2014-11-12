// This file was automatically generated from features.soy.
// Please don't edit this file by hand.

/**
 * @fileoverview Templates in namespace soy.examples.features.
 * @public
 */

if (typeof soy == 'undefined') { var soy = {}; }
if (typeof soy.examples == 'undefined') { soy.examples = {}; }
if (typeof soy.examples.features == 'undefined') { soy.examples.features = {}; }


soy.examples.features.demoComments = function(opt_data, opt_ignored) {
  return 'blah blah<br>http://www.google.com<br>';
};
if (goog.DEBUG) {
  soy.examples.features.demoComments.soyTemplateName = 'soy.examples.features.demoComments';
}


soy.examples.features.demoLineJoining = function(opt_data, opt_ignored) {
  return 'First second.<br><i>First</i>second.<br>Firstsecond.<br><i>First</i> second.<br>Firstsecond.<br>';
};
if (goog.DEBUG) {
  soy.examples.features.demoLineJoining.soyTemplateName = 'soy.examples.features.demoLineJoining';
}


soy.examples.features.demoRawTextCommands = function(opt_data, opt_ignored) {
  return '<pre>Space       : AA BB<br>Empty string: AABB<br>New line    : AA\nBB<br>Carriage ret: AA\rBB<br>Tab         : AA\tBB<br>Left brace  : AA{BB<br>Right brace : AA}BB<br>Literal     : AA\tBB { CC\n  DD } EE {sp}{\\n}{rb} FF</pre>';
};
if (goog.DEBUG) {
  soy.examples.features.demoRawTextCommands.soyTemplateName = 'soy.examples.features.demoRawTextCommands';
}


soy.examples.features.demoPrint = function(opt_data, opt_ignored) {
  return 'Boo!<br>Boo!<br>3<br>' + soy.$$escapeHtml(opt_data.boo) + '<br>' + soy.$$escapeHtml(1 + opt_data.two) + '<br>26, true.<br>';
};
if (goog.DEBUG) {
  soy.examples.features.demoPrint.soyTemplateName = 'soy.examples.features.demoPrint';
}


soy.examples.features.demoPrintDirectives = function(opt_data, opt_ignored) {
  return 'insertWordBreaks:<br><div style="width:150px; border:1px solid #00CC00">' + soy.$$escapeHtml(opt_data.longVarName) + '<br>' + soy.$$insertWordBreaks(soy.$$escapeHtml(opt_data.longVarName), 5) + '<br></div>id:<br><span id="' + opt_data.elementId + '" class="' + opt_data.cssClass + '" style="border:1px solid #000000">Hello</span>';
};
if (goog.DEBUG) {
  soy.examples.features.demoPrintDirectives.soyTemplateName = 'soy.examples.features.demoPrintDirectives';
}


soy.examples.features.demoAutoescapeTrue = function(opt_data, opt_ignored) {
  return soy.$$escapeHtml(opt_data.italicHtml) + '<br>' + soy.$$filterNoAutoescape(opt_data.italicHtml) + '<br>';
};
if (goog.DEBUG) {
  soy.examples.features.demoAutoescapeTrue.soyTemplateName = 'soy.examples.features.demoAutoescapeTrue';
}


soy.examples.features.demoAutoescapeFalse = function(opt_data, opt_ignored) {
  return opt_data.italicHtml + '<br>' + soy.$$escapeHtml(opt_data.italicHtml) + '<br>';
};
if (goog.DEBUG) {
  soy.examples.features.demoAutoescapeFalse.soyTemplateName = 'soy.examples.features.demoAutoescapeFalse';
}


soy.examples.features.demoMsg = function(opt_data, opt_ignored) {
  return 'Hello ' + soy.$$escapeHtml(opt_data.name) + '!<br>Click <a href="' + soy.$$escapeHtml(opt_data.labsUrl) + '">here</a> to access Labs.<br>Archive<br>Archive<br>';
};
if (goog.DEBUG) {
  soy.examples.features.demoMsg.soyTemplateName = 'soy.examples.features.demoMsg';
}


soy.examples.features.demoIf = function(opt_data, opt_ignored) {
  return ((Math.round(opt_data.pi * 100) / 100 == 3.14) ? soy.$$escapeHtml(opt_data.pi) + ' is a good approximation of pi.' : (Math.round(opt_data.pi) == 3) ? soy.$$escapeHtml(opt_data.pi) + ' is a bad approximation of pi.' : soy.$$escapeHtml(opt_data.pi) + ' is nowhere near the value of pi.') + '<br>';
};
if (goog.DEBUG) {
  soy.examples.features.demoIf.soyTemplateName = 'soy.examples.features.demoIf';
}


soy.examples.features.demoSwitch = function(opt_data, opt_ignored) {
  var output = 'Dear ' + soy.$$escapeHtml(opt_data.name) + ', &nbsp;';
  switch (opt_data.name) {
    case 'Go':
      output += 'You\'ve been bad this year.';
      break;
    case 'Fay':
    case 'Ivy':
      output += 'You\'ve been good this year.';
      break;
    default:
      output += 'You don\'t really believe in me, do you?';
  }
  output += '&nbsp; --Santa<br>';
  return output;
};
if (goog.DEBUG) {
  soy.examples.features.demoSwitch.soyTemplateName = 'soy.examples.features.demoSwitch';
}


soy.examples.features.demoForeach = function(opt_data, opt_ignored) {
  var output = '';
  var personList144 = opt_data.persons;
  var personListLen144 = personList144.length;
  if (personListLen144 > 0) {
    for (var personIndex144 = 0; personIndex144 < personListLen144; personIndex144++) {
      var personData144 = personList144[personIndex144];
      output += ((personIndex144 == 0) ? 'First,' : (personIndex144 == personListLen144 - 1) ? 'Finally,' : 'Then') + ' ' + ((personData144.numWaffles == 1) ? soy.$$escapeHtml(personData144.name) + ' ate 1 waffle.' : soy.$$escapeHtml(personData144.name) + ' ate ' + soy.$$escapeHtml(personData144.numWaffles) + ' waffles.') + '<br>';
    }
  } else {
    output += 'Nobody here ate any waffles.<br>';
  }
  return output;
};
if (goog.DEBUG) {
  soy.examples.features.demoForeach.soyTemplateName = 'soy.examples.features.demoForeach';
}


soy.examples.features.demoFor = function(opt_data, opt_ignored) {
  var output = '';
  var iLimit167 = opt_data.numLines;
  for (var i167 = 0; i167 < iLimit167; i167++) {
    output += 'Line ' + soy.$$escapeHtml(i167 + 1) + ' of ' + soy.$$escapeHtml(opt_data.numLines) + '.<br>';
  }
  for (var i173 = 2; i173 < 10; i173 += 2) {
    output += soy.$$escapeHtml(i173) + '... ';
  }
  output += 'Who do we appreciate?<br>';
  return output;
};
if (goog.DEBUG) {
  soy.examples.features.demoFor.soyTemplateName = 'soy.examples.features.demoFor';
}


soy.examples.features.demoCallWithoutParam = function(opt_data, opt_ignored) {
  return soy.examples.simple.helloWorld(null) + '<br>' + soy.examples.features.tripReport_(null) + '<br>' + soy.examples.features.tripReport_(opt_data) + '<br>' + soy.examples.features.tripReport_(opt_data.tripInfo) + '<br>';
};
if (goog.DEBUG) {
  soy.examples.features.demoCallWithoutParam.soyTemplateName = 'soy.examples.features.demoCallWithoutParam';
}


soy.examples.features.demoCallWithParam = function(opt_data, opt_ignored) {
  var output = '';
  var destinationList187 = opt_data.destinations;
  var destinationListLen187 = destinationList187.length;
  for (var destinationIndex187 = 0; destinationIndex187 < destinationListLen187; destinationIndex187++) {
    var destinationData187 = destinationList187[destinationIndex187];
    output += soy.examples.features.tripReport_(soy.$$augmentMap(opt_data, {destination: destinationData187})) + '<br>' + ((destinationIndex187 % 2 == 0) ? soy.examples.features.tripReport_({name: opt_data.companionName, destination: destinationData187}) + '<br>' : '');
  }
  return output;
};
if (goog.DEBUG) {
  soy.examples.features.demoCallWithParam.soyTemplateName = 'soy.examples.features.demoCallWithParam';
}


soy.examples.features.demoCallWithParamBlock = function(opt_data, opt_ignored) {
  var param200 = '';
  switch (Math.floor(Math.random() * 3)) {
    case 0:
      param200 += 'Boston';
      break;
    case 1:
      param200 += 'Singapore';
      break;
    case 2:
      param200 += 'Zurich';
      break;
  }
  var output = '' + soy.examples.features.tripReport_({name: opt_data.name, destination: param200});
  output += '<br>';
  return output;
};
if (goog.DEBUG) {
  soy.examples.features.demoCallWithParamBlock.soyTemplateName = 'soy.examples.features.demoCallWithParamBlock';
}


soy.examples.features.tripReport_ = function(opt_data, opt_ignored) {
  opt_data = opt_data || {};
  return '' + ((! opt_data.name) ? 'A trip was taken.' : (! opt_data.destination) ? soy.$$escapeHtml(opt_data.name) + ' took a trip.' : soy.$$escapeHtml(opt_data.name) + ' took a trip to ' + soy.$$escapeHtml(opt_data.destination) + '.');
};
if (goog.DEBUG) {
  soy.examples.features.tripReport_.soyTemplateName = 'soy.examples.features.tripReport_';
}


soy.examples.features.demoParamWithKindAttribute = function(opt_data, opt_ignored) {
  var output = '<div>';
  var param237 = '';
  var iList238 = opt_data.list;
  var iListLen238 = iList238.length;
  for (var iIndex238 = 0; iIndex238 < iListLen238; iIndex238++) {
    var iData238 = iList238[iIndex238];
    param237 += '<li>' + soy.$$escapeHtml(iData238) + '</li>';
  }
  output += soy.examples.features.demoParamWithKindAttributeCallee_({message: soydata.VERY_UNSAFE.$$ordainSanitizedHtmlForInternalBlocks('<b>' + soy.$$escapeHtml(opt_data.message) + '</b>'), listItems: soydata.VERY_UNSAFE.$$ordainSanitizedHtmlForInternalBlocks(param237)});
  output += '</div>';
  return output;
};
if (goog.DEBUG) {
  soy.examples.features.demoParamWithKindAttribute.soyTemplateName = 'soy.examples.features.demoParamWithKindAttribute';
}


soy.examples.features.demoParamWithKindAttributeCallee_ = function(opt_data, opt_ignored) {
  return '<div>' + soy.$$escapeHtml(opt_data.message) + '</div><ol>' + soy.$$escapeHtml(opt_data.listItems) + '</ol>';
};
if (goog.DEBUG) {
  soy.examples.features.demoParamWithKindAttributeCallee_.soyTemplateName = 'soy.examples.features.demoParamWithKindAttributeCallee_';
}


soy.examples.features.demoExpressions = function(opt_data, opt_ignored) {
  var output = 'First student\'s major: ' + soy.$$escapeHtml(opt_data.students[0].major) + '<br>Last student\'s year: ' + soy.$$escapeHtml(opt_data.students[opt_data.students.length - 1].year) + '<br>Random student\'s major: ' + soy.$$escapeHtml(opt_data.students[Math.floor(Math.random() * opt_data.students.length)].major) + '<br>';
  var studentList259 = opt_data.students;
  var studentListLen259 = studentList259.length;
  for (var studentIndex259 = 0; studentIndex259 < studentListLen259; studentIndex259++) {
    var studentData259 = studentList259[studentIndex259];
    output += soy.$$escapeHtml(studentData259.name) + ':' + ((studentIndex259 == 0) ? ' First.' : (studentIndex259 == studentListLen259 - 1) ? ' Last.' : (studentIndex259 == Math.ceil(opt_data.students.length / 2) - 1) ? ' Middle.' : '') + ((studentIndex259 % 2 == 1) ? ' Even.' : '') + ' ' + soy.$$escapeHtml(studentData259.major) + '.' + ((studentData259.major == 'Physics' || studentData259.major == 'Biology') ? ' Scientist.' : '') + ((opt_data.currentYear - studentData259.year < 10) ? ' Young.' : '') + ' ' + soy.$$escapeHtml(studentData259.year < 2000 ? Math.round((studentData259.year - 1905) / 10) * 10 + 's' : '00s') + '. ' + ((studentData259.year < 2000) ? soy.$$escapeHtml(Math.round((studentData259.year - 1905) / 10) * 10) : '00') + 's.<br>';
  }
  return output;
};
if (goog.DEBUG) {
  soy.examples.features.demoExpressions.soyTemplateName = 'soy.examples.features.demoExpressions';
}


soy.examples.features.demoDoubleBraces = function(opt_data, opt_ignored) {
  return 'The set of ' + soy.$$escapeHtml(opt_data.setName) + ' is {' + soy.examples.features.buildCommaSeparatedList_({items: opt_data.setMembers}) + ', ...}.';
};
if (goog.DEBUG) {
  soy.examples.features.demoDoubleBraces.soyTemplateName = 'soy.examples.features.demoDoubleBraces';
}


soy.examples.features.buildCommaSeparatedList_ = function(opt_data, opt_ignored) {
  var output = '';
  var itemList303 = opt_data.items;
  var itemListLen303 = itemList303.length;
  for (var itemIndex303 = 0; itemIndex303 < itemListLen303; itemIndex303++) {
    var itemData303 = itemList303[itemIndex303];
    output += ((! (itemIndex303 == 0)) ? ', ' : '') + soy.$$escapeHtml(itemData303);
  }
  return output;
};
if (goog.DEBUG) {
  soy.examples.features.buildCommaSeparatedList_.soyTemplateName = 'soy.examples.features.buildCommaSeparatedList_';
}


soy.examples.features.demoBidiSupport = function(opt_data, opt_ignored) {
  var output = '<div id="title1" style="font-variant:small-caps" ' + soy.$$escapeHtml(soy.$$bidiDirAttr(1, opt_data.title)) + '>' + soy.$$escapeHtml(opt_data.title) + '</div><div id="title2" style="font-variant:small-caps">' + soy.$$bidiSpanWrap(1, soy.$$escapeHtml(opt_data.title)) + '</div>by ' + soy.$$bidiSpanWrap(1, soy.$$escapeHtml(opt_data.author)) + ' (' + soy.$$escapeHtml(opt_data.year) + ')<div id="choose_a_keyword">Your favorite keyword: <select>';
  var keywordList333 = opt_data.keywords;
  var keywordListLen333 = keywordList333.length;
  for (var keywordIndex333 = 0; keywordIndex333 < keywordListLen333; keywordIndex333++) {
    var keywordData333 = keywordList333[keywordIndex333];
    output += '<option value="' + soy.$$escapeHtml(keywordData333) + '">' + soy.$$bidiUnicodeWrap(1, soy.$$escapeHtml(keywordData333)) + '</option>';
  }
  output += '</select></div><a href="#" style="float:right">Help</a><br>';
  return output;
};
if (goog.DEBUG) {
  soy.examples.features.demoBidiSupport.soyTemplateName = 'soy.examples.features.demoBidiSupport';
}


soy.examples.features.bidiGlobalDir = function(opt_data, opt_ignored) {
  return '' + soy.$$escapeHtml(1);
};
if (goog.DEBUG) {
  soy.examples.features.bidiGlobalDir.soyTemplateName = 'soy.examples.features.bidiGlobalDir';
}


soy.examples.features.exampleHeader = function(opt_data, opt_ignored) {
  return '<hr><b>' + soy.$$escapeHtml(opt_data.exampleNum) + '. ' + soy.$$escapeHtml(opt_data.exampleName) + '</b><br>';
};
if (goog.DEBUG) {
  soy.examples.features.exampleHeader.soyTemplateName = 'soy.examples.features.exampleHeader';
}
