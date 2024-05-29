/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package arrowstm

import java.util.concurrent.CompletableFuture

import scala.concurrent.Future
import scala.compat.java8.FutureConverters

import cats.effect.kernel.Async

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.future.FutureKt
import kotlinx.coroutines.CoroutineScopeKt
import kotlinx.coroutines.CoroutineStart

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
}
