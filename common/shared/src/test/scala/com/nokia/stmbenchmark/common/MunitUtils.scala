/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package common

import munit.{ Tag, Location, TestOptions }

trait MunitUtils { this: munit.FunSuite =>

  protected val DontCare = -2

  private[this] val expectedDepthsAndCosts = Map[String, (Int, Int)](
    "minimal.txt" -> (2, 24),
    "testBoard.txt" -> (3, 3307),
    "sparseshort.txt" -> (1, 9251),
    "sparselong_micro.txt" -> (DontCare, DontCare), // TODO
    "sparselong_mini.txt" -> (DontCare, DontCare), // TODO
    "sparselong.txt" -> (1, 16849),
    "mainboard.txt" -> (3, 174128),
    "memboard.txt" -> (3, 162917), // TODO
  )

  protected val Verbose = new Tag("Verbose")

  protected def println(s: String): Unit = { // subclasses may override
    Predef.println(s)
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
    if (board.restricted == 0) {
      val expMd = expMaxDepth match {
        case -1 => expectedDepthsAndCosts(testOpts.name)._1
        case md => md
      }
      if (expMd != DontCare) {
        this.assertEquals(solution.maxDepth, expMd)
      }
      val expTc = expTotalCost match {
        case -1 => expectedDepthsAndCosts(testOpts.name)._2
        case tc => tc
      }
      if (expTc != DontCare) {
        // total cost is not always the same
        // (due to nondeterminism), but should
        // be approximately equal:
        val maxTc = 1.05 * expTc.toDouble
        val minTc = 0.95 * expTc.toDouble
        this.assert(
          (solution.totalCost <= maxTc) && (solution.totalCost >= minTc),
          s"totalCost should be between ${minTc} and ${maxTc}, but was ${solution.totalCost}"
        )
      }
    } // else: restricted board, results should be different
  }
}
