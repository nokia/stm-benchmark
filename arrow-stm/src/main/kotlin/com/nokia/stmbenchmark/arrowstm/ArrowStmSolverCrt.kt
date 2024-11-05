/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark.arrowstm

import scala.Tuple2
import scala.collection.immutable.List
import scala.collection.immutable.Nil
import scala.collection.immutable.Map
import scala.collection.mutable.Builder

import arrow.fx.stm.STM
import arrow.fx.stm.atomically

import com.nokia.stmbenchmark.common.Board
import com.nokia.stmbenchmark.common.Point
import com.nokia.stmbenchmark.common.Route
import com.nokia.stmbenchmark.common.BoolMatrix
import com.nokia.stmbenchmark.common.Solver

class ArrowStmSolverCrt(internal val parLimit: Int) {

  suspend fun solve(board: Board.Normalized): Solver.Solution {
    val obstructed = BoolMatrix.obstructedFromBoard(board)

    val depth = atomically {
      newTMatrix<Int>(board.height(), board.width(), 0)
    }

    if (parLimit == 1) {
      val itr = board.routes().iterator()
      var solvedRoutes: Builder<Tuple2<Route, List<Point>>, List<Tuple2<Route, List<Point>>>> = List.newBuilder()
      while (itr.hasNext()) {
        val route = itr.next()
        val solution = solveOneRoute(board, obstructed, depth, route)
        solvedRoutes.addOne(Tuple2.apply(route, solution))
      }
      // we're cheating here with ev = null, but we know that it's correct:
      return Solver.Solution(solvedRoutes.result().toMap(null))
    } else {
      throw Exception("todo")
    }
  }

  internal suspend fun solveOneRoute(
    board: Board.Normalized,
    obstructed: BoolMatrix,
    depth: TMatrix<Int>,
    route: Route
  ): List<Point> {
    return atomically {
      // TODO: debug(s"Solving $route")
      val cost = expand(board, obstructed, depth, route)
      // TODO: val costStr = cost.debug(debug = log)(i => f"$i%2s", txn)
      // TODO: debug("Cost after `expand`:\n" + costStr)
      val solution = solve(board, route, cost)
      // TODO: debug(s"Solution:\n" + board.debugSolution(Map(route -> solution), debug = log))
      lay(depth, solution)
      solution
    }
  }

  internal fun STM.expand(
    board: Board.Normalized,
    obstructed: BoolMatrix,
    depth: TMatrix<Int>,
    route: Route
  ): TMatrix<Int> {
    val startPoint = route.a()
    val endPoint = route.b()

    val cost = newTMatrix(depth.height, depth.width, 0)
    cost.run { set(startPoint.y(), startPoint.x(), 1) }

    var wavefront = listOf(startPoint)

    var go = true
    while (go) {
      val newWavefront = mutableListOf<Point>()
      for (point in wavefront) {
        val pointCost = cost.run { get(point.y(), point.x()) }
        val itr = board.adjacentPoints(point).iterator()
        while (itr.hasNext()) {
          val adjacent = itr.next()
          if (obstructed.apply(adjacent.y(), adjacent.x()) && (adjacent != endPoint)) {
            // can't go in that direction
          } else {
            val currentCost = cost.run { get(adjacent.y(), adjacent.x()) }
            val newCost = pointCost + Board.cost(depth.run { get(adjacent.y(), adjacent.x()) })
            if ((currentCost == 0) || (newCost < currentCost)) {
              cost.run { set(adjacent.y(), adjacent.x(), newCost) }
              newWavefront += adjacent
            } else {
              // not better
            }
          }
        }
      }

      if (newWavefront.isEmpty()) {
        throw Solver.Stuck()
      } else {
        val costAtRouteEnd = cost.run { get(endPoint.y(), endPoint.x()) }
        if (costAtRouteEnd > 0) {
          val newCosts = newWavefront.map { marked -> cost.run { get(marked.y(), marked.x()) } }
          val minimumNewCost = newCosts.min()
          if (costAtRouteEnd < minimumNewCost) {
            // no new location has lower cost than the
            // cost currently at the route end, so
            // no reason to continue:
            go = false
          } else {
            // continue with the new wavefront:
            wavefront = newWavefront
          }
        } else {
          // continue with the new wavefront:
          wavefront = newWavefront
        }
      }
    }

    return cost
  }

  internal fun STM.solve(
    board: Board.Normalized,
    route: Route,
    cost: TMatrix<Int>
  ): List<Point> {
    // we're going *back* from the route end:
    val startPoint = route.b()
    val endPoint = route.a()
    var solution: List<Point> = Nil.prepended(startPoint)
    while (solution.head() != endPoint) {
      val adjacent = board.adjacentPoints(solution.head())
      val costs: List<Tuple2<Point, Int>> = adjacent.map<Tuple2<Point, Int>> { a ->
        val aCost = cost.run { get(a.y(), a.x()) }
        Tuple2.apply(a, aCost)
      }
      val lowestCost: Tuple2<Point, Int> = minBySecond(nonZero(costs))
      solution = solution.prepended(lowestCost._1()) as List<Point> // cast, because kotlin can't infer
    }

    return solution
  }

  internal fun nonZero(lst: List<Tuple2<Point, Int>>): List<Tuple2<Point, Int>> {
    // we have to cast here, because kotlin can't infer for scala `filter`:
    return lst.filter { (it as Tuple2<Point, Int>)._2() != 0 } as List<Tuple2<Point, Int>>
  }

  internal fun minBySecond(lst: List<Tuple2<Point, Int>>): Tuple2<Point, Int> {
    // we just rewrite scala `minBy`, because can't use `Ordering` from kotlin:
    val itr = lst.iterator()
    var min = itr.next()
    while (itr.hasNext()) {
      val n = itr.next()
      if (n._2() < min._2()) {
        min = n
      }
    }
    return min
  }

  internal fun STM.lay(depth: TMatrix<Int>, solution: List<Point>): Unit {
    val itr = solution.iterator()
    while (itr.hasNext()) {
      val point = itr.next()
      val ov: Int = depth.run { get(point.y(), point.x()) }
      depth.run { set(point.y(), point.x(), ov + 1) }
    }
  }
}
