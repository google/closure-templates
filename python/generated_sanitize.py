# START GENERATED CODE FOR ESCAPERS.
from __future__ import unicode_literals

import re
import urllib

try:
  str = unicode
except NameError:
  pass


def escape_uri_helper(v):
  return urllib.quote(str(v), '')

_ESCAPE_MAP_FOR_ESCAPE_HTML__AND__NORMALIZE_HTML__AND__ESCAPE_HTML_NOSPACE__AND__NORMALIZE_HTML_NOSPACE = {
  '\x00': '&#0;',
  '\x09': '&#9;',
  '\x0a': '&#10;',
  '\x0b': '&#11;',
  '\x0c': '&#12;',
  '\x0d': '&#13;',
  ' ': '&#32;',
  '\"': '&quot;',
  '&': '&amp;',
  '\'': '&#39;',
  '-': '&#45;',
  '/': '&#47;',
  '<': '&lt;',
  '=': '&#61;',
  '>': '&gt;',
  '`': '&#96;',
  '\x85': '&#133;',
  '\xa0': '&#160;',
  '\u2028': '&#8232;',
  '\u2029': '&#8233;'
}

def _REPLACER_FOR_ESCAPE_HTML__AND__NORMALIZE_HTML__AND__ESCAPE_HTML_NOSPACE__AND__NORMALIZE_HTML_NOSPACE(match):
  ch = match.group(0)
  return _ESCAPE_MAP_FOR_ESCAPE_HTML__AND__NORMALIZE_HTML__AND__ESCAPE_HTML_NOSPACE__AND__NORMALIZE_HTML_NOSPACE[ch]

_ESCAPE_MAP_FOR_ESCAPE_JS_STRING__AND__ESCAPE_JS_REGEX = {
  '\x00': '\\x00',
  '\x08': '\\x08',
  '\x09': '\\t',
  '\x0a': '\\n',
  '\x0b': '\\x0b',
  '\x0c': '\\f',
  '\x0d': '\\r',
  '\"': '\\x22',
  '$': '\\x24',
  '&': '\\x26',
  '\'': '\\x27',
  '(': '\\x28',
  ')': '\\x29',
  '*': '\\x2a',
  '+': '\\x2b',
  ',': '\\x2c',
  '-': '\\x2d',
  '.': '\\x2e',
  '/': '\\/',
  ':': '\\x3a',
  '<': '\\x3c',
  '=': '\\x3d',
  '>': '\\x3e',
  '?': '\\x3f',
  '[': '\\x5b',
  '\\': '\\\\',
  ']': '\\x5d',
  '^': '\\x5e',
  '{': '\\x7b',
  '|': '\\x7c',
  '}': '\\x7d',
  '\x85': '\\x85',
  '\u2028': '\\u2028',
  '\u2029': '\\u2029'
}

def _REPLACER_FOR_ESCAPE_JS_STRING__AND__ESCAPE_JS_REGEX(match):
  ch = match.group(0)
  return _ESCAPE_MAP_FOR_ESCAPE_JS_STRING__AND__ESCAPE_JS_REGEX[ch]

_ESCAPE_MAP_FOR_ESCAPE_CSS_STRING = {
  '\x00': '\\0 ',
  '\x08': '\\8 ',
  '\x09': '\\9 ',
  '\x0a': '\\a ',
  '\x0b': '\\b ',
  '\x0c': '\\c ',
  '\x0d': '\\d ',
  '\"': '\\22 ',
  '&': '\\26 ',
  '\'': '\\27 ',
  '(': '\\28 ',
  ')': '\\29 ',
  '*': '\\2a ',
  '/': '\\2f ',
  ':': '\\3a ',
  ';': '\\3b ',
  '<': '\\3c ',
  '=': '\\3d ',
  '>': '\\3e ',
  '@': '\\40 ',
  '\\': '\\5c ',
  '{': '\\7b ',
  '}': '\\7d ',
  '\x85': '\\85 ',
  '\xa0': '\\a0 ',
  '\u2028': '\\2028 ',
  '\u2029': '\\2029 '
}

def _REPLACER_FOR_ESCAPE_CSS_STRING(match):
  ch = match.group(0)
  return _ESCAPE_MAP_FOR_ESCAPE_CSS_STRING[ch]

_ESCAPE_MAP_FOR_NORMALIZE_URI__AND__FILTER_NORMALIZE_URI__AND__FILTER_NORMALIZE_MEDIA_URI = {
  '\x00': '%00',
  '\x01': '%01',
  '\x02': '%02',
  '\x03': '%03',
  '\x04': '%04',
  '\x05': '%05',
  '\x06': '%06',
  '\x07': '%07',
  '\x08': '%08',
  '\x09': '%09',
  '\x0a': '%0A',
  '\x0b': '%0B',
  '\x0c': '%0C',
  '\x0d': '%0D',
  '\x0e': '%0E',
  '\x0f': '%0F',
  '\x10': '%10',
  '\x11': '%11',
  '\x12': '%12',
  '\x13': '%13',
  '\x14': '%14',
  '\x15': '%15',
  '\x16': '%16',
  '\x17': '%17',
  '\x18': '%18',
  '\x19': '%19',
  '\x1a': '%1A',
  '\x1b': '%1B',
  '\x1c': '%1C',
  '\x1d': '%1D',
  '\x1e': '%1E',
  '\x1f': '%1F',
  ' ': '%20',
  '\"': '%22',
  '\'': '%27',
  '(': '%28',
  ')': '%29',
  '<': '%3C',
  '>': '%3E',
  '\\': '%5C',
  '{': '%7B',
  '}': '%7D',
  '\x7f': '%7F',
  '\x85': '%C2%85',
  '\xa0': '%C2%A0',
  '\u2028': '%E2%80%A8',
  '\u2029': '%E2%80%A9',
  '\uff01': '%EF%BC%81',
  '\uff03': '%EF%BC%83',
  '\uff04': '%EF%BC%84',
  '\uff06': '%EF%BC%86',
  '\uff07': '%EF%BC%87',
  '\uff08': '%EF%BC%88',
  '\uff09': '%EF%BC%89',
  '\uff0a': '%EF%BC%8A',
  '\uff0b': '%EF%BC%8B',
  '\uff0c': '%EF%BC%8C',
  '\uff0f': '%EF%BC%8F',
  '\uff1a': '%EF%BC%9A',
  '\uff1b': '%EF%BC%9B',
  '\uff1d': '%EF%BC%9D',
  '\uff1f': '%EF%BC%9F',
  '\uff20': '%EF%BC%A0',
  '\uff3b': '%EF%BC%BB',
  '\uff3d': '%EF%BC%BD'
}

def _REPLACER_FOR_NORMALIZE_URI__AND__FILTER_NORMALIZE_URI__AND__FILTER_NORMALIZE_MEDIA_URI(match):
  ch = match.group(0)
  return _ESCAPE_MAP_FOR_NORMALIZE_URI__AND__FILTER_NORMALIZE_URI__AND__FILTER_NORMALIZE_MEDIA_URI[ch]


_MATCHER_FOR_ESCAPE_HTML = re.compile(r'[\x00\x22\x26\x27\x3c\x3e]', re.U)

_MATCHER_FOR_NORMALIZE_HTML = re.compile(r'[\x00\x22\x27\x3c\x3e]', re.U)

_MATCHER_FOR_ESCAPE_HTML_NOSPACE = re.compile(r'[\x00\x09-\x0d \x22\x26\x27\x2d\/\x3c-\x3e`\x85\xa0\u2028\u2029]', re.U)

_MATCHER_FOR_NORMALIZE_HTML_NOSPACE = re.compile(r'[\x00\x09-\x0d \x22\x27\x2d\/\x3c-\x3e`\x85\xa0\u2028\u2029]', re.U)

_MATCHER_FOR_ESCAPE_JS_STRING = re.compile(r'[\x00\x08-\x0d\x22\x26\x27\/\x3c-\x3e\x5b-\x5d\x7b\x7d\x85\u2028\u2029]', re.U)

_MATCHER_FOR_ESCAPE_JS_REGEX = re.compile(r'[\x00\x08-\x0d\x22\x24\x26-\/\x3a\x3c-\x3f\x5b-\x5e\x7b-\x7d\x85\u2028\u2029]', re.U)

_MATCHER_FOR_ESCAPE_CSS_STRING = re.compile(r'[\x00\x08-\x0d\x22\x26-\x2a\/\x3a-\x3e@\\\x7b\x7d\x85\xa0\u2028\u2029]', re.U)

_MATCHER_FOR_NORMALIZE_URI__AND__FILTER_NORMALIZE_URI__AND__FILTER_NORMALIZE_MEDIA_URI = re.compile(r'[\x00- \x22\x27-\x29\x3c\x3e\\\x7b\x7d\x7f\x85\xa0\u2028\u2029\uff01\uff03\uff04\uff06-\uff0c\uff0f\uff1a\uff1b\uff1d\uff1f\uff20\uff3b\uff3d]', re.U)

_FILTER_FOR_FILTER_CSS_VALUE = re.compile(r"""^(?!-*(?:expression|(?:moz-)?binding))(?!\s+)(?:[.#]?-?(?:[_a-z0-9-]+)(?:-[_a-z0-9-]+)*-?|(?:rgb|hsl)a?\([0-9.%, ]+\)|-?(?:[0-9]+(?:\.[0-9]*)?|\.[0-9]+)(?:[a-z]{1,2}|%)?|!important|\s+)*\Z""", re.U | re.I)

_FILTER_FOR_FILTER_NORMALIZE_URI = re.compile(r"""^(?![^#?]*/(?:\.|%2E){2}(?:[/?#]|\Z))(?:(?:https?|mailto):|[^&:/?#]*(?:[/?#]|\Z))""", re.U | re.I)

_FILTER_FOR_FILTER_NORMALIZE_MEDIA_URI = re.compile(r"""^[^&:/?#]*(?:[/?#]|\Z)|^https?:|^data:image/[a-z0-9+]+;base64,[a-z0-9+/]+=*\Z|^blob:""", re.U | re.I)

_FILTER_FOR_FILTER_IMAGE_DATA_URI = re.compile(r"""^data:image/(?:bmp|gif|jpe?g|png|tiff|webp);base64,[a-z0-9+/]+=*\Z""", re.U | re.I)

_FILTER_FOR_FILTER_TEL_URI = re.compile(r"""^tel:[0-9a-z;=\-+._!~*' /():&$#?@,]+\Z""", re.U | re.I)

_FILTER_FOR_FILTER_HTML_ATTRIBUTES = re.compile(r"""^(?!on|src|(?:style|action|archive|background|cite|classid|codebase|data|dsync|href|longdesc|usemap)\s*$)(?:[a-z0-9_$:-]*)\Z""", re.U | re.I)

_FILTER_FOR_FILTER_HTML_ELEMENT_NAME = re.compile(r"""^(?!script|style|title|textarea|xmp|no)[a-z0-9_$:-]*\Z""", re.U | re.I)

def escape_html_helper(value):
  value = str(value)
  return _MATCHER_FOR_ESCAPE_HTML.sub(
      _REPLACER_FOR_ESCAPE_HTML__AND__NORMALIZE_HTML__AND__ESCAPE_HTML_NOSPACE__AND__NORMALIZE_HTML_NOSPACE, value)


def normalize_html_helper(value):
  value = str(value)
  return _MATCHER_FOR_NORMALIZE_HTML.sub(
      _REPLACER_FOR_ESCAPE_HTML__AND__NORMALIZE_HTML__AND__ESCAPE_HTML_NOSPACE__AND__NORMALIZE_HTML_NOSPACE, value)


def escape_html_nospace_helper(value):
  value = str(value)
  return _MATCHER_FOR_ESCAPE_HTML_NOSPACE.sub(
      _REPLACER_FOR_ESCAPE_HTML__AND__NORMALIZE_HTML__AND__ESCAPE_HTML_NOSPACE__AND__NORMALIZE_HTML_NOSPACE, value)


def normalize_html_nospace_helper(value):
  value = str(value)
  return _MATCHER_FOR_NORMALIZE_HTML_NOSPACE.sub(
      _REPLACER_FOR_ESCAPE_HTML__AND__NORMALIZE_HTML__AND__ESCAPE_HTML_NOSPACE__AND__NORMALIZE_HTML_NOSPACE, value)


def escape_js_string_helper(value):
  value = str(value)
  return _MATCHER_FOR_ESCAPE_JS_STRING.sub(
      _REPLACER_FOR_ESCAPE_JS_STRING__AND__ESCAPE_JS_REGEX, value)


def escape_js_regex_helper(value):
  value = str(value)
  return _MATCHER_FOR_ESCAPE_JS_REGEX.sub(
      _REPLACER_FOR_ESCAPE_JS_STRING__AND__ESCAPE_JS_REGEX, value)


def escape_css_string_helper(value):
  value = str(value)
  return _MATCHER_FOR_ESCAPE_CSS_STRING.sub(
      _REPLACER_FOR_ESCAPE_CSS_STRING, value)


def filter_css_value_helper(value):
  value = str(value)
  if not _FILTER_FOR_FILTER_CSS_VALUE.search(value):
    return 'zSoyz'

  return value


def normalize_uri_helper(value):
  value = str(value)
  return _MATCHER_FOR_NORMALIZE_URI__AND__FILTER_NORMALIZE_URI__AND__FILTER_NORMALIZE_MEDIA_URI.sub(
      _REPLACER_FOR_NORMALIZE_URI__AND__FILTER_NORMALIZE_URI__AND__FILTER_NORMALIZE_MEDIA_URI, value)


def filter_normalize_uri_helper(value):
  value = str(value)
  if not _FILTER_FOR_FILTER_NORMALIZE_URI.search(value):
    return 'about:invalid#zSoyz'

  return _MATCHER_FOR_NORMALIZE_URI__AND__FILTER_NORMALIZE_URI__AND__FILTER_NORMALIZE_MEDIA_URI.sub(
      _REPLACER_FOR_NORMALIZE_URI__AND__FILTER_NORMALIZE_URI__AND__FILTER_NORMALIZE_MEDIA_URI, value)


def filter_normalize_media_uri_helper(value):
  value = str(value)
  if not _FILTER_FOR_FILTER_NORMALIZE_MEDIA_URI.search(value):
    return 'about:invalid#zSoyz'

  return _MATCHER_FOR_NORMALIZE_URI__AND__FILTER_NORMALIZE_URI__AND__FILTER_NORMALIZE_MEDIA_URI.sub(
      _REPLACER_FOR_NORMALIZE_URI__AND__FILTER_NORMALIZE_URI__AND__FILTER_NORMALIZE_MEDIA_URI, value)


def filter_image_data_uri_helper(value):
  value = str(value)
  if not _FILTER_FOR_FILTER_IMAGE_DATA_URI.search(value):
    return 'data:image/gif;base64,zSoyz'

  return value


def filter_tel_uri_helper(value):
  value = str(value)
  if not _FILTER_FOR_FILTER_TEL_URI.search(value):
    return 'about:invalid#zSoyz'

  return value


def filter_html_attributes_helper(value):
  value = str(value)
  if not _FILTER_FOR_FILTER_HTML_ATTRIBUTES.search(value):
    return 'zSoyz'

  return value


def filter_html_element_name_helper(value):
  value = str(value)
  if not _FILTER_FOR_FILTER_HTML_ELEMENT_NAME.search(value):
    return 'zSoyz'

  return value

_HTML_TAG_REGEX = re.compile(r"""<(?:!|/?([a-zA-Z][a-zA-Z0-9:\-]*))(?:[^>'"]|"[^"]*"|'[^']*')*>""", re.U)

_LT_REGEX = re.compile('<')

_SAFE_TAG_WHITELIST = ('b', 'br', 'em', 'i', 's', 'sub', 'sup', 'u')


# END GENERATED CODE
