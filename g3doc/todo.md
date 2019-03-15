# TODO

This file is meant to track technical debt issues in the Soy codebase. This
isn't meant to track bugs or feature requests, but rather long term design
issues.


## Eliminate the SoyData type hierarchy

There is a half finished migration of `SoyData` -> `SoyValue` this should be
completed and the various SoyData classes should be deleted. We should also try
to delete the public use of internal soy collection objects (`SoyEasyList`,
`SoyEasyDict`...).
