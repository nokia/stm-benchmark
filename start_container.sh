#!/bin/sh
#
# © 2023-2025 Nokia
# Licensed under the Apache License 2.0
# SPDX-License-Identifier: Apache-2.0
#

set -e -u

IMAGE="stm-benchmark-image:latest"
_DIR="$(pwd)/benchmarks/results"

docker run -it --rm --privileged --mount type=bind,src="$_DIR",dst="/root/stm-benchmark/benchmarks/results" "$IMAGE" /bin/bash
