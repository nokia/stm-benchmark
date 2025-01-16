/*
 * © 2023-2025 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package common

import java.util.concurrent.ThreadLocalRandom

import scala.concurrent.duration._

import cats.effect.{ IO, Resource }

import fs2.Stream

import munit.{ CatsEffectSuite, Location, AnyFixture }

abstract class CeIoSolverSpec extends CatsEffectSuite with MunitUtils {

  protected[this] def solverRes: Resource[IO, Solver[IO]]

  private[this] val _solver = ResourceSuiteLocalFixture[Solver[IO]](
    name = "solver",
    resource = this.solverRes
  )

  override def munitFixtures: Seq[AnyFixture[_]] =
    this._solver +: super.munitFixtures

  protected[this] def solver: Solver[IO] =
    _solver()

  protected[this] def normalize(b: Board): Board.Normalized = {
    val seed = if (b.routes.size > 240) {
      42L
    } else {
      ThreadLocalRandom.current().nextLong()
    }
    b.normalize(seed)
  }

  protected final def debug(msg: String): IO[Unit] =
    IO.consoleForIO.println(msg)

  protected final def assertTsk(cond: Boolean)(implicit loc: Location): IO[Unit] =
    IO(assert(cond)(loc))

  override def munitIOTimeout =
    60.minutes

  test("empty.txt") {
    val b = this.normalize(Board.empty(10, 10))
    solver.solve(b).flatMap { solution =>
      IO { checkSolutionInternal("empty.txt", b, solution) }
    }
  }

  // https://github.com/chrisseaton/ruby-stm-lee-demo/blob/master/inputs/minimal.txt
  test("minimal.txt") { // TODO: run this small test repeatedly
    val s = Stream[IO, String](
      List(
        "B 10 10",
        "P 2 2",
        "P 7 2",
        "P 2 7",
        "P 7 7",
        "J 2 2 7 7",
        "J 7 2 2 7",
        "E",
        "",
      ).mkString("\n")
    )
    Board.fromStream(s).flatMap { board =>
      val b = this.normalize(board)
      solver.solve(b).flatMap { solution =>
        IO {
          checkSolutionInternal(
            "minimal.txt".tag(Verbose),
            b,
            solution,
          )
        }
      }
    }
  }
}
