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

from . import abstract_translator
from icu import Formattable
from icu import Locale
from icu import MessageFormat

# To allow the rest of the file to assume Python 3 strings, we will assign str
# to unicode for Python 2. This will error in 3 and be ignored.
try:
  str = unicode  # pylint: disable=redefined-builtin, invalid-name
except NameError:
  pass


class SimpleTranslator(abstract_translator.AbstractTranslator):
  """Minimal implementation of the i18n extension API.

  This is a minimal implementation of the core API for demo purpose.
  """

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

  def prepare_icu(self, msg_id, msg_text):
    return MessageFormat(msg_text, Locale('en'))

  def render_icu(self, msg, values):
    return msg.format(values.keys(), map(Formattable, values.values()))
