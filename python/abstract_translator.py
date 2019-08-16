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

"""This module defines the API for a valid i18n extension.

All valid i18n extensions should implement methods defined in this class.

class SimpleTranslator(AbstractTranslator):
  def prepare(self, msg_id, msg_text, msg_placeholders):
    ...

  def render(self, msg, values, is_html=False):
    ...

At python runtime, an instance of the actual implementation class should
be assigned to 'msg_translator_impl'

msg_translator_impl = SimpleTranslator()

"""
# Emulate Python 3 style unicode string literals.
from __future__ import unicode_literals

__author__ = ('fredriklundh@google.com (Fredrik Lundh)',
              'steveyang@google.com (Chenyun Yang)',)

# To allow the rest of the file to assume Python 3 strings, we will assign str
# to unicode for Python 2. This will error in 3 and be ignored.
try:
  str = unicode  # pylint: disable=redefined-builtin, invalid-name
except NameError:
  pass


class AbstractTranslator(object):
  """This class defines the API for a valid i18n extension."""

  def is_msg_available(self, msg_id):
    """Determines whether an individual message is available for translation.

    Args:
      msg_id: The id of the message to test.
    Returns:
      Whether the message is available for translation.
    """
    raise NotImplementedError

  def prepare(self, msg_id, msg_text, msg_placeholders):
    """Prepares an I18N string for later rendering.

    This function takes a message identifier, the message text, and a
    list of placeholder names, and wraps them in some opaque
    data structure that's easy to render for the underlying system.

    The result is passed to the render() function, along with the
    variables to insert.

    This method is typically called once for each source message, at
    module import time.  It can be called at other times, though, and
    may in theory be called multiple times for the same message, so
    shouldn't have any side effects.

    A given source string is keyed on the message identifier.

    The placeholders list is a list of identifiers.

    For embedded expressions, this is the name of a temporary variable
    in Soy format. For HTML expressions, the placeholders are the
    names of Soy-generated HTML tag names.

    The text string uses Soy syntax to mark where the placeholders
    should be inserted, e.g.

       # embedded expression
       msg_text = 'This is a {$value}'
       msg_placeholders = ('$value',)

       # soy generated HTML tags
       msg_text = 'Please click {START_LINK}here{END_LINK}.'
       msg_placeholders = ('START_LINK', 'END_LINK')

    If the string has no placeholders, consider using `prepare_literal`.

    Args:
      msg_id: Message identifier.  This is the same identifier as you
        get when extracting messages to the TC, given as a short
        numeric string.
      msg_text: Message text.  Placeholders are marked as {name}
        (similar to Python's format syntax).  To add { or } to the
        string itself, write as {{ or }}.
      msg_placeholders: A tuple containing placeholder names.  If
        there are no placeholders in the string, consider using the
        literal API instead.
    Returns:
      An opaque object that's passed to the render() method to
      render this string.

    """
    raise NotImplementedError('placeholder strings not supported')

  def render(self, msg, values, is_html=False):
    """Renders a prepared I18N string.

    This takes the opaque object returned by prepare() and
    renders it to a string (or any other object that can be placed in
    the output buffer).

    TODO: consider use a tuple of ordered values.

    Args:
      msg: A message object created by prepare.
      values: A dictionary contains names and values for the
        corresponding placeholders.
      is_html: Whether the message is rendered in the HTML context.

    Returns:
      The rendered string.
    """
    raise NotImplementedError

  # String literals.

  def prepare_literal(self, msg_id, msg_text):
    """Prepares an I18N literal string for later rendering.

    Same as prepare(), but doesn't support placeholders.

    Args:
      msg_id: Message identifier.
      msg_text: Message text.
    Returns:
      An opaque object that's passed to the render_literal()
      method to render this string.
    """
    raise NotImplementedError('literal strings not supported')

  def render_literal(self, msg, is_html=False):
    """Renders a prepared I18N literal string.

    Args:
      msg: A message object created by prepare_literal.
      is_html: Whether the message is rendered in the HTML context.
    Returns:
      The rendered string.
    """
    raise NotImplementedError

  # Plural Msg Node
  # We separate this case because single branch cases could be optimized

  def prepare_plural(self, msg_id, msg_cases, msg_placeholders):
    """Prepare an ICU string for rendering.

    Same as prepare(), but takes msg_cases dict instead of raw msg_text.
    This is used for plural translation. (To be more precise, plural msg node
    without genders attribute, the later is rewritten by the compiler to
    select node)

    Args:
      msg_id: Message identifier.  This is the same identifier as you
        get when extracting messages to the TC, given as a short
        numeric string.
      msg_cases: A dict maps from case spec string to a branch msg_text.
        Case spec comes in two possible format. "=<number>" for explicit value,
        or the string "other" for default. The values of the dict are in the
        same format of msg_text in the general prepare methods. For example:
        {'other': '{$numDrafts} drafts', '=0': 'No drafts', '=1': '1 draft'}
      msg_placeholders: A tuple containing placeholder names.  If
        there are no placeholders in the string, consider using the
        literal API instead.
    Returns:
      An opaque object that's passed to the render() method to
      render this string.
    """
    raise NotImplementedError('ICU strings not supported')

  def render_plural(self, msg, case_value, values, is_html=False):
    """Renders a prepared plural msg object.

    Args:
      msg: A message object created by prepare_plural.
      case_value: An integer for the case value.
      values: A dictionary contains names and values for the
        corresponding placeholders.
      is_html: Whether the message is rendered in the HTML context.

    Returns:
      The rendered string.
    """
    raise NotImplementedError

  # Arbitrary ICU strings.

  def prepare_icu(self, msg_id, msg_text, msg_fields, is_html=False):
    """Prepare an ICU string for rendering.

    Same as prepare(), but takes a string in ICU syntax for
    msg_text.  This is used for select and plural translation.

    Args:
      msg_id: Message identifier.
      msg_text: An ICU string.
      msg_fields: A tuple containing the names of all configurable ICU fields.
      is_html: Whether the message is rendered in the HTML context.
    Returns:
      An opaque object that's passed to the render_icu()
      method to render this string.
    """
    raise NotImplementedError('ICU strings not supported')

  def render_icu(self, msg, values):
    """Renders a prepared ICU string object.

    Args:
      msg: A message object created by prepare_icu.
      values: A dictionary contains names and values for the
        corresponding placeholders.
    Returns:
      The rendered string.
    """
    raise NotImplementedError

  def format_num(self, value, target_format):
    """Formats a number into a specific format (decimal, currency, etc.).

    Args:
      value: The value to format.
      target_format: The target number format.
    Returns:
      The given number formatted into a string.
    """
    raise NotImplementedError
