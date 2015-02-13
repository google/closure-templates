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

"""Functionality for basic print directives.

This module contains functions to support all non-escaping related print
directives which do not have direct Python support. Escaping directives live in
sanitize.py.
"""

# Emulate Python 3 style unicode string literals.
from __future__ import unicode_literals

__author__ = 'dcphillips@google.com (David Phillips)'

# To allow the rest of the file to assume Python 3 strings, we will assign str
# to unicode for Python 2. This will error in 3 and be ignored.
try:
  str = unicode
except NameError:
  pass


def truncate(value, max_len, add_ellipsis):
  """Truncate a string to the max_len and optionally add ellipsis.

  Args:
    value: The input string to truncate.
    max_len: The maximum length allowed for the result.
    add_ellipsis: Whether or not to add ellipsis.

  Returns:
    A truncated string.
  """
  if len(value) <= max_len:
    return value

  # If the max_len is too small, ignore any ellipsis logic.
  if not add_ellipsis or max_len <= 3:
    return value[:max_len]

  # Reduce max_len to compensate for the ellipsis length.
  max_len -= 3

  # Truncate and add the ellipsis.
  return value[:max_len] + '...'
