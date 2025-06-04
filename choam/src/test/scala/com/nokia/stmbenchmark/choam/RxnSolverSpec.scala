/*
 * © 2023-2025 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package choam

import scala.annotation.unused

import cats.syntax.all._
import cats.effect.IO
import cats.effect.kernel.Resource

import dev.tauri.choam.ChoamRuntime
import dev.tauri.choam.async.AsyncReactive

import common.JvmCeIoSolverSpec
import common.Solver

final class RxnSolverSpec extends RxnSolverSpecBase {
  protected[this] def mkSolver(rt: ChoamRuntime, numCpu: Int)(implicit ar: AsyncReactive[IO]): IO[Solver[IO]] =
    RxnSolver[IO](parLimit = numCpu, log = false, strategy = RxnSolver.sleepStrategy)
}

final class ErRxnSolverSpec extends RxnSolverSpecBase {
  protected[this] def mkSolver(rt: ChoamRuntime, numCpu: Int)(implicit ar: AsyncReactive[IO]): IO[Solver[IO]] =
    ErRxnSolver[IO](parLimit = numCpu, log = false, strategy = RxnSolver.sleepStrategy)
}

final class ErtRxnSolverSpec extends RxnSolverSpecBase {
  protected[this] def mkSolver(rt: ChoamRuntime, numCpu: Int)(implicit ar: AsyncReactive[IO]): IO[Solver[IO]] =
    ErtRxnSolver[IO](parLimit = numCpu, log = false, strategy = RxnSolver.sleepStrategy)
}

final class ImpRxnSolverSpec extends RxnSolverSpecBase {
  protected[this] def mkSolver(rt: ChoamRuntime, numCpu: Int)(implicit @unused ar: AsyncReactive[IO]): IO[Solver[IO]] =
    ImpRxnSolver[IO](rt = rt, parLimit = numCpu, log = false) // TODO: sleepStrategy
}

trait RxnSolverSpecBase extends JvmCeIoSolverSpec {

  protected[this] def mkSolver(rt: ChoamRuntime, numCpu: Int)(implicit ar: AsyncReactive[IO]): IO[Solver[IO]]

  protected[this] final override def solverRes: Resource[IO, Solver[IO]] = {
    Resource.eval(IO { Runtime.getRuntime().availableProcessors() }).flatMap { numCpu =>
      ChoamRuntime[IO].flatMap { rt =>
        AsyncReactive.from[IO](rt).flatMap { implicit ar =>
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
  testFromResource("sparselong.txt")
  testFromResource("mainboard.txt", restrict = 3) // unrestricted takes approx. 10 mins
  testFromResource("memboard.txt", restrict = 2) // unrestricted takes approx. 6 mins

  override def afterEach(context: AfterEach): Unit = {
    printJmxInfo()
  }

  private def printJmxInfo(): Unit = {
    val mbs = java.lang.management.ManagementFactory.getPlatformMBeanServer()
    val names = mbs.queryNames(
      new javax.management.ObjectName("dev.tauri.choam.stats:type=EmcasJmxStats*"),
      null
    )

    names.forEach { objName =>

      def getAttr(attrName: String): Option[(String, AnyRef)] = {
        Either
          .catchOnly[javax.management.AttributeNotFoundException](mbs.getAttribute(objName, attrName))
          .toOption
          .map(attrName -> _)
      }

      val attrs = List(
        "Commits",
        "McasAttempts",
        "CyclesDetected",
        "AvgCyclesPerMcasAttempt",
      ).sorted

      for ((name, value) <- attrs.map(getAttr).collect { case Some(kv) => kv }) {
        println(s"  ${name}: ${value}")
      }

      mbs.invoke(objName, "checkConsistency", Array.empty[AnyRef], Array.empty[String]) match {
        case null =>
          // OK
        case errMsg =>
          println(errMsg.toString)
      }
    }
  }
}
