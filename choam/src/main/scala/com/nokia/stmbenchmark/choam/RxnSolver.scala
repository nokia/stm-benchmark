/*
 * © 2023-2024 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package choam

import scala.concurrent.duration._

import cats.data.{ Chain, NonEmptyChain }
import cats.syntax.all._
import cats.effect.syntax.all._
import cats.effect.Async
import cats.effect.std.Console

import dev.tauri.choam.{ Rxn, Axn }
import dev.tauri.choam.async.AsyncReactive

import common.{ Solver, Board, Point, Route, BoolMatrix }

object RxnSolver {

  private[stmbenchmark] val spinStrategy: Rxn.Strategy.Spin =
    Rxn.Strategy.Default

  private[stmbenchmark] val cedeStrategy: Rxn.Strategy = {
    Rxn.Strategy.cede(
      maxRetries = Rxn.Strategy.Default.maxRetries,
      maxSpin = Rxn.Strategy.Default.maxSpin,
      randomizeSpin = Rxn.Strategy.Default.randomizeSpin,
    )
  }

  private[stmbenchmark] val sleepStrategy: Rxn.Strategy = {
    Rxn.Strategy.sleep(
      maxRetries = Rxn.Strategy.Default.maxRetries,
      maxSpin = Rxn.Strategy.Default.maxSpin,
      randomizeSpin = Rxn.Strategy.Default.randomizeSpin,
      maxSleep = 100.millis, // FIXME
      randomizeSleep = true,
    )
  }

  def apply[F[_]](
    parLimit: Int,
    log: Boolean,
    strategy: Rxn.Strategy = Rxn.Strategy.Default,
  )(implicit F: Async[F]): F[Solver[F]] = {
    F.pure(new Solver[F] {

      private[this] implicit val reactive: AsyncReactive[F] =
        AsyncReactive.forAsync[F]

      private[this] val _c =
        Console.make[F]

      private[this] val runConfig: Rxn.Strategy =
        strategy

      private[this] final def debug(msg: String): Axn[Unit] = {
        if (log) Axn.unsafe.delay { println(msg) }
        else Rxn.unit
      }

      private[this] final def debugF(msg: String): F[Unit] = {
        if (log) _c.println(msg)
        else F.unit
      }

      final override def solve(board: Board.Normalized): F[Solver.Solution] = {
        val obstructed = BoolMatrix.obstructedFromBoard(board)

        def solveOneRoute(depth: RefMatrix[Int], route: Route): F[List[Point]] = {
          val act: Axn[List[Point]] = for {
            _ <- debug(s"Solving $route")
            cost <- expand(depth, route)
            costStr <- cost.debug(debug = log)(i => f"$i%2s")
            _ <- debug("Cost after `expand`:\n" + costStr)
            solution <- solve(route, cost)
            solutionList = solution.toList
            _ <- debug(s"Solution:\n" + board.debugSolution(Map(route -> solutionList), debug = log))
            _ <- lay(depth, solution)
          } yield solutionList
          reactive.applyAsync(act, null, runConfig)
        }

        def expand(depth: RefMatrix[Int], route: Route): Axn[RefMatrix[Int]] = {
          val startPoint = route.a
          val endPoint = route.b

          RefMatrix[Int](depth.height, depth.width, 0).flatMapF { cost =>
            cost(startPoint.y, startPoint.x).set.provide(1).flatMapF { _ =>

              def go(wavefront: Chain[Point]): Axn[Chain[Point]] = {
                val mkNewWf = wavefront.foldMapM[Axn, Chain[Point]] { point =>
                  cost(point.y, point.x).get.flatMapF { pointCost =>
                    board.adjacentPoints(point).foldMapM[Axn, Chain[Point]] { adjacent =>
                      if (obstructed(adjacent.y, adjacent.x) && (adjacent != endPoint)) {
                        // can't go in that direction
                        Rxn.pure(Chain.empty)
                      } else {
                        cost(adjacent.y, adjacent.x).get.flatMapF { currentCost =>
                          depth(adjacent.y, adjacent.x).get.flatMapF { d =>
                            val newCost = pointCost + Board.cost(d)
                            if ((currentCost == 0) || (newCost < currentCost)) {
                              cost(adjacent.y, adjacent.x).set.provide(newCost).as(Chain(adjacent))
                            } else {
                              Rxn.pure(Chain.empty)
                            }
                          }
                        }
                      }
                    }
                  }
                }

                mkNewWf.flatMap { newWavefront =>
                  if (newWavefront.isEmpty) {
                    Axn.unsafe.delay { throw new Solver.Stuck }
                  } else {
                    cost(endPoint.y, endPoint.x).get.flatMapF { costAtRouteEnd =>
                      if (costAtRouteEnd > 0) {
                        newWavefront.traverse { marked =>
                          cost(marked.y, marked.x).get
                        }.flatMapF { newCosts =>
                          val minimumNewCost = newCosts.minimumOption.get // TODO: partial function
                          if (costAtRouteEnd < minimumNewCost) {
                            // no new location has lower cost than the
                            // cost currently at the route end, so
                            // no reason to continue:
                            Rxn.pure(newWavefront)
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

              go(Chain(startPoint)).as(cost)
            }
          }
        }

        def solve(route: Route, cost: RefMatrix[Int]): Axn[NonEmptyChain[Point]] = {
          // we're going *back* from the route end:
          val startPoint = route.b
          val endPoint = route.a
          Rxn.monadInstance.iterateWhileM(NonEmptyChain(startPoint)) { solution =>
            val adjacent = board.adjacentPoints(solution.head)
            adjacent.traverse { a =>
              cost(a.y, a.x).get.map(a -> _)
            }.map { costs =>
              val lowestCost = costs.filter(_._2 != 0).minBy(_._2)
              lowestCost._1 +: solution
            }
          } (p = { solution => solution.head != endPoint })
        }

        def lay(depth: RefMatrix[Int], solution: NonEmptyChain[Point]): Axn[Unit] = {
          solution.traverse_ { point =>
            depth(point.y, point.x).update(_ + 1)
          }
        }

        RefMatrix.apply(
          h = board.height,
          w = board.width,
          initial = 0,
        ).run[F].flatMap { depth =>
          val solveOne = { (route: Route) =>
            solveOneRoute(depth, route).map(route -> _)
          }
          val solveInParallel = if (parLimit == 1) {
            board.routes.traverse(solveOne)
          } else {
            board.routes.parTraverseN(parLimit)(solveOne)
          }
          solveInParallel.flatMap { solutions =>
            val solution = Map(solutions: _*)
            debugF("Full solution:\n" + board.debugSolution(solution, debug = log)).as(Solver.Solution(solution))
          }
        }
      }
    })
  }
}
