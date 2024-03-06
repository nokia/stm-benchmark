/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package common

import java.util.concurrent.atomic.AtomicReference

abstract class Solver[F[_]] {
  def solve(board: Board.Normalized): F[Solver.Solution]
}

object Solver {

  final case class Solution(routes: Map[Route, List[Point]]) {

    def routeCount: Int =
      this.routes.size

    // These are lazy, because we don't want to do this
    // computation when we're running benchmarks:

    private[this] val costAndDepth: AtomicReference[(Int, Int)] =
      new AtomicReference

    /** How much is the overall cost of the solution? */
    def totalCost: Int =
      this.getOrComputeCostAndDepth()._1

    /** What's the maximum depth used? */
    def maxDepth: Int =
      this.getOrComputeCostAndDepth()._2

    private[this] def getOrComputeCostAndDepth(): (Int, Int) = {
      this.costAndDepth.getOpaque() match {
        case null =>
          this.costAndDepth.get() match {
            case null =>
              this.computeAndStoreCostAndDepth()
            case cd =>
              cd
          }
        case cd =>
          cd
      }
    }

    private[this] def computeAndStoreCostAndDepth(): (Int, Int) = {
      val (depthMap, cost) = routes.values.foldLeft((Map.empty[Point, Int], 0)) { (state, route) =>
        route.foldLeft(state) { (state, point) =>
          val (depth, cost) = state
          val pointDepth = depth.getOrElse(point, 0)
          val newCost = cost + Board.cost(pointDepth)
          val newDepth = depth.updated(point, pointDepth + 1)
          (newDepth, newCost)
        }
      }

      val result = (cost, depthMap.values.max)
      this.costAndDepth.compareAndExchange(null, result) match {
        case null => result
        case other => other
      }
    }
  }

  final class Stuck(msg: String) extends Exception(msg) {
    def this() =
      this("solver is stuck")
  }
}
