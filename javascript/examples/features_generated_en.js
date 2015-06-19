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
  return soydata.VERY_UNSAFE.ordainSanitizedHtml('blah blah<br>http://www.google.com<br>');
};
if (goog.DEBUG) {
  soy.examples.features.demoComments.soyTemplateName = 'soy.examples.features.demoComments';
}


soy.examples.features.demoLineJoining = function(opt_data, opt_ignored) {
  return soydata.VERY_UNSAFE.ordainSanitizedHtml('First second.<br><i>First</i>second.<br>Firstsecond.<br><i>First</i> second.<br>Firstsecond.<br>');
};
if (goog.DEBUG) {
  soy.examples.features.demoLineJoining.soyTemplateName = 'soy.examples.features.demoLineJoining';
}


soy.examples.features.demoRawTextCommands = function(opt_data, opt_ignored) {
  return soydata.markUnsanitizedText('<pre>Space       : AA BB<br>Empty string: AABB<br>New line    : AA\nBB<br>Carriage ret: AA\rBB<br>Tab         : AA\tBB<br>Left brace  : AA{BB<br>Right brace : AA}BB<br>Literal     : AA\tBB { CC\n  DD } EE {sp}{\\n}{rb} FF</pre>');
};
if (goog.DEBUG) {
  soy.examples.features.demoRawTextCommands.soyTemplateName = 'soy.examples.features.demoRawTextCommands';
}


soy.examples.features.demoPrint = function(opt_data, opt_ignored) {
  return soydata.VERY_UNSAFE.ordainSanitizedHtml('Boo!<br>Boo!<br>3<br>' + soy.$$escapeHtml(opt_data.boo) + '<br>' + soy.$$escapeHtml(1 + opt_data.two) + '<br>26, true.<br>');
};
if (goog.DEBUG) {
  soy.examples.features.demoPrint.soyTemplateName = 'soy.examples.features.demoPrint';
}


soy.examples.features.demoAutoescapeTrue = function(opt_data, opt_ignored) {
  return soydata.VERY_UNSAFE.ordainSanitizedHtml(soy.$$escapeHtml(opt_data.italicHtml) + '<br>');
};
if (goog.DEBUG) {
  soy.examples.features.demoAutoescapeTrue.soyTemplateName = 'soy.examples.features.demoAutoescapeTrue';
}


soy.examples.features.demoMsg = function(opt_data, opt_ignored) {
  return soydata.VERY_UNSAFE.ordainSanitizedHtml('Hello ' + soy.$$escapeHtml(opt_data.name) + '!<br>Click <a href="' + soy.$$escapeHtmlAttribute(soy.$$filterNormalizeUri(opt_data.labsUrl)) + '">here</a> to access Labs.<br>Archive<br>Archive<br>');
};
if (goog.DEBUG) {
  soy.examples.features.demoMsg.soyTemplateName = 'soy.examples.features.demoMsg';
}


soy.examples.features.demoIf = function(opt_data, opt_ignored) {
  return soydata.VERY_UNSAFE.ordainSanitizedHtml(((Math.round(opt_data.pi * 100) / 100 == 3.14) ? soy.$$escapeHtml(opt_data.pi) + ' is a good approximation of pi.' : (Math.round(opt_data.pi) == 3) ? soy.$$escapeHtml(opt_data.pi) + ' is a bad approximation of pi.' : soy.$$escapeHtml(opt_data.pi) + ' is nowhere near the value of pi.') + '<br>');
};
if (goog.DEBUG) {
  soy.examples.features.demoIf.soyTemplateName = 'soy.examples.features.demoIf';
}


soy.examples.features.demoSwitch = function(opt_data, opt_ignored) {
  var $$temp;
  var output = 'Dear ' + soy.$$escapeHtml(opt_data.name) + ', &nbsp;';
  switch ((goog.isObject($$temp = opt_data.name)) ? $$temp.toString() : $$temp) {
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
  return soydata.VERY_UNSAFE.ordainSanitizedHtml(output);
};
if (goog.DEBUG) {
  soy.examples.features.demoSwitch.soyTemplateName = 'soy.examples.features.demoSwitch';
}


soy.examples.features.demoForeach = function(opt_data, opt_ignored) {
  var output = '';
  var personList141 = opt_data.persons;
  var personListLen141 = personList141.length;
  if (personListLen141 > 0) {
    for (var personIndex141 = 0; personIndex141 < personListLen141; personIndex141++) {
      var personData141 = personList141[personIndex141];
      output += ((personIndex141 == 0) ? 'First,' : (personIndex141 == personListLen141 - 1) ? 'Finally,' : 'Then') + ' ' + ((personData141.numWaffles == 1) ? soy.$$escapeHtml(personData141.name) + ' ate 1 waffle.' : soy.$$escapeHtml(personData141.name) + ' ate ' + soy.$$escapeHtml(personData141.numWaffles) + ' waffles.') + '<br>';
    }
  } else {
    output += 'Nobody here ate any waffles.<br>';
  }
  return soydata.VERY_UNSAFE.ordainSanitizedHtml(output);
};
if (goog.DEBUG) {
  soy.examples.features.demoForeach.soyTemplateName = 'soy.examples.features.demoForeach';
}


soy.examples.features.demoFor = function(opt_data, opt_ignored) {
  var output = '';
  var iLimit145 = opt_data.numLines;
  for (var i145 = 0; i145 < iLimit145; i145++) {
    output += 'Line ' + soy.$$escapeHtml(i145 + 1) + ' of ' + soy.$$escapeHtml(opt_data.numLines) + '.<br>';
  }
  for (var i151 = 2; i151 < 10; i151 += 2) {
    output += soy.$$escapeHtml(i151) + '... ';
  }
  output += 'Who do we appreciate?<br>';
  return soydata.VERY_UNSAFE.ordainSanitizedHtml(output);
};
if (goog.DEBUG) {
  soy.examples.features.demoFor.soyTemplateName = 'soy.examples.features.demoFor';
}


soy.examples.features.demoCallWithoutParam = function(opt_data, opt_ignored) {
  return soydata.VERY_UNSAFE.ordainSanitizedHtml(soy.$$escapeHtml(soy.examples.features.tripReport_(null)) + '<br>' + soy.$$escapeHtml(soy.examples.features.tripReport_(opt_data)) + '<br>' + soy.$$escapeHtml(soy.examples.features.tripReport_(opt_data.tripInfo)) + '<br>');
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
  var destinationList175 = opt_data.destinations;
  var destinationListLen175 = destinationList175.length;
  for (var destinationIndex175 = 0; destinationIndex175 < destinationListLen175; destinationIndex175++) {
    var destinationData175 = destinationList175[destinationIndex175];
    output += soy.$$escapeHtml(soy.examples.features.tripReport_(soy.$$augmentMap(opt_data, {destination: destinationData175}))) + '<br>' + ((destinationIndex175 % 2 == 0) ? soy.$$escapeHtml(soy.examples.features.tripReport_({name: opt_data.companionName, destination: destinationData175})) + '<br>' : '');
  }
  return soydata.VERY_UNSAFE.ordainSanitizedHtml(output);
};
if (goog.DEBUG) {
  soy.examples.features.demoCallWithParam.soyTemplateName = 'soy.examples.features.demoCallWithParam';
}


soy.examples.features.demoCallWithParamBlock = function(opt_data, opt_ignored) {
  var $$temp;
  var param179 = '';
  switch ((goog.isObject($$temp = Math.floor(Math.random() * 3))) ? $$temp.toString() : $$temp) {
    case 0:
      param179 += 'Boston';
      break;
    case 1:
      param179 += 'Singapore';
      break;
    case 2:
      param179 += 'Zurich';
      break;
  }
  var output = '' + soy.$$escapeHtml(soy.examples.features.tripReport_({name: opt_data.name, destination: soydata.$$markUnsanitizedTextForInternalBlocks(param179)}));
  output += '<br>';
  return soydata.VERY_UNSAFE.ordainSanitizedHtml(output);
};
if (goog.DEBUG) {
  soy.examples.features.demoCallWithParamBlock.soyTemplateName = 'soy.examples.features.demoCallWithParamBlock';
}


soy.examples.features.tripReport_ = function(opt_data, opt_ignored) {
  opt_data = opt_data || {};
  return soydata.markUnsanitizedText((! opt_data.name) ? 'A trip was taken.' : (! opt_data.destination) ? '' + opt_data.name + ' took a trip.' : '' + opt_data.name + ' took a trip to ' + ('' + opt_data.destination) + '.');
};
if (goog.DEBUG) {
  soy.examples.features.tripReport_.soyTemplateName = 'soy.examples.features.tripReport_';
}


soy.examples.features.demoParamWithKindAttribute = function(opt_data, opt_ignored) {
  var output = '<div>';
  var param216 = '';
  var iList220 = opt_data.list;
  var iListLen220 = iList220.length;
  for (var iIndex220 = 0; iIndex220 < iListLen220; iIndex220++) {
    var iData220 = iList220[iIndex220];
    param216 += '<li>' + soy.$$escapeHtml(iData220) + '</li>';
  }
  output += soy.examples.features.demoParamWithKindAttributeCallee_({message: soydata.VERY_UNSAFE.$$ordainSanitizedHtmlForInternalBlocks('<b>' + soy.$$escapeHtml(opt_data.message) + '</b>'), listItems: soydata.VERY_UNSAFE.$$ordainSanitizedHtmlForInternalBlocks(param216)});
  output += '</div>';
  return soydata.VERY_UNSAFE.ordainSanitizedHtml(output);
};
if (goog.DEBUG) {
  soy.examples.features.demoParamWithKindAttribute.soyTemplateName = 'soy.examples.features.demoParamWithKindAttribute';
}


soy.examples.features.demoParamWithKindAttributeCallee_ = function(opt_data, opt_ignored) {
  return soydata.VERY_UNSAFE.ordainSanitizedHtml('<div>' + soy.$$escapeHtml(opt_data.message) + '</div><ol>' + soy.$$escapeHtml(opt_data.listItems) + '</ol>');
};
if (goog.DEBUG) {
  soy.examples.features.demoParamWithKindAttributeCallee_.soyTemplateName = 'soy.examples.features.demoParamWithKindAttributeCallee_';
}


soy.examples.features.demoExpressions = function(opt_data, opt_ignored) {
  var output = 'First student\'s major: ' + soy.$$escapeHtml(opt_data.students[0].major) + '<br>Last student\'s year: ' + soy.$$escapeHtml(opt_data.students[opt_data.students.length - 1].year) + '<br>Random student\'s major: ' + soy.$$escapeHtml(opt_data.students[Math.floor(Math.random() * opt_data.students.length)].major) + '<br>';
  var studentList268 = opt_data.students;
  var studentListLen268 = studentList268.length;
  for (var studentIndex268 = 0; studentIndex268 < studentListLen268; studentIndex268++) {
    var studentData268 = studentList268[studentIndex268];
    output += soy.$$escapeHtml(studentData268.name) + ':' + ((studentIndex268 == 0) ? ' First.' : (studentIndex268 == studentListLen268 - 1) ? ' Last.' : (studentIndex268 == Math.ceil(opt_data.students.length / 2) - 1) ? ' Middle.' : '') + ((studentIndex268 % 2 == 1) ? ' Even.' : '') + ' ' + soy.$$escapeHtml(studentData268.major) + '.' + ((studentData268.major == 'Physics' || studentData268.major == 'Biology') ? ' Scientist.' : '') + ((opt_data.currentYear - studentData268.year < 10) ? ' Young.' : '') + ' ' + soy.$$escapeHtml(studentData268.year < 2000 ? Math.round((studentData268.year - 1905) / 10) * 10 + 's' : '00s') + '. ' + ((studentData268.year < 2000) ? soy.$$escapeHtml(Math.round((studentData268.year - 1905) / 10) * 10) : '00') + 's.<br>';
  }
  return soydata.VERY_UNSAFE.ordainSanitizedHtml(output);
};
if (goog.DEBUG) {
  soy.examples.features.demoExpressions.soyTemplateName = 'soy.examples.features.demoExpressions';
}


soy.examples.features.demoDoubleBraces = function(opt_data, opt_ignored) {
  return soydata.markUnsanitizedText('The set of ' + ('' + opt_data.setName) + ' is {' + soy.examples.features.buildCommaSeparatedList_({items: opt_data.setMembers}) + ', ...}.');
};
if (goog.DEBUG) {
  soy.examples.features.demoDoubleBraces.soyTemplateName = 'soy.examples.features.demoDoubleBraces';
}


soy.examples.features.buildCommaSeparatedList_ = function(opt_data, opt_ignored) {
  var output = '';
  var itemList286 = opt_data.items;
  var itemListLen286 = itemList286.length;
  for (var itemIndex286 = 0; itemIndex286 < itemListLen286; itemIndex286++) {
    var itemData286 = itemList286[itemIndex286];
    output += ((! (itemIndex286 == 0)) ? ', ' : '') + ('' + itemData286);
  }
  return soydata.markUnsanitizedText(output);
};
if (goog.DEBUG) {
  soy.examples.features.buildCommaSeparatedList_.soyTemplateName = 'soy.examples.features.buildCommaSeparatedList_';
}


soy.examples.features.demoBidiSupport = function(opt_data, opt_ignored) {
  var output = '<div id="title1" style="font-variant:small-caps" ' + soy.$$filterHtmlAttributes(soy.$$bidiDirAttr(1, opt_data.title)) + '>' + soy.$$escapeHtml(opt_data.title) + '</div><div id="title2" style="font-variant:small-caps">' + soy.$$bidiSpanWrap(1, soy.$$escapeHtml(opt_data.title)) + '</div>by ' + soy.$$bidiSpanWrap(1, soy.$$escapeHtml(opt_data.author)) + ' (' + soy.$$escapeHtml(opt_data.year) + ')<div id="choose_a_keyword">Your favorite keyword: <select>';
  var keywordList318 = opt_data.keywords;
  var keywordListLen318 = keywordList318.length;
  for (var keywordIndex318 = 0; keywordIndex318 < keywordListLen318; keywordIndex318++) {
    var keywordData318 = keywordList318[keywordIndex318];
    output += '<option value="' + soy.$$escapeHtmlAttribute(keywordData318) + '">' + soy.$$escapeHtml(soy.$$bidiUnicodeWrap(1, keywordData318)) + '</option>';
  }
  output += '</select></div><a href="#" style="float:' + soy.$$escapeHtmlAttribute(soy.$$filterCssValue('right')) + '">Help</a><br>';
  return soydata.VERY_UNSAFE.ordainSanitizedHtml(output);
};
if (goog.DEBUG) {
  soy.examples.features.demoBidiSupport.soyTemplateName = 'soy.examples.features.demoBidiSupport';
}


soy.examples.features.bidiGlobalDir = function(opt_data, opt_ignored) {
  return soydata.markUnsanitizedText('' + 1);
};
if (goog.DEBUG) {
  soy.examples.features.bidiGlobalDir.soyTemplateName = 'soy.examples.features.bidiGlobalDir';
}


soy.examples.features.exampleHeader = function(opt_data, opt_ignored) {
  return soydata.VERY_UNSAFE.ordainSanitizedHtml('<hr><b>' + soy.$$escapeHtml(opt_data.exampleNum) + '. ' + soy.$$escapeHtml(opt_data.exampleName) + '</b><br>');
};
if (goog.DEBUG) {
  soy.examples.features.exampleHeader.soyTemplateName = 'soy.examples.features.exampleHeader';
}
