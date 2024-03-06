<!--

   © 2023-2024 Nokia
   Licensed under the Apache License 2.0
   SPDX-License-Identifier: Apache-2.0

-->

# STM benchmarks

*Benchmarks for software transactional memory (STM) implementations in Scala*

Based on the idea of [chrisseaton/ruby-stm-lee-demo](https://github.com/chrisseaton/ruby-stm-lee-demo),
we've implemented (a simplified version of) *Lee’s algorithm*, and used STM to parallelize it.

## Tested STM implementations

We've implemented the algorithm with various Scala STMs. We tried to use the exact same algorithm
in every implementation, but didn't write intentionally unidiomatic code. The tested/measured
STMs are as follows:

- [Cats STM](https://github.com/TimWSpence/cats-stm) in folder [cats-stm](/cats-stm).
- [CHOAM](https://github.com/durban/choam) in folder [choam](/choam) (technically not an STM, but close enough).
- [ZSTM](https://github.com/zio/zio/tree/series/2.x/core/shared/src/main/scala/zio/stm) in folder [zstm](/zstm).

We also have a (baseline) sequential (non-parallelized) implementation of the same algorithm in folder
[sequential](/sequential). This sequential implementation is intentionally not very well optimized,
because we'd like to compare it to similarly high-level and easy to use STMs.

## Benchmarks

Benchmarks are in [`Benchmarks.scala`](benchmarks/src/main/scala/com/nokia/stmbenchmark/benchmarks/Benchmarks.scala).
They can be configured with the following JMH parameters:

 - `board`: the input(s) are specified by this parameter, which is a filename to be loaded from classpath resources.
 - `seed`: before solving, the boards are "normalized" with a pseudorandom shuffle; this is the random seed to use.

The various parallel implementations are tunable with more parameters:

- For all parallel implementations:
   - `parLimit`: parallelism is limited to this value (e.g., with `parTraverseN`); defaults to
   `Runtime.getRuntime().availableProcessors()`.
- For Cats STM only:
  - `txnLimitMultiplier`: we pass `txnLimitMultiplier * parLimit` to Cats STM as the number of transactions
    that are allowed to evaluate concurrently (the `n` argument to `STM.runtime`).
- For CHOAM only:
  - `strategy`: the `Rxn.Strategy` to use for backoff (`spin | cede |sleep`).
