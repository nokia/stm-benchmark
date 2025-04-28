/*
 * © 2023-2025 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package kyostm

import kyo.STM

import munit.FunSuite

final class TMatrixSpec extends FunSuite with KyoInteropMunit {

  testKyo("test1") {
    for {
      m <- STM.run(TMatrix(4, 4, "foo").map { m =>
        m.set(1, 1, "bar").map(_ => m)
      })
      v01 <- STM.run(m(0, 1))
      _ <- this.assertKyo(v01 == "foo")
      v11 <- STM.run(m(1, 1))
      _ <- this.assertKyo(v11 == "bar")
    } yield ()
  }
}
