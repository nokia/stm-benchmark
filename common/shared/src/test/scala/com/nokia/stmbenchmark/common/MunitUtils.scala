/*
 * © 2023-2025 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package common

import java.util.concurrent.ThreadLocalRandom

import munit.{ Tag, Location, TestOptions }

trait MunitUtils { this: munit.FunSuite =>

  private[this] final case class ExpectedResult(
    maxDepth: Int,
    totalCost: Int,
  )

  protected val DontCare = -2

  /** board -> (restrict -> expectedResult) */
  private[this] val expectedDepthsAndCosts = Map[String, Map[Int, ExpectedResult]](
    "empty.txt" -> Map(0 -> ExpectedResult(0, 0)),
    "minimal.txt" -> Map(0 -> ExpectedResult(2, 24)),
    "four_crosses.txt" -> Map(
      0 -> ExpectedResult(2, 28),
    ),
    "testBoard.txt" -> Map(0 -> ExpectedResult(4, 3307)),
    "sparseshort_mini.txt" -> Map(0 -> ExpectedResult(1, 990)),
    "sparseshort.txt" -> Map(
      0 -> ExpectedResult(1, 9251),
      1 -> ExpectedResult(1, 4625),
      2 -> ExpectedResult(1, 2312),
    ),
    "sparselong_mini.txt" -> Map(
      0 -> ExpectedResult(1, 1810),
    ),
    "sparselong.txt" -> Map(
      0 -> ExpectedResult(1, 16849),
      1 -> ExpectedResult(1, 8134),
      2 -> ExpectedResult(1, 4067),
      3 -> ExpectedResult(1, 1743),
    ),
    "mainboard.txt" -> Map(
      0 -> ExpectedResult(3, 174128),
      1 -> ExpectedResult(3, 81015),
      2 -> ExpectedResult(2, 39100),
      3 -> ExpectedResult(2, 20713),
      4 -> ExpectedResult(2, 10786),
      5 -> ExpectedResult(2, 5035),
      6 -> ExpectedResult(2, 3376),
      7 -> ExpectedResult(1, 1271),
      8 -> ExpectedResult(1, 529),
    ),
    "memboard.txt" -> Map(
      0 -> ExpectedResult(3, 162917),
      1 -> ExpectedResult(3, 77378),
      2 -> ExpectedResult(3, 36800),
      4 -> ExpectedResult(2, 9008),
      5 -> ExpectedResult(2, 4074),
      6 -> ExpectedResult(2, 2783),
    ),
  )

  protected val Verbose = new Tag("Verbose")

  protected def println(s: String): Unit = { // subclasses may override
    Predef.println(s)
  }

  protected def normalizeAndRestrict(board: Board, restrict: Int): Board.Normalized = {
    val seed = if (board.routes.size > 240) {
      // The expected results for the larger
      // boards are heavily dependent on how
      // routes are removed and shuffled, so
      // we don't vary these:
      42L
    } else {
      ThreadLocalRandom.current().nextLong()
    }
    val rng = new scala.util.Random(seed)
    board.normalize(rng.nextLong()).restrict(restrict, rng.nextLong())
  }

  protected def checkSolutionInternal(
    testOpts: TestOptions,
    board: Board.Normalized,
    solution: Solver.Solution,
    expMaxDepth: Int = -1,
    expTotalCost: Int = -1,
  )(implicit loc: Location): Unit = {
    val debug = testOpts.tags.contains(Verbose)
    if (debug) {
      // print the whole board:
      val boardRepr = board.debugSolution(solution.routes, debug = true)
      println(boardRepr)
    }
    // print stats:
    val stats = Board.debugSolutionStats(solution, debug = true, indent = "  ")
    println(s"${testOpts.name}\n${stats}")
    // check solution validity:
    this.assert(board.isSolutionValid(solution.routes))
    // check expected results:
    val expMd = expMaxDepth match {
      case -1 => expectedDepthsAndCosts(testOpts.name).get(board.restricted).map(_.maxDepth)
      case md => Some(md)
    }
    expMd match {
      case Some(expMd) =>
        this.assert(solution.maxDepth <= expMd, s"maxDepth should be <= ${expMd}, but was ${solution.maxDepth}")
      case None =>
        fail(s"Not found maxDepth for ${testOpts.name} (restrict = ${board.restricted})")
    }
    val expTc = expTotalCost match {
      case -1 => expectedDepthsAndCosts(testOpts.name).get(board.restricted).map(_.totalCost)
      case tc => Some(tc)
    }
    expTc match {
      case Some(expTc) =>
        // total cost is not always the same
        // (due to nondeterminism), but should
        // be approximately equal:
        val maxTc = 1.05 * expTc.toDouble
        val minTc = 0.95 * expTc.toDouble
        this.assert(
          (solution.totalCost <= maxTc) && (solution.totalCost >= minTc),
          s"totalCost should be between ${minTc} and ${maxTc}, but was ${solution.totalCost}"
        )
      case None =>
        fail(s"Not found totalCost for ${testOpts.name} (restrict = ${board.restricted})")
    }
  }
}
