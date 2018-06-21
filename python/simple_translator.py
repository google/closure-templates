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

"""This module demos a simple i18n extension."""

# Emulate Python 3 style unicode string literals.
from __future__ import unicode_literals

__author__ = 'steveyang@google.com (Chenyun Yang)'

import icu

from . import abstract_translator

# To allow the rest of the file to assume Python 3 strings, we will assign str
# to unicode for Python 2. This will error in 3 and be ignored.
try:
  str = unicode  # pylint: disable=redefined-builtin, invalid-name
except NameError:
  pass


_NUM_FORMAT_PATTERNS = {
    'currency': '${0:,.2f}',
    'decimal': '{0:,.3f}',
    'percent': '{0:,.0%}',
    'scientific': '{0:.0E}'
}

_SHORT_SUFFIXES = {
    1E3: 'K',
    1E6: 'M',
    1E9: 'B',
    1E12: 'T'
}

_LONG_SUFFIXES = {
    1E3: ' thousand',
    1E6: ' million',
    1E9: ' billion',
    1E12: ' trillion'
}


class SimpleTranslator(abstract_translator.AbstractTranslator):
  """Minimal implementation of the i18n extension API.

  This is a minimal implementation of the core API for demo purpose.
  """

  def is_msg_available(self, msg_id):
    return True

  def prepare_literal(self, msg_id, msg_text):
    # use the string itself as the opaque object
    return msg_text

  def render_literal(self, msg):
    # Calling format() to apply the same escape mechanism for '{' and '} as
    # formatted string
    return msg.format()

  def prepare(self, msg_id, msg_text, msg_placeholders):
    return msg_text

  def render(self, msg, values):
    return msg.format(**values)

  def prepare_plural(self, msg_id, msg_cases, msg_placeholders):
    return msg_cases

  def render_plural(self, msg, case_value, values):
    msg_text = msg.get('=%d' % case_value) or msg.get('other')
    return msg_text.format(**values)

  def prepare_icu(self, msg_id, msg_text, msg_fields):
    return icu.MessageFormat(msg_text, icu.Locale('en'))

  def render_icu(self, msg, values):
    return msg.format(values.keys(), map(_format_icu, values.values()))

  def format_num(self,
                 value,
                 target_format,
                 min_fraction_digits=None,
                 max_fraction_digits=None):

    if min_fraction_digits is not None or max_fraction_digits is not None:
      raise ValueError(
          'Min and max fraction digits arguments are not supported in python')

    if target_format in _NUM_FORMAT_PATTERNS:
      result = _NUM_FORMAT_PATTERNS[target_format].format(value)
      if target_format == 'decimal':
        result = result.rstrip('0').rstrip('.')
      return result
    elif target_format == 'compact_short':
      return _format_compact(value, short=True)
    elif target_format == 'compact_long':
      return _format_compact(value, short=False)

    return str(value)


def _format_icu(value):
  try:
    return icu.Formattable(value)
  except:
    return icu.Formattable(str(value))


def _format_compact(value, short=True):
  """Compact number formatting using proper suffixes based on magnitude.

  Compact number formatting has slightly idiosyncratic behavior mainly due to
  two rules. First, if the value is below 1000, the formatting should just be a
  2 digit decimal formatting. Second, the number is always truncated to leave at
  least 2 digits. This means that a number with one digit more than the
  magnitude, such as 1250, is still left with 1.2K, whereas one more digit would
  leave it without the decimal, such as 12500 becoming 12K.

  Args:
    value: The value to format.
    short: Whether to use the short form suffixes or long form suffixes.
  Returns:
    A formatted number as a string.
  """
  if value < 1000:
    return '{0:.2f}'.format(value).rstrip('0').rstrip('.')

  suffixes = _SHORT_SUFFIXES if short else _LONG_SUFFIXES
  for key, suffix in sorted(suffixes.items(), reverse=True):
    if value >= key:
      value = value / float(key)
      if value >= 10:
        pattern = '{0:,.0f}' + suffix
      else:
        pattern = '{0:.1f}' + suffix
      return pattern.format(value)
