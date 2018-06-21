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

"""Libraries for accessing and interacting with the environment.

This module provides environmental related libraries which can be overridden
with the environmentModulePath compiler flag.
"""

# Emulate Python 3 style unicode string literals.
from __future__ import unicode_literals

__author__ = 'dcphillips@google.com (David Phillips)'

# To allow the rest of the file to assume Python 3 strings, we will assign str
# to unicode for Python 2. This will error in 3 and be ignored.
try:
  str = unicode  # pylint: disable=redefined-builtin, invalid-name
except NameError:
  pass


def file_loader(file_path, file_name, access_mode='r'):
  return open('%s/%s' % (file_path, file_name), access_mode)
