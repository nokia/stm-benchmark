<!--

   © 2023-2024 Nokia
   Licensed under the Apache License 2.0
   SPDX-License-Identifier: Apache-2.0

-->

# STM benchmarks

*Benchmarks for software transactional memory (STM) implementations in Scala*

Based on the idea of [chrisseaton/ruby-stm-lee-demo](https://github.com/chrisseaton/ruby-stm-lee-demo),
we've implemented (a simplified version of) *Lee’s algorithm*, with STM (to parallelize it).

## Tested STM implementations

We've implemented the algorithm in various Scala STMs:

- [Cats STM](https://github.com/TimWSpence/cats-stm) in folder [cats-stm](/cats-stm).
- [CHOAM](https://github.com/durban/choam) in folder [choam](/choam) (technically not an STM, but close enough).
- [ZSTM](https://github.com/zio/zio/tree/series/2.x/core/shared/src/main/scala/zio/stm) in folder [zstm](/zstm).

We also have a (baseline) sequential (non-parallelized) implementation of the same algorithm in folder
[sequential](/sequential).
