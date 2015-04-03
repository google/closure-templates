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
  return 'Zhello ' + soy.$$escapeHtml(opt_data.name) + '!<br>Zclick <a href="' + soy.$$escapeHtml(opt_data.labsUrl) + '">zhere</a> zto zaccess Zlabs.<br>Zarchive<br>Zarchive<br>';
};
if (goog.DEBUG) {
  soy.examples.features.demoMsg.soyTemplateName = 'soy.examples.features.demoMsg';
}


soy.examples.features.demoIf = function(opt_data, opt_ignored) {
  return ((Math.round(opt_data.pi * 100) / 100 == 3.14) ? soy.$$escapeHtml(opt_data.pi) + ' zis za zgood zapproximation zof zpi.' : (Math.round(opt_data.pi) == 3) ? soy.$$escapeHtml(opt_data.pi) + ' zis za zbad zapproximation zof zpi.' : soy.$$escapeHtml(opt_data.pi) + ' zis znowhere znear zthe zvalue zof zpi.') + '<br>';
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
  var personList163 = opt_data.persons;
  var personListLen163 = personList163.length;
  if (personListLen163 > 0) {
    for (var personIndex163 = 0; personIndex163 < personListLen163; personIndex163++) {
      var personData163 = personList163[personIndex163];
      output += ((personIndex163 == 0) ? 'First,' : (personIndex163 == personListLen163 - 1) ? 'Finally,' : 'Then') + ' ' + ((personData163.numWaffles == 1) ? soy.$$escapeHtml(personData163.name) + ' ate 1 waffle.' : soy.$$escapeHtml(personData163.name) + ' ate ' + soy.$$escapeHtml(personData163.numWaffles) + ' waffles.') + '<br>';
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
  return soy.examples.features.tripReport_(null) + '<br>' + soy.examples.features.tripReport_(opt_data) + '<br>' + soy.examples.features.tripReport_(opt_data.tripInfo) + '<br>';
};
if (goog.DEBUG) {
  soy.examples.features.demoCallWithoutParam.soyTemplateName = 'soy.examples.features.demoCallWithoutParam';
}


soy.examples.features.demoCallOtherFile = function(opt_data, opt_ignored) {
  return soydata.VERY_UNSAFE.ordainSanitizedHtml(soy.examples.simple.helloWorld(null) + '<br>');
};
if (goog.DEBUG) {
  soy.examples.features.demoCallOtherFile.soyTemplateName = 'soy.examples.features.demoCallOtherFile';
}


soy.examples.features.demoCallWithParam = function(opt_data, opt_ignored) {
  var output = '';
  var destinationList197 = opt_data.destinations;
  var destinationListLen197 = destinationList197.length;
  for (var destinationIndex197 = 0; destinationIndex197 < destinationListLen197; destinationIndex197++) {
    var destinationData197 = destinationList197[destinationIndex197];
    output += soy.examples.features.tripReport_(soy.$$augmentMap(opt_data, {destination: destinationData197})) + '<br>' + ((destinationIndex197 % 2 == 0) ? soy.examples.features.tripReport_({name: opt_data.companionName, destination: destinationData197}) + '<br>' : '');
  }
  return output;
};
if (goog.DEBUG) {
  soy.examples.features.demoCallWithParam.soyTemplateName = 'soy.examples.features.demoCallWithParam';
}


soy.examples.features.demoCallWithParamBlock = function(opt_data, opt_ignored) {
  var param201 = '';
  switch (Math.floor(Math.random() * 3)) {
    case 0:
      param201 += 'Boston';
      break;
    case 1:
      param201 += 'Singapore';
      break;
    case 2:
      param201 += 'Zurich';
      break;
  }
  var output = '' + soy.examples.features.tripReport_({name: opt_data.name, destination: param201});
  output += '<br>';
  return output;
};
if (goog.DEBUG) {
  soy.examples.features.demoCallWithParamBlock.soyTemplateName = 'soy.examples.features.demoCallWithParamBlock';
}


soy.examples.features.tripReport_ = function(opt_data, opt_ignored) {
  opt_data = opt_data || {};
  return '' + ((! opt_data.name) ? 'Za ztrip zwas ztaken.' : (! opt_data.destination) ? soy.$$escapeHtml(opt_data.name) + ' ztook za ztrip.' : soy.$$escapeHtml(opt_data.name) + ' ztook za ztrip zto ' + soy.$$escapeHtml(opt_data.destination) + '.');
};
if (goog.DEBUG) {
  soy.examples.features.tripReport_.soyTemplateName = 'soy.examples.features.tripReport_';
}


soy.examples.features.demoParamWithKindAttribute = function(opt_data, opt_ignored) {
  var output = '<div>';
  var param238 = '';
  var iList242 = opt_data.list;
  var iListLen242 = iList242.length;
  for (var iIndex242 = 0; iIndex242 < iListLen242; iIndex242++) {
    var iData242 = iList242[iIndex242];
    param238 += '<li>' + soy.$$escapeHtml(iData242) + '</li>';
  }
  output += soy.examples.features.demoParamWithKindAttributeCallee_({message: soydata.VERY_UNSAFE.$$ordainSanitizedHtmlForInternalBlocks('<b>' + soy.$$escapeHtml(opt_data.message) + '</b>'), listItems: soydata.VERY_UNSAFE.$$ordainSanitizedHtmlForInternalBlocks(param238)});
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
  var studentList290 = opt_data.students;
  var studentListLen290 = studentList290.length;
  for (var studentIndex290 = 0; studentIndex290 < studentListLen290; studentIndex290++) {
    var studentData290 = studentList290[studentIndex290];
    output += soy.$$escapeHtml(studentData290.name) + ':' + ((studentIndex290 == 0) ? ' First.' : (studentIndex290 == studentListLen290 - 1) ? ' Last.' : (studentIndex290 == Math.ceil(opt_data.students.length / 2) - 1) ? ' Middle.' : '') + ((studentIndex290 % 2 == 1) ? ' Even.' : '') + ' ' + soy.$$escapeHtml(studentData290.major) + '.' + ((studentData290.major == 'Physics' || studentData290.major == 'Biology') ? ' Scientist.' : '') + ((opt_data.currentYear - studentData290.year < 10) ? ' Young.' : '') + ' ' + soy.$$escapeHtml(studentData290.year < 2000 ? Math.round((studentData290.year - 1905) / 10) * 10 + 's' : '00s') + '. ' + ((studentData290.year < 2000) ? soy.$$escapeHtml(Math.round((studentData290.year - 1905) / 10) * 10) : '00') + 's.<br>';
  }
  return output;
};
if (goog.DEBUG) {
  soy.examples.features.demoExpressions.soyTemplateName = 'soy.examples.features.demoExpressions';
}


soy.examples.features.demoDoubleBraces = function(opt_data, opt_ignored) {
  return 'Zthe zset zof ' + soy.$$escapeHtml(opt_data.setName) + ' zis {' + soy.examples.features.buildCommaSeparatedList_({items: opt_data.setMembers}) + ', ...}.';
};
if (goog.DEBUG) {
  soy.examples.features.demoDoubleBraces.soyTemplateName = 'soy.examples.features.demoDoubleBraces';
}


soy.examples.features.buildCommaSeparatedList_ = function(opt_data, opt_ignored) {
  var output = '';
  var itemList308 = opt_data.items;
  var itemListLen308 = itemList308.length;
  for (var itemIndex308 = 0; itemIndex308 < itemListLen308; itemIndex308++) {
    var itemData308 = itemList308[itemIndex308];
    output += ((! (itemIndex308 == 0)) ? ', ' : '') + soy.$$escapeHtml(itemData308);
  }
  return output;
};
if (goog.DEBUG) {
  soy.examples.features.buildCommaSeparatedList_.soyTemplateName = 'soy.examples.features.buildCommaSeparatedList_';
}


soy.examples.features.demoBidiSupport = function(opt_data, opt_ignored) {
  var output = '<div id="title1" style="font-variant:small-caps" ' + soy.$$escapeHtml(soy.$$bidiDirAttr(1, opt_data.title)) + '>' + soy.$$escapeHtml(opt_data.title) + '</div><div id="title2" style="font-variant:small-caps">' + soy.$$bidiSpanWrap(1, soy.$$escapeHtml(opt_data.title)) + '</div>zby ' + soy.$$bidiSpanWrap(1, soy.$$escapeHtml(opt_data.author)) + ' (' + soy.$$escapeHtml(opt_data.year) + ')<div id="choose_a_keyword">Zyour zfavorite zkeyword: <select>';
  var keywordList340 = opt_data.keywords;
  var keywordListLen340 = keywordList340.length;
  for (var keywordIndex340 = 0; keywordIndex340 < keywordListLen340; keywordIndex340++) {
    var keywordData340 = keywordList340[keywordIndex340];
    output += '<option value="' + soy.$$escapeHtml(keywordData340) + '">' + soy.$$bidiUnicodeWrap(1, soy.$$escapeHtml(keywordData340)) + '</option>';
  }
  output += '</select></div><a href="#" style="float:right">Zhelp</a><br>';
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
