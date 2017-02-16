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
"""Runtime module for compiled soy templates.

This module provides utility functions required by soy templates compiled with
the Python compilers. These functions handle the runtime internals necessary to
match JS behavior in module and function loading, along with type behavior.
"""

# Emulate Python 3 style unicode string literals.
from __future__ import unicode_literals

__author__ = 'dcphillips@google.com (David Phillips)'

import importlib
import math
import os
import re
import sys

from . import environment

try:
  import scandir
except ImportError:
  scandir = None

# To allow the rest of the file to assume Python 3 strings, we will assign str
# to unicode for Python 2. This will error in 3 and be ignored.
try:
  str = unicode  # pylint: disable=redefined-builtin, invalid-name
except NameError:
  pass

# Map from registered delegate template key to the priority, function, and
# function name tuple.
_DELEGATE_REGISTRY = {}

# All number types for use during custom type functions.
_NUMBER_TYPES = (int, long, float)

# The mapping of css class names for get_css_name.
_css_name_mapping = None


def get_xid_name(name):   # pylint: disable=unused-argument
  """Not currently implemented."""
  raise NotImplementedError('xid is not implemented')


def get_css_name(class_name, modifier=None):
  """Return the mapped css class name with modifier.

  Following the pattern of goog.getCssName in closure, this function maps a css
  class name to its proper name, and applies an optional modifier.

  If no mapping is present, the class_name and modifier are joined with hyphens
  and returned directly.

  If a mapping is present, the resulting css name will be retrieved from the
  mapping and returned.

  If one argument is passed it will be processed, if two are passed only the
  modifier will be processed, as it is assumed the first argument was generated
  as a result of calling goog.getCssName.

  Args:
    class_name: The class name to look up.
    modifier: An optional modifier to append to the class_name.

  Returns:
    A mapped class name with optional modifier.
  """
  pieces = [class_name]
  if modifier:
    pieces.append(modifier)

  if _css_name_mapping:
    # Only map the last piece of the name.
    pieces[-1] = _css_name_mapping.get(pieces[-1], pieces[-1])

  return '-'.join(pieces)


def set_css_name_mapping(mapping):
  """Set the mapping of css names.

  Args:
    mapping: A dictionary of original class names to mapped class names.
  """
  global _css_name_mapping
  _css_name_mapping = mapping


def get_delegate_fn(template_id, variant, allow_empty_default):
  """Get the delegate function associated with the given template_id/variant.

  Retrieves the (highest-priority) implementation that has been registered for
  a given delegate template key (template_id and variant). If no implementation
  has been registered for the key, then the fallback is the same template_id
  with empty variant. If the fallback is also not registered,
  and allow_empty_default is true, then returns an implementation that is
  equivalent to an empty template (i.e. rendered output would be empty string).

  Args:
    template_id: The delegate template id.
    variant: The delegate template variant (can be an empty string, or a number
        when a global is used).
    allow_empty_default: Whether to default to the empty template function if
        there's no active implementation.

  Returns:
    The retrieved implementation function.

  Raises:
    RuntimeError: when no implementation of one delegate template is found.
  """
  entry = _DELEGATE_REGISTRY.get(_gen_delegate_id(template_id, variant))
  fn = entry[1] if entry else None
  if not fn and variant != '':
    # Fallback to empty variant.
    entry = _DELEGATE_REGISTRY.get(_gen_delegate_id(template_id))
    fn = entry[1] if entry else None

  if fn:
    return fn
  elif allow_empty_default:
    return _empty_template_function
  else:
    msg = ('Found no active impl for delegate call to "%s:%s"'
           '(and not allow_empty_default="true").')
    raise RuntimeError(msg % (template_id, variant))


def merge_into_dict(original, secondary):
  """Merge two dictionaries into the first and return it.

  This is simply a conveinence wrapper around the dictionary update method. In
  addition to the update it returns the original dict to allow for chaining.

  Args:
    original: The dict which will be updated.
    secondary: The dict which will be copied.

  Returns:
    The updated original dictionary.
  """
  original.update(secondary)
  return original


def namespaced_import(name, namespace=None, environment_path=None):
  """A function to import compiled soy modules using the Soy namespace.

  This function attempts to first import the module directly. If it isn't found
  in the matching package as the Soy Namespace, it will walk the sys.path
  structure open any module with a matching name and test its SOY_NAMESPACE
  attribute. If it matches it will load that instead.

  Multiple files can share the same soy namespace. In that instance, all of
  these files will be loaded, combined, and loaded as one module.

  Note: If multiple files share the same namespace, they still require that the
  module name begins with the last part of the namespace (e.g.
  soy.examples.delegates will load delegates0.py, delegatesxyz.py, etc.).
  TODO(dcphillips): See if there's any way we can avoid this limitation without
  blowing up load times.

  Args:
    name: The name of the module to import.
    namespace: The namespace of the module to import.
    environment_path: A custom environment module path for interacting with the
        runtime environment.

  Returns:
    The Module object.
  """
  full_namespace = '%s.%s' % (namespace, name) if namespace else name
  try:
    # Try searching for the module directly
    return importlib.import_module(full_namespace)
  except ImportError:
    # If the module isn't found, search without the namespace and check the
    # namespaces.
    if namespace:
      namespace_key = "SOY_NAMESPACE: '%s'." % full_namespace
      module = None
      if environment_path:
        file_loader = importlib.import_module(environment_path).file_loader
      else:
        file_loader = environment.file_loader
      for sys_path, f_path, f_name in _find_modules(name):
        # Verify the file namespace by comparing the 5th line.
        with file_loader(f_path, f_name, 'r') as f:
          for _ in range(4):
            next(f)
          if namespace_key != next(f).rstrip():
            continue

        # Strip the root path and the file extension.
        module_path = os.path.relpath(f_path, sys_path).replace('/', '.')
        module_name = os.path.splitext(f_name)[0]
        module = getattr(
            __import__(module_path, globals(), locals(), [module_name], -1),
            module_name)
        break
      if module:
        # Add this to the global modules list for faster loading in the future.
        _cache_module(full_namespace, module)
        return module
    raise


def manifest_import(namespace, manifest):
  """Imports a module using a namespace manifest to find the module.
  """
  if not manifest:
    raise ImportError('No manifest provided')
  elif namespace not in manifest:
    raise ImportError('Manfest does not contain namespace: %s' % namespace)

  return importlib.import_module(manifest[namespace])


def key_safe_data_access(data, key):
  """Safe key based data access.

  Traditional bracket access in Python (foo['bar']) will throw a KeyError (or
  IndexError if in a list) when encountering a non-existent key.
  foo.get(key, None) is solves this problem for objects, but doesn't work with
  lists. Thus this function serves to do safe access with a unified syntax for
  both lists and dictionaries.

  Args:
    data: The data object to search for the key within.
    key: The key to use for access.

  Returns:
    data[key] if key is present or None otherwise.
  """
  try:
    return data[key]
  except (KeyError, IndexError):
    return None


def register_delegate_fn(template_id, variant, priority, fn, fn_name):
  """Register a delegate function in the global registry.

  Args:
    template_id: The id for the given template.
    variant: The variation key for the given template.
    priority: The priority value of the given template.
    fn: The template function.
    fn_name: A unique name of the function generated at compile time.

  Raises:
    RuntimeError: If a delegate was attempted to be added with the same
        priority an error will be raised.
  """
  map_key = _gen_delegate_id(template_id, variant)
  curr_priority, _, curr_fn_name = _DELEGATE_REGISTRY.get(map_key,
                                                          (None, None, None))

  # Ignore unless at a equal or higher priority.
  if curr_priority is None or priority > curr_priority:
    # Registering new or higher-priority function: replace registry entry.
    _DELEGATE_REGISTRY[map_key] = (priority, fn, fn_name)
  elif priority == curr_priority and fn_name != curr_fn_name:
    # Registering same-priority function: error.
    raise RuntimeError(
        'Encountered two active delegates with the same priority (%s:%s:%s).' %
        (template_id, variant, priority))


def simplify_num(value, precision):
  """Convert the given value to an int if the precision is below 1.

  Args:
    value: A number value (int, float, etc.).
    precision: The desired precision.
  Returns:
    A number typed as an int if the precision is low enough.
  """
  if precision <= 0:
    return int(value)
  return value


def type_safe_add(*args):
  """A coercion function emulating JS style type conversion in the '+' operator.

  This function is similar to the JavaScript behavior when using the '+'
  operator. Variables will will use the default behavior of the '+' operator
  until they encounter a type error at which point the more 'simple' type will
  be coerced to the more 'complex' type.

  Supported types are None (which is treated like a bool), bool, primitive
  numbers (int, float, etc.), and strings. All other objects will be converted
  to strings.

  Example:
    type_safe_add(True, True) = 2
    type_safe_add(True, 3) = 4
    type_safe_add(3, 'abc') = '3abc'
    type_safe_add(True, 3, 'abc') = '4abc'
    type_safe_add('abc', True, 3) = 'abcTrue3'

  Args:
    *args: List of parameters for addition/coercion.

  Returns:
    The result of the addition. The return type will be based on the most
    'complex' type passed in. Typically an integer or a string.
  """
  if not args:
    return None

  # JS operators can sometimes work as unary operators. So, we fall back to the
  # initial value here in those cases to prevent ambiguous output.
  if len(args) == 1:
    return args[0]

  is_string = isinstance(args[0], basestring)
  result = args[0]
  for arg in args[1:]:
    try:
      if is_string:
        arg = _convert_to_js_string(arg)
      result += arg
    except TypeError:
      # Special case for None which can be converted to bool but is not
      # autocoerced. This can result in a conversion of result from a boolean to
      # a number (which can affect later string conversion) and should be
      # retained.
      if arg is None:
        result += False
      else:
        result = _convert_to_js_string(result) + _convert_to_js_string(arg)
        is_string = True

  return result


def type_safe_eq(first, second):
  """An equality function that does type coercion for various scenarios.

  This function emulates JavaScript's equalty behavior. In JS, Objects will be
  converted to strings when compared to a string primitive.

  Args:
    first: The first value to compare.
    second: The second value to compare.

  Returns:
    True/False depending on the result of the comparison.
  """
  # If the values are empty or of the same type, no coersion necessary.
  # TODO(dcphillips): Do a more basic type equality check if it's not slower
  # (b/16661176).
  if first is None or second is None or type(first) == type(second):
    return first == second

  try:
    # TODO(dcphillips): This potentially loses precision for very large numbers.
    # See b/16241488.
    if isinstance(first, _NUMBER_TYPES) and not isinstance(first, bool):
      return first == float(second)
    if isinstance(second, _NUMBER_TYPES) and not isinstance(second, bool):
      return float(first) == second

    if isinstance(first, basestring):
      return first == str(second)
    if isinstance(second, basestring):
      return str(first) == second
  except ValueError:
    # Ignore type coersion failures
    pass

  return first == second


def check_not_null(val):
  """A helper to implement the Soy Function checkNotNull.

  Args:
    val: The value to test.

  Returns:
    val if it was not None.

  Raises:
    RuntimeError: If val is None.
  """
  if val is None:
    raise RuntimeError('Unexpected null value')
  return val


def parse_int(s):
  """A function that attempts to convert the input string into an int.

  Returns None if the input is not a valid int.

  Args:
    s: String to convert.

  Returns:
    int if s is a valid int string, otherwise None.
  """
  try:
    return int(s)
  except ValueError:
    return None


def parse_float(s):
  """A function that attempts to convert the input string into a float.

  Returns None if the input is not a valid float, or if the input is NaN.

  Args:
    s: String to convert.

  Returns:
    float if s is a valid float string that is not NaN, otherwise None.
  """
  try:
    f = float(s)
  except ValueError:
    return None
  return None if math.isnan(f) else f


######################
# Utility functions. #
######################
# pylint: disable=unused-argument
def _empty_template_function(data=None, ij_data=None):
  return ''


def _cache_module(namespace, module):
  """Cache a loaded module in sys.modules.

  Besides the caching of the main module itself, any parent packages that don't
  exist need to be cached as well.

  Args:
    namespace: The python namespace.
    module: The module object to be cached.
  """
  sys.modules[namespace] = module
  while '.' in namespace:
    namespace = namespace.rsplit('.', 1)[0]
    if namespace in sys.modules:
      return
    # TODO(dcphillips): Determine if anything's gained by having real modules
    # for the packages.
    sys.modules[namespace] = {}


def _convert_to_js_string(value):
  """Convert a value to a string, with the JS string values for primitives.

  Args:
    value: The value to stringify.
  Returns:
    A string representation of value. For primitives, ensure that the result
    matches the string value of their JS counterparts.
  """
  if value is None:
    return 'null'
  elif isinstance(value, bool):
    return str(value).lower()
  else:
    return str(value)


def _find_modules(name):
  """Walks the sys path and looks for modules that start with 'name'.

  This function yields all results which match the pattern in the sys path.
  It can be treated similar to os.walk(), but yields only files which match
  the pattern. These are meant to be used for traditional import
  syntax. Bad paths are ignored and skipped.

  Args:
    name: The name to match against the beginning of the module name.
  Yields:
    A tuple containing the path, the base system path, and the file name.
  """
  # TODO(dcphillips): Allow for loading of compiled source once namespaces are
  # limited to one file (b/16628735).
  module_file_name = re.compile(r'^%s.*\.py$' % name)
  # If scandir is available, it offers 5-20x improvement of walk performance.
  walk = scandir.walk if scandir else os.walk
  for path in sys.path:
    try:
      for root, _, files in walk(path):
        for f in files:
          if module_file_name.match(f):
            yield path, root, f
    except OSError:
      # Ignore bad paths
      pass


def _gen_delegate_id(template_id, variant=''):
  return 'key_%s:%s' % (template_id, variant)
