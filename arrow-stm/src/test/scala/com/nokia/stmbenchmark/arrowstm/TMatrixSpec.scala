/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package arrowstm

import munit.FunSuite

final class TMatrixSpec extends FunSuite with KotlinInterop {

  private[this] val tmt = new TMatrixTest

  test("TMatrix test1") {
    scalaFutureFromCoroutine(tmt.test1)
  }

  test("TMatrix test2") {
    scalaFutureFromCoroutine(tmt.test2)
  }
}
