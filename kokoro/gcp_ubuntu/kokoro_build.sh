#!/bin/bash

# Fail on any error.
set -e

${KOKORO_ARTIFACTS_DIR}/github/closure-templates/opensource/kokoro/build.sh
