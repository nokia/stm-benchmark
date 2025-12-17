/*
 * © 2023-2025 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package choam
package txn

import cats.effect.IO
import cats.effect.kernel.Resource

import dev.tauri.choam.ChoamRuntime
import dev.tauri.choam.stm.Transactive

import common.JvmCeIoSolverSpec
import common.Solver

final class TxnSolverSpec extends TxnSolverSpecBase {
  protected[this] def mkSolver(rt: ChoamRuntime, numCpu: Int)(implicit ar: Transactive[IO]): IO[Solver[IO]] =
    TxnSolver[IO](parLimit = numCpu, log = false)
}

trait TxnSolverSpecBase extends JvmCeIoSolverSpec with rxn.RxnSolverSpecBasePlatform {

  protected[this] def mkSolver(rt: ChoamRuntime, numCpu: Int)(implicit ar: Transactive[IO]): IO[Solver[IO]]

  protected[this] final override def solverRes: Resource[IO, Solver[IO]] = {
    Resource.eval(IO { Runtime.getRuntime().availableProcessors() }).flatMap { numCpu =>
      ChoamRuntime.make[IO].flatMap { rt =>
        Transactive.from[IO](rt).flatMap { implicit ar =>
          Resource.eval(this.mkSolver(rt, numCpu)(using ar))
        }
      }
    }
  }

  testFromResource("four_crosses.txt".tag(Verbose))
  testFromResource("testBoard.txt".tag(Verbose))
  testFromResource("sparseshort_mini.txt")
  testFromResource("sparseshort.txt")
  testFromResource("sparselong_mini.txt")
  testFromResource("sparselong.txt", restrict = if (isJvm) 0 else 3)

  if (isJvm) { // TODO: SN: too big boards(?)
    testFromResource("mainboard.txt", restrict = 3) // unrestricted takes approx. 8 mins
    testFromResource("memboard.txt", restrict = 2) // unrestricted takes approx. 4 mins
  }

  override def afterEach(context: AfterEach): Unit = {
    printJmxInfo()
  }
}
