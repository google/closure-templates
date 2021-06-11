#!/bin/bash

# Fail on any error.
set -e

${KOKORO_ARTIFACTS_DIR}/github/closure-templates/kokoro/build.sh
