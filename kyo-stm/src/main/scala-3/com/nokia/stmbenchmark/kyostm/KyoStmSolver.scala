/*
 * © 2023-2025 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package kyostm

import kyo.{ <, Abort, Async, Chunk, Sync, STM, Kyo, Schedule, Frame, Meter, Scope }

import common.{ Board, BoolMatrix, Point, Route, Solver }

object KyoStmSolver {

  val defaultRetrySchedule: Schedule = {
    // by default, kyo-stm seems to only
    // (re?)try transactions 20 times; but
    // that is enough to solve only the
    // smallest boards, so we configure
    // infinite retries here:
    STM.defaultRetrySchedule.forever
  }

  def apply(
    parLimit: Int,
    log: Boolean,
    retrySchedule: Schedule = defaultRetrySchedule,
  ): Solver[<[*, Async & Abort[Throwable]]] < Sync = Sync.defer {
    new Solver[<[*, Async & Abort[Throwable]]] {

      private[this] final def debug(msg: String): Unit < Sync = {
        if (log) Sync.defer { println(msg) }
        else ()
      }

      /** Like `kyo.Async.parallel`, but without the grouping */
      private[this] final def parallelN[A](parallelism: Int)(tasks: Seq[A < (Abort[Throwable] & Async)])(
        implicit frame: Frame
      ): Seq[A] < (Abort[Throwable] & Async) = {
        val scoped: Seq[A]< (Abort[Throwable] & Async & Scope) = Meter.initSemaphore(
          concurrency = parallelism,
          reentrant = false
        ).map { semaphore =>
          Async.collectAll(tasks.map { task =>
            semaphore.run(task)
          }, concurrency = Integer.MAX_VALUE)
        }
        Scope.run(scoped)
      }

      final override def solve(board: Board.Normalized): Solver.Solution < (Async & Abort[Throwable]) = {
        val obstructed = BoolMatrix.obstructedFromBoard(board)

        def solveOneRoute(depth: TMatrix[Int], route: Route): List[Point] < (Async & Abort[Throwable]) = {
          val txn: List[Point] < STM = (if (log) debug(s"Solving $route") else () : Unit < Sync).map { _ =>
            expand(depth, route).map { cost =>
              cost.debug(debug = log)(i => f"$i%2s").map { costStr =>
                debug("Cost after `expand`:\n" + costStr).map { _ =>
                  solve(route, cost).map { solution =>
                    debug(s"Solution:\n" + board.debugSolution(Map(route -> solution), debug = log)).map { _ =>
                      lay(depth, solution).map(_ => solution)
                    }
                  }
                }
              }
            }
          }

          STM.run(retrySchedule)(txn)
        }

        def expand(depth: TMatrix[Int], route: Route): TMatrix[Int] < STM = {
          val startPoint = route.a
          val endPoint = route.b

          TMatrix[Int](depth.height, depth.width, 0).map { cost =>
            cost.set(startPoint.y, startPoint.x, 1).map { _ =>

              def go(wavefront: Chunk[Point]): Chunk[Point] < STM = {
                val mkNewWf: Chunk[Point] < STM = Kyo.foreachConcat(Chunk.from(wavefront)) { point =>
                  cost(point.y, point.x).map { pointCost =>
                    Kyo.foreachConcat(Chunk.from(board.adjacentPoints(point))) { adjacent =>
                      if (obstructed(adjacent.y, adjacent.x) && (adjacent != endPoint)) {
                        // can't go in that direction
                        Chunk.empty[Point]
                      } else {
                        cost(adjacent.y, adjacent.x).map { currentCost =>
                          depth(adjacent.y, adjacent.x).map { d =>
                            val newCost = pointCost + Board.cost(d)
                            if ((currentCost == 0) || (newCost < currentCost)) {
                              cost.set(adjacent.y, adjacent.x, newCost).map(_ => Chunk(adjacent))
                            } else {
                              Chunk.empty
                            }
                          }
                        }
                      }
                    }
                  }
                }

                mkNewWf.map { newWavefront =>
                  if (newWavefront.isEmpty) {
                    Abort.panic(new Solver.Stuck)
                  } else {
                    cost(endPoint.y, endPoint.x).map { costAtRouteEnd =>
                      if (costAtRouteEnd > 0) {
                        Kyo.foreach(newWavefront) { marked =>
                          cost(marked.y, marked.x)
                        }.map { newCosts =>
                          val minimumNewCost = newCosts.min
                          if (costAtRouteEnd < minimumNewCost) {
                            // no new location has lower cost than the
                            // cost currently at the route end, so
                            // no reason to continue:
                            newWavefront
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

              go(Chunk(startPoint)).map(_ => cost)
            }
          }
        }

        def solve(route: Route, cost: TMatrix[Int]): List[Point] < STM = {
          // we're going *back* from the route end:
          val startPoint = route.b
          val endPoint = route.a

          def go(solution: List[Point]): List[Point] < STM = {
            if (solution.head == endPoint) {
              // we're done
              solution
            } else {
              val adjacent = board.adjacentPoints(solution.head)
              Kyo.foreach(adjacent) { a =>
                cost(a.y, a.x).map(a -> _)
              }.map { costs =>
                val lowestCost = costs.filter(_._2 != 0).minBy(_._2)
                go(lowestCost._1 :: solution)
              }
            }
          }

          go(List(startPoint))
        }

        def lay(depth: TMatrix[Int], solution: List[Point]): Unit < STM = {
          Kyo.foreachDiscard(solution) { point =>
            depth.modify(point.y, point.x, _ + 1)
          }
        }

        STM.run(retrySchedule)(TMatrix.apply[Int](h = board.height, w = board.width, initial = 0)).map { depth =>
          val pl = java.lang.Math.max(1, java.lang.Math.min(parLimit, board.numberOrRoutes))
          val solveOne = { (route: Route) =>
            solveOneRoute(depth, route).map(route -> _)
          }
          val solveAll = if (pl == 1) {
            Kyo.foreach(board.routes)(solveOne)
          } else {
            parallelN(parallelism = pl)(board.routes.map(solveOne))
          }
          solveAll.map { solutions =>
            val solution = Map(solutions: _*)
            debug("Full solution:\n" + board.debugSolution(solution, debug = log)).map { _ =>
              Solver.Solution(solution)
            }
          }
        }
      }
    }
  }
}
