# Copyright 2015 Google Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Functions for bidirectional support.

Soy functions and directives for setting bidirectional attributes and tags.
All other directives and functions should go in sanitize.py or directives.py.
"""

# Emulate Python 3 style unicode string literals.
from __future__ import unicode_literals

__author__ = 'dcphillips@google.com (David Phillips)'

import re
from . import sanitize

# To allow the rest of the file to assume Python 3 strings, we will assign str
# to unicode for Python 2. This will error in 3 and be ignored.
try:
  str = unicode
except NameError:
  pass


#############
# Constants #
#############


# Regular expression to check if a string contains any numerals. Used to
# differentiate between completely neutral strings and those containing
# numbers, which are weakly LTR.
_HAS_NUMERALS_RE = re.compile(r'\d')


# Simplified regular expression for am HTML tag (opening or closing) or an HTML
# escape - the things we want to skip over in order to ignore their ltr
# characters.
_HTML_SKIP_RE = re.compile('<[^>]*>|&[^;]+;')


# Regular expression to check if a string looks like something that must
# always be LTR even in RTL text, e.g. a URL. When estimating the
# directionality of text containing these, we treat these as weakly LTR,
# like numbers.
_IS_REQUIRED_LTR_RE = re.compile(r'^http[s]?://.*')


# A practical pattern to identify strong LTR/RTL character. This pattern is not
# theoretically correct according to unicode standard. It is simplified for
# performance and small code size.
# Sourced from JS bidi implementation:
# TODO(dcphillips): Review this and see if it's worth it to have a more
# comprehensive implementation for Python or to generate both versions.
_LTR_CHARS = (
    'A-Za-z\u00C0-\u00D6\u00D8-\u00F6\u00F8-\u02B8\u0300-\u0590\u0800-\u1FFF' +
    '\u200E\u2C00-\uFB1C\uFE00-\uFE6F\uFEFD-\uFFFF')
_RTL_CHARS = '\u0591-\u07FF\u200F\uFB1D-\uFDFF\uFE70-\uFEFC'


# Regular expression to check for LTR characters.
_LTR_CHARS_RE = re.compile('[' + _LTR_CHARS + ']')


# Regular expression to check if a piece of text is of RTL directionality on
# first character with strong directionality.
_RTL_DIR_CHECK_RE = re.compile('^[^' + _LTR_CHARS + ']*[' + _RTL_CHARS + ']')


# Regular expressions to check if a piece of text is of LTR/RTL directionality
# on the last character with strong direcitonality.
_LTR_EXIT_DIR_CHECK_RE = re.compile(
    '[' + _LTR_CHARS + '][^' + _RTL_CHARS + ']*$')
_RTL_EXIT_DIR_CHECK_RE = re.compile(
    '[' + _RTL_CHARS + '][^' + _LTR_CHARS + ']*$')


# This constant controls threshold of rtl directionality.
_RTL_ESTIMATION_THRESHOLD = 0.40


# Unicode format characters to specify the directionality of text.
class FORMAT:
  # Unicode "Left-To-Right Embedding" (LRE) character.
  LRE = '\u202A'
  # Unicode "Right-To-Left Embedding" (RLE) character.
  RLE = '\u202B'
  # Unicode "Pop Directional Formatting" (PDF) character.
  PDF = '\u202C'
  # Unicode "Left-To-Right Mark" (LRM) character.
  LRM = '\u200E'
  # Unicode "Right-To-Left Mark" (RLM) character.
  RLM = '\u200F'


#############
# Functions #
#############


def dir_attr(global_dir, text, is_html=False):
  """Returns directionality attribute.

  Returns 'dir="ltr"' or 'dir="rtl"', depending on text's estimated
  directionality, if it is not the same as bidiGlobalDir. Otherwise, returns
  the empty string. If is_html, makes sure to ignore the LTR nature of the
  mark-up and escapes in text, making the logic suitable for HTML and
  HTML-escaped text. If text has a global direction, this is used instead of
  estimating the directionality.

  Args:
    global_dir: The global directionality context: 1 if ltr, -1 if rtl, 0
        if unknown.
    text: The content whose directionality is to be estimated.
    is_html: Whether text is HTML.

  Returns:
    'dir="rtl"' for RTL text in non-RTL context; 'dir="ltr"' for LTR text in
    non-LTR context; else, the empty string.
  """
  formatter = _get_bidi_formatter(global_dir)
  content_dir = sanitize.get_content_dir(text)
  if content_dir is None:
    is_html = is_html or _is_content_html(text)
    content_dir = _estimate_direction(text, is_html)

  approval = sanitize.IActuallyUnderstandSoyTypeSafetyAndHaveSecurityApproval(
      'Directionality attributes are by nature Sanitized Html Attributes.')
  return sanitize.SanitizedHtmlAttribute(formatter.attr(content_dir),
                                         approval=approval)


def mark_after(global_dir, text, is_html=False):
  """Returns a unicode character to mark directionality.

  Returns a Unicode BiDi mark matching globalDir (LRM or RLM) if the
  directionality or the exit directionality of text are opposite to globalDir.
  Otherwise returns the empty string.
  If is_html, makes sure to ignore the LTR nature of the mark-up and escapes
  in text, making the logic suitable for HTML and HTML-escaped text.
  If text has a global direction, this is used instead of
  estimating the directionality.

  Args:
    global_dir: The global directionality context: 1 if ltr, -1 if rtl, 0 if
        unknown.
    text: The content whose directionality is to be estimated.
    is_html: Whether text is HTML/HTML-escaped. Default: false.

  Returns:
    A Unicode bidi mark matching globalDir, or the empty string when text's
    overall and exit directionalities both match globalDir, or globalDir is 0
    (unknown).
  """
  formatter = _get_bidi_formatter(global_dir)
  is_html = is_html or _is_content_html(text)
  content_dir = sanitize.get_content_dir(text)
  if content_dir is None:
    content_dir = _estimate_direction(text, is_html)

  return formatter.mark_after(content_dir, text, is_html)


def span_wrap(global_dir, text):
  """Returns text wrapped in a span tag with directionality.

  The output is wrapped with <span dir="ltr|rtl"> according to its
  directionality - but only if that is neither neutral nor the same as the
  global context. Otherwise, returns text unchanged.
  Always treats text as HTML/HTML-escaped, i.e. ignores mark-up and escapes
  when estimating text's directionality.
  If text has a global direction, this is used instead of estimating the
  directionality.

  Args:
    global_dir: The global directionality context: 1 if ltr, -1 if rtl, 0 if
        unknown.
    text: The string to be wrapped. Can be other types, but the value will be
        coerced to a string.

  Returns:
    The input text wrapped in a span tag with directionality.
  """
  formatter = _get_bidi_formatter(global_dir)
  content_dir = sanitize.get_content_dir(text)
  if content_dir is None:
    content_dir = _estimate_direction(text, True)

  # Like other directives whose Java class implements SanitizedContentOperator,
  # |bidiSpanWrap is called after the escaping (if any) has already been done,
  # and thus there is no need for it to produce actual SanitizedContent.
  return formatter.span_wrap(content_dir, text)


def text_dir(text, is_html=False):
  """Estimate the directionality of the text.

  Estimate the overall directionality of the text. If is_html, make sure to
  ignore the LTR nature of the mark-up and escapes in text, making the logic
  suitable for HTML and HTML-escaped text.

  Args:
    text: The content whose directionality is being estimated.
    is_html: Whether the text is HTML/HTML-escaped.

  Returns:
    1 if the text is LTR, -1 if it is RTL, 0 otherwise.
  """
  content_dir = sanitize.get_content_dir(text)
  if content_dir is not None:
    return content_dir

  is_html = is_html or _is_content_html(text)
  return _estimate_direction(text, is_html)


def unicode_wrap(global_dir, text):
  """Returns text wrapped with Unicode bidi formatting characters.

  Returns text wrapped in Unicode BiDi formatting characters according to its
  directionality, i.e. either LRE or RLE at the beginning and PDF at the end -
  but only if text's directionality is neither neutral nor the same as the
  global context. Otherwise, returns text unchanged.
  Only treats SanitizedHtml as HTML/HTML-escaped, i.e. ignores mark-up and
  escapes when estimating text's directionality.
  If text has a global direction, this is used instead of estimating the
  directionality.

  Args:
    global_dir: The global directionality context: 1 if ltr, -1 if rtl, 0 if
        unknown.
    text: The string to be wrapped. Can be other types, but the value will be
        coerced to a string.

  Returns:
    The input string maybe wrapped with Unicode bidi formatting characters.
  """
  formatter = _get_bidi_formatter(global_dir)
  is_html = _is_content_html(text)
  content_dir = sanitize.get_content_dir(text)
  if content_dir is None:
    content_dir = _estimate_direction(text, is_html)

  wrapped_text = formatter.unicode_wrap(
      content_dir, text, is_html)

  # Bidi-wrapping a value converts it to the context directionality. Since it
  # does not cost us anything, we will indicate this known direction in the
  # output SanitizedContent, even though the intended consumer of that
  # information - a bidi wrapping directive - has already been run.
  wrapped_text_dir = formatter.direction

  # Unicode-wrapping UnsanitizedText gives UnsanitizedText.
  # Unicode-wrapping safe HTML or JS string data gives valid, safe HTML or JS
  # string data.
  approval = sanitize.IActuallyUnderstandSoyTypeSafetyAndHaveSecurityApproval(
      'Persisting existing sanitizations.')
  if is_html:
    return sanitize.SanitizedHtml(wrapped_text, wrapped_text_dir,
                                  approval=approval)
  if sanitize.is_content_kind(text, sanitize.CONTENT_KIND.JS_STR_CHARS):
    return sanitize.SanitizedJsStrChars(wrapped_text, wrapped_text_dir,
                                        approval=approval)

  # Unicode-wrapping does not conform to the syntax of the other types of
  # content. For lack of anything better to do, we we do not declare a content
  # kind at all by falling through to the non-SanitizedContent case below.

  # The input was not SanitizedContent, so our output isn't SanitizedContent
  # either.
  return wrapped_text


def bidi_end_edge(d):
  if d < 0:
    return 'left'
  return 'right'


def bidi_start_edge(d):
  if d < 0:
    return 'right'
  return 'left'


def bidi_mark(d):
  if d < 0:
    return '\u200F'
  return '\u200E'

###########
# Classes #
###########


class BidiFormatter(object):
  instances = {}

  def __init__(self, global_dir):
    if global_dir > 0:
      self.direction = sanitize.DIR.LTR
    elif global_dir < 0:
      self.direction = sanitize.DIR.RTL
    else:
      self.direction = sanitize.DIR.NEUTRAL

  def attr(self, content_dir):
    """Returns directionality attribute if needed.

    Returns 'dir="ltr"' or 'dir="rtl"', depending on the given directionality,
    if it is not the same as the context directionality. Otherwise, returns the
    empty string.

    Args:
      content_dir: The directionality of the content.

    Returns:
      'dir="rtl"' for RTL text in non-RTL context; 'dir="ltr"' for LTR text in
      non-LTR context; else, the empty string.
    """
    if not content_dir or content_dir == self.direction:
      return ''
    elif content_dir < 0:
      return 'dir="rtl"'
    else:
      return 'dir="ltr"'

  def mark_after(self, content_dir, text, is_html):
    """Returns a unicode directionality mark character if changing direction.

    Args:
      content_dir: The directionality of the content.
      text: The text which is ending.
      is_html: Whether the input text is html content.
    Returns:
      A Unicode LRM or RLM character or an empty string if no change.
    """
    text = str(text)
    is_ltr = self.direction == sanitize.DIR.LTR
    is_rtl = self.direction == sanitize.DIR.RTL

    # Only mark a directionality if there's a mismatch in directionality.
    if (content_dir * self.direction < 0 or
        (is_ltr and _ends_with_rtl(text, is_html)) or
        (is_rtl and _ends_with_ltr(text, is_html))):
      return FORMAT.LRM if is_ltr else FORMAT.RLM

    return ''

  def span_wrap(self, content_dir, text):
    """Wrap the text in a span with a dir attribute if an opposing direction.

    Args:
      content_dir: The directionality of the content.
      text: The text to wrap.

    Returns:
      A wrapped text if necessary, or the original text.
    """
    text = str(text)

    # Whether to add the "dir" attribute.
    use_dir = (content_dir != sanitize.DIR.NEUTRAL and
               content_dir != self.direction)
    if use_dir:
      # Wrap is needed.
      result = '<span %s>%s</span>' % (self.attr(content_dir), text)
    else:
      result = text

    return result + self.mark_after(content_dir, text, True)

  def unicode_wrap(self, content_dir, text, is_html):
    """Wrap the text with unicode direction characters if an opposing direction.

    Args:
      content_dir: The directionality of the content.
      text: The text to wrap.
      is_html: Whether the input text is html.
    Returns:
      Wrapped text if necessary, or the original text.
    """
    text = str(text)
    result = []
    if content_dir != sanitize.DIR.NEUTRAL and content_dir != self.direction:
      if content_dir == sanitize.DIR.RTL:
        result.append(FORMAT.RLE)
      else:
        result.append(FORMAT.LRE)
      result.append(text)
      result.append(FORMAT.PDF)
    else:
      result.append(text)

    result.append(self.mark_after(content_dir, text, is_html))
    return ''.join(result)


#############################
# Private Utility Functions #
#############################


def _ends_with_ltr(value, is_html):
  """Checks if the last directional character of text is RLT.

  Args:
    value: The string to check.
    is_html: Whether the string is HTML/HTML-escaped.

  Returns:
    Boolean whether the LTR exit directionality was detected.
  """
  return _LTR_EXIT_DIR_CHECK_RE.search(_strip_html_if_needed(value, is_html))


def _ends_with_rtl(value, is_html):
  """Checks if the last directional character of text is RTL.

  Args:
    value: The string to check.
    is_html: Whether the string is HTML/HTML-escaped.

  Returns:
    Boolean whether the RTL exit directionality was detected.
  """
  return _RTL_EXIT_DIR_CHECK_RE.search(_strip_html_if_needed(value, is_html))


def _estimate_direction(value, is_html):
  """Estimate the directionality based on the content.

  To estimate the directionality, we tokenize the input and look for regex
  matches which strongly or weakly indicate a direction. If the input is html,
  tags are stripped first.

  Args:
    value: The input string.
    is_html: Whether the input is an html string.
  Returns:
    The estimated directionality sanitize.DIR enum.
  """
  value = str(value)

  rtl_count = 0
  total_count = 0
  has_weakly_ltr = False
  tokens = _strip_html_if_needed(value, is_html).split()
  for token in tokens:
    if _RTL_DIR_CHECK_RE.search(token):
      rtl_count += 1
      total_count += 1
    elif _IS_REQUIRED_LTR_RE.search(token):
      has_weakly_ltr = True
    elif _LTR_CHARS_RE.search(token):
      total_count += 1
    elif _HAS_NUMERALS_RE.search(token):
      has_weakly_ltr = True

  if total_count == 0 and has_weakly_ltr:
    return sanitize.DIR.LTR
  elif total_count == 0:
    return sanitize.DIR.NEUTRAL
  elif float(rtl_count) / total_count > _RTL_ESTIMATION_THRESHOLD:
    return sanitize.DIR.RTL
  else:
    return sanitize.DIR.LTR


def _get_bidi_formatter(global_dir):
  """Return the appropriate BidiFormatter singleton instance.

  Args:
    global_dir: The global directionality.
  Returns:
    The BidiFormatter instance.
  """
  formatter = BidiFormatter.instances.get(global_dir)
  if not formatter:
    formatter = BidiFormatter.instances[global_dir] = BidiFormatter(global_dir)
  return formatter


def _is_content_html(text):
  return sanitize.is_content_kind(text, sanitize.CONTENT_KIND.HTML)


def _strip_html_if_needed(value, is_html=False):
  return _HTML_SKIP_RE.sub('', value) if is_html else value
