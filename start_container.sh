#!/bin/sh
#
# © 2023-2025 Nokia
# Licensed under the Apache License 2.0
# SPDX-License-Identifier: Apache-2.0
#

set -e -u

IMAGE="sbtscala/scala-sbt:amazoncorretto-al2023-21.0.7_1.11.0_3.7.0"
_DIR=$(pwd)

docker run -it --rm --privileged --mount type=bind,src="$_DIR",dst="/root/stm-benchmark" "$IMAGE" /bin/bash
