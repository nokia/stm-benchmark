/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package arrowstm

import java.util.concurrent.CompletableFuture

import scala.concurrent.{ Future, ExecutionContext }
import scala.concurrent.duration.FiniteDuration
import scala.compat.java8.FutureConverters

import cats.effect.kernel.Async
import cats.effect.unsafe.{ IORuntime, IORuntimeBuilder, Scheduler }

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.future.FutureKt
import kotlinx.coroutines.CoroutineScopeKt
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers

object KotlinInterop extends KotlinInterop

trait KotlinInterop {

  final def completableFutureFromCoroutine[A](callCrt: Continuation[_ >: A] => AnyRef): CompletableFuture[A] = {
    FutureKt.future(
      CoroutineScopeKt.CoroutineScope(EmptyCoroutineContext.INSTANCE),
      EmptyCoroutineContext.INSTANCE,
      CoroutineStart.DEFAULT,
      (_, cont) => callCrt(cont),
    )
  }

  final def scalaFutureFromCoroutine[A](callCrt: Continuation[_ >: A] => AnyRef): Future[A] = {
    FutureConverters.toScala(this.completableFutureFromCoroutine[A](callCrt))
  }

  final def faFromCoroutine[F[_], A](callCrt: Continuation[_ >: A] => AnyRef)(implicit F: Async[F]): F[A] = {
    F.fromCompletableFuture(F.delay {
      this.completableFutureFromCoroutine[A](callCrt)
    })
  }

  final def ioRuntimeFromCoroutineDispatchers(): IORuntime = {
    val defaultDispatcher = Dispatchers.getDefault()
    val blockingDispatcher = Dispatchers.getIO()
    IORuntimeBuilder()
      .setCompute(new ExecutionContext {
        final override def reportFailure(cause: Throwable): Unit =
          cause.printStackTrace()
        final override def execute(runnable: Runnable): Unit =
          defaultDispatcher.dispatch(EmptyCoroutineContext.INSTANCE, runnable)
      }, () => ())
      .setBlocking(new ExecutionContext {
        final override def reportFailure(cause: Throwable): Unit =
          cause.printStackTrace()
        final override def execute(runnable: Runnable): Unit =
          blockingDispatcher.dispatch(EmptyCoroutineContext.INSTANCE, runnable)
      }, () => ())
      .setScheduler(new Scheduler {
        private[this] val delayInstance =
          kotlinx.coroutines.DelayKt.getDelay(EmptyCoroutineContext.INSTANCE)
        final override def sleep(delay: FiniteDuration, task: Runnable): Runnable = {
          val canceller = delayInstance.invokeOnTimeout(delay.toMillis, task, EmptyCoroutineContext.INSTANCE)
          canceller.dispose _
        }
        final override def nowMillis(): Long =
          System.currentTimeMillis()
        final override def monotonicNanos(): Long =
          System.nanoTime()
      }, () => ())
      .build()
  }
}
