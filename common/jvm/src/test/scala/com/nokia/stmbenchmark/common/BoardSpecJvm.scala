/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package common

import cats.effect.IO

import munit.CatsEffectSuite

final class BoardSpecJvm extends CatsEffectSuite {

  test("Board.fromResource") {
    def tst(resourceName: String): IO[Unit] = {
      Board.fromResource[IO](resourceName).flatMap { board =>
        IO(assert((board.height > 0) && (board.width > 0), clue = resourceName))
      }
    }

    IO.parSequenceN(4)(List(
      tst("four_crosses.txt"),
      tst("mainboard.txt"),
      tst("memboard.txt"),
      tst("minimal.txt"),
      tst("sparselong_mini.txt"),
      tst("sparselong.txt"),
      tst("sparseshort_mini.txt"),
      tst("sparseshort.txt"),
      tst("testBoard.txt"),
    ))
  }
}
