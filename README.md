<!--

   © 2023-2024 Nokia
   Licensed under the Apache License 2.0
   SPDX-License-Identifier: Apache-2.0

-->

# STM benchmarks

*Benchmarks for software transactional memory (STM) implementations on the JVM*

Based on the idea of [chrisseaton/ruby-stm-lee-demo](https://github.com/chrisseaton/ruby-stm-lee-demo)
(and originally on [Lee-TM](https://apt.cs.manchester.ac.uk/projects/TM/LeeBenchmark/)),
we've implemented (a simplified version of) *Lee’s routing algorithm*, and used STM to parallelize it.

Further reading:

- https://chrisseaton.com/truffleruby/ruby-stm/ (the Ruby implementation referred to above),
- and the two papers about Lee-TM:
  - Ian Watson, Chris Kirkham and Mikel Luján.
    "A Study of a Transactional Parallel Routing Algorithm."
    _In Proceedings of the 16th International Conference on Parallel Architectures and Compilation Techniques (PACT 2007),
    Brasov, Romania, Sept. 2007, pp 388-398._
    ([PDF](https://apt.cs.manchester.ac.uk/apt/projects/TM/LeeRouting/lee-TM-pact2007.pdf))
  - Mohammad Ansari, Christos Kotselidis, Kim Jarvis, Mikel Luján, Chris Kirkham, and Ian Watson.
    "Lee-TM: A Non-trivial Benchmark for Transactional Memory."
    _In Proceedings of the 8th International Conference on Algorithms and Architectures for Parallel Processing (ICA3PP 2008),
    Aiya Napa, Cyprus, June 2008._
    ([PDF](https://apt.cs.manchester.ac.uk/apt/people/ansarim/papers/pdfs/ica3pp08-ansari.pdf))

## Tested STM implementations

We've implemented Lee's algorithm with various STMs in Scala (and one in Kotlin). We've tried to
implement the algorithm as similar as reasonably possible in every implementation, but we didn't
write (intentionally) unidiomatic code just to be more similar. The tested/measured STMs are (in
alphabetic order) as follows (with some remarks for each implementation):

- [arrow-fx-stm](https://arrow-kt.io/learn/coroutines/stm) in folder [arrow-stm](/arrow-stm)
  - The algorithm is written in Kotlin, with a thin Scala wrapper; certain parts of the Kotlin
    code are weird due to trying to implement a Scala API without excessive copying.
  - We run the STM transactions on the default coroutine dispatcher of Kotlin (as they're
    probably expected to be used).
  - We also have to run some `cats.effect.IO`s (for loading the boards), but we run these
    also on the same coroutine dispatcher.
  - We use `arrow.fx.stm.TArray` for the board matrices.
- [Cats STM](https://github.com/TimWSpence/cats-stm) in folder [cats-stm](/cats-stm)
  - We run the Cats STM transactions on a Cats Effect runtime, which they're designed to run on.
  - We disable tracing in the runtime, to avoid the negative performance impact.
  - Cats STM doesn't have a built-in `TArray` or similar type, so we use `Array[TVar[A]]` for the
    board matrices.
- [CHOAM](https://github.com/durban/choam) in folder [choam](/choam)
  - This is technically not an STM, but close enough (this algorithm doesn't require
    _everything_ from an STM, e.g., there is no need for the `orElse` combinator).
  - We run the `Rxn`s on a Cats Effect runtime, which they're designed to run on.
  - We disable tracing in the runtime, to avoid the negative performance impact.
  - For the board matrices we use the built-in `Ref.array` in CHOAM.
- [ScalaSTM](https://github.com/scala-stm/scala-stm) in folder [scala-stm](/scala-stm)
  - We've implemented 2 versions:
    [`ScalaStmSolver`](scala-stm/src/main/scala/com/nokia/stmbenchmark/scalastm/ScalaStmSolver.scala)
    uses the ScalaSTM API in an idiomatic way, while
    [`WrStmSolver`](scala-stm/src/main/scala/com/nokia/stmbenchmark/scalastm/WrStmSolver.scala)
    wraps the ScalaSTM API in a monadic API similar to that of Cats STM or ZSTM. This way
    we can also get some ideas about the overhead of a monadic ("programs as values") API.
  - For easy parallelization, we run the ScalaSTM transactions on a Cats Effect runtime.
    ScalaSTM sometimes blocks threads, but does this by using `scala.concurrent.BlockContext`,
    which is supported by the Cats Effect runtime (it starts compensating threads as necessary),
    so this should be fine (although maybe not ideal).
  - We disable tracing in the runtime, to avoid the negative performance impact.
  - We use ScalaSTM's `TArray` for the board matrices.
- [ZSTM](https://github.com/zio/zio/tree/series/2.x/core/shared/src/main/scala/zio/stm) in folder [zstm](/zstm).
  - We run the ZSTM transactions on their own `zio.Runtime`, which they seem designed for.
  - We disable `FiberRoots` in the runtime, to avoid the negative performance impact.
  - We use ZSTM's `TArray` for the board matrices.

Some general remarks:

- The transactions in these implementations of Lee’s routing algorithm are read heavy,
  but at the end they always write to some locations (to lay a route). This means that
  read-only transactions, and transactions which only access a very small number of
  `TVar`s are not tested/measured.
- We also have a (baseline) sequential (non-parallelized) implementation of the same algorithm in folder
  [sequential](/sequential). This sequential implementation is intentionally not very well optimized,
  because we'd like to compare it to similarly high-level and easy to use STMs.

## Benchmarks

Benchmarks are in [`Benchmarks.scala`](benchmarks/src/main/scala/com/nokia/stmbenchmark/benchmarks/Benchmarks.scala).
They can be configured with the following JMH parameters:

- `board` (`String`): the input(s) are specified by this parameter, which is a filename to be loaded from classpath resources.
  - `testBoard.txt`: originally from Lee-TM, apparently a "small but realistic board".
  - `sparselong_mini.txt`: a small version of `sparselong.txt`, originally from Lee-TM;
    it has very long routes, so there are lots of conflicts between the transactions.
  - `sparseshort_mini.txt`: a small version of `sparseshort.txt`, originally from Lee-TM;
    it has very short routes, which cause transactions to have few conflicts.
  - `four_crosses.txt`: a very small board we've created, with very short routes, which still
    have both some conflicts, and also some possibilities for parallelization.
- `seed` (`Long`): before solving, the boards are "normalized" with a pseudorandom shuffle; this is the random seed to use.
- `restrict` (`Int`):
  - Before solving, the boards are "restricted", i.e., some of the routes are removed from them.
    This makes solving them easier (because there is less work, and also less chance of conflicts).
  - The value passed to this parameter will be used to `>>` (right shift) the number of routes;
    e.g., `restrict=1` will remove approximately half of the routes. (The routes to remove are
    chosen pseudorandomly based on `seed`.)
  - The goal with this parameter is to run more measurements, e.g., with `restrict=2,1,0`, to see
    how the STMs deal with increasing work (and also conflicts).

The various parallel implementations are tunable with more parameters:

- `parLimit` (`Int`): parallelism is limited to this value (e.g., with `parTraverseN`), but see below; specify `0` to use
  `Runtime.getRuntime().availableProcessors()`.
- `parLimitMultiplier` (`Int`): we use `parLimit * parLimitMultiplier` as the parallelism limit.
