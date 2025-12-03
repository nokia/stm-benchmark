/*
 * © 2023-2025 Nokia
 * Licensed under the Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nokia.stmbenchmark
package common

trait MunitUtilsPlatform {

  final def isJvm: Boolean = true

  final def isJs: Boolean = false

  final def isNative: Boolean = false
}
