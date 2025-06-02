#!/bin/sh
#
# © 2023-2025 Nokia
# Licensed under the Apache License 2.0
# SPDX-License-Identifier: Apache-2.0
#

# Build and tag image with:
# docker build -t stm-benchmark-image .

FROM sbtscala/scala-sbt:amazoncorretto-al2023-21.0.7_1.11.0_3.7.0

# Download dependencies to speed up running benchmarks;
# we're not building the benchmarks themselves, to
# make sure there are no incremental compilation issues:
RUN sbt ";skipBanner;update"
