#!/bin/sh
#
# © 2023-2025 Nokia
# Licensed under the Apache License 2.0
# SPDX-License-Identifier: Apache-2.0
#

set -e -u

IMAGE="sbtscala/scala-sbt:amazoncorretto-al2023-17.0.15_1.11.0_3.3.6"

docker run -it --rm --privileged "$IMAGE" /bin/bash
