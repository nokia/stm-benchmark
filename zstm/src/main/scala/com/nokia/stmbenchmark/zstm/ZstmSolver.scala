/*
 * © 2023-2025 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package zstm

import zio.{ UIO, Task, ZIO }
import zio.stm.{ STM, USTM, TaskSTM, ZSTM }

import common.{ Solver, Board, Point, Route, BoolMatrix }

object ZstmSolver {

  def apply(parLimit: Int, log: Boolean): UIO[Solver[Task]] = {

    def debug(msg: String): USTM[Unit] = {
      if (log) STM.unit.map { _ => println(msg) }
      else STM.unit
    }

    def debugF(msg: String): Task[Unit] = {
      if (log) ZIO.console.flatMap { c => c.printLine(msg) }
      else ZIO.unit
    }

    ZIO.succeed {
      new Solver[Task] {

        final override def solve(board: Board.Normalized): Task[Solver.Solution] = {
          val obstructed = BoolMatrix.obstructedFromBoard(board)

          def solveOneRoute(depth: TMatrix[Int], route: Route): Task[List[Point]] = {
            val txn = for {
              _ <- if (log) debug(s"Solving $route") else ZSTM.unit
              cost <- expand(depth, route)
              costStr <- cost.debug(debug = log)(i => f"$i%2s")
              _ <- debug("Cost after `expand`:\n" + costStr)
              solution <- solve(route, cost)
              _ <- debug(s"Solution:\n" + board.debugSolution(Map(route -> solution), debug = log))
              _ <- lay(depth, solution)
            } yield solution
            txn.commit
          }

          def expand(depth: TMatrix[Int], route: Route): TaskSTM[TMatrix[Int]] = {
            val startPoint = route.a
            val endPoint = route.b

            TMatrix[Int](depth.height, depth.width, 0).flatMap { cost =>
              cost.set(startPoint.y, startPoint.x, 1).flatMap { _ =>

                def go(wavefront: List[Point]): TaskSTM[List[Point]] = {
                  val mkNewWf = ZSTM.foreach(wavefront) { point =>
                    cost(point.y, point.x).flatMap { pointCost =>
                      ZSTM.foreach(board.adjacentPoints(point)) { adjacent =>
                        if (obstructed(adjacent.y, adjacent.x) && (adjacent != endPoint)) {
                          // can't go in that direction
                          ZSTM.succeed(Nil)
                        } else {
                          cost(adjacent.y, adjacent.x).flatMap { currentCost =>
                            depth(adjacent.y, adjacent.x).flatMap { d =>
                              val newCost = pointCost + Board.cost(d)
                              if ((currentCost == 0) || (newCost < currentCost)) {
                                cost.set(adjacent.y, adjacent.x, newCost).as(adjacent :: Nil)
                              } else {
                                ZSTM.succeed(Nil)
                              }
                            }
                          }
                        }
                      }.map(_.flatten)
                    }
                  }.map(_.flatten)

                  mkNewWf.flatMap { newWavefront =>
                    if (newWavefront.isEmpty) {
                      ZSTM.die(new Solver.Stuck)
                    } else {
                      cost(endPoint.y, endPoint.x).flatMap { costAtRouteEnd =>
                        if (costAtRouteEnd > 0) {
                          ZSTM.foreach(newWavefront) { marked =>
                            cost(marked.y, marked.x)
                          }.flatMap { newCosts =>
                            val minimumNewCost = newCosts.min
                            if (costAtRouteEnd < minimumNewCost) {
                              // no new location has lower cost than the
                              // cost currently at the route end, so
                              // no reason to continue:
                              ZSTM.succeed(newWavefront)
                            } else {
                              // continue with the new wavefront:
                              go(newWavefront)
                            }
                          }
                        } else {
                          // continue with the new wavefront:
                          go(newWavefront)
                        }
                      }
                    }
                  }
                }

                go(startPoint :: Nil).as(cost)
              }
            }
          }

          def solve(route: Route, cost: TMatrix[Int]): TaskSTM[List[Point]] = {
            // we're going *back* from the route end:
            val startPoint = route.b
            val endPoint = route.a
            ZSTM.iterate(List(startPoint))(cont = { solution => solution.head != endPoint }) { solution =>
              val adjacent = board.adjacentPoints(solution.head)
              ZSTM.foreach(adjacent) { a =>
                cost(a.y, a.x).map(a -> _)
              }.map { costs =>
                val lowestCost = costs.filter(_._2 != 0).minBy(_._2)
                lowestCost._1 :: solution
              }
            }
          }

          def lay(depth: TMatrix[Int], solution: List[Point]): TaskSTM[Unit] = {
            ZSTM.foreachDiscard(solution) { point =>
              depth.modify(point.y, point.x, _ + 1)
            }
          }

          TMatrix.apply[Int](
            h = board.height,
            w = board.width,
            initial = 0,
          ).commit.flatMap { depth =>
            val pl = java.lang.Math.max(1, java.lang.Math.min(parLimit, board.numberOrRoutes))
            val tasks = board.routes.map { route =>
              solveOneRoute(depth, route).map(route -> _)
            }
            val solveAll = if (pl == 1) {
              ZIO.mergeAll(tasks)(zero = Map.empty[Route, List[Point]]) { _ + _ }
            } else {
              ZIO.withParallelism(pl) {
                ZIO.mergeAllPar(tasks)(zero = Map.empty[Route, List[Point]]) { _ + _ }
              }
            }
            solveAll.flatMap { solution =>
              debugF("Full solution:\n" + board.debugSolution(solution, debug = log)).as(Solver.Solution(solution))
            }
          }
        }
      }
    }
  }
}
