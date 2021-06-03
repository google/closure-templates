## python

This directory contains the Python runtime files needed by templates compiled
into Python.

Given Python's package structure, no assumptions are made in the compiled code
about the package these modules reside in. Instead, users should provide the
runtime package as a flag to the compiler ("--runtimePath").
