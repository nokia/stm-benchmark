/*
 * © 2023-2025 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia

package object stmbenchmark {

  private[stmbenchmark] type tailrec = scala.annotation.tailrec

  private[stmbenchmark] def impossible(msg: String): Nothing =
    sys.error(msg)
}
