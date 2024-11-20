/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark.benchmarks;

import scala.runtime.BoxedUnit;
import scala.Function0;
import scala.Function1;

import cats.effect.IO;
import cats.effect.unsafe.IORuntime;

final class UnsafeAccess {

  private UnsafeAccess() {}

  static final <A> void unsafeRunFiberWithoutFatalCb(
    IORuntime rt,
    IO<A> tsk,
    Function0<BoxedUnit> canceled,
    Function1<Throwable, BoxedUnit> failure,
    Function1<A, BoxedUnit> success
  ) {
    tsk.unsafeRunFiber(canceled, failure, success, false, rt);
  }
}
