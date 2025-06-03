#!/bin/sh
#
# © 2023-2025 Nokia
# Licensed under the Apache License 2.0
# SPDX-License-Identifier: Apache-2.0
#

# Build and tag image with:
# docker build -t stm-benchmark-image .

# TODO: sbt 1.11.1
FROM sbtscala/scala-sbt:amazoncorretto-al2023-21.0.7_1.11.0_3.7.0

# copy the build definition only, and download dependencies:
COPY ./build.sbt /root/stm-benchmark/build.sbt
COPY ./project /root/stm-benchmark/project
WORKDIR /root/stm-benchmark
RUN sbt update

# copy everything, and compile everything (even generated benchmark code):
COPY . /root/stm-benchmark
RUN sbt staticAnalysis
RUN sbt "runBenchmarks -lp"
