/*
 * © 2023-2025 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package choam
package rxn

import cats.syntax.all._

trait RxnSolverSpecBasePlatform {

  protected def printJmxInfo(): Unit = {
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
