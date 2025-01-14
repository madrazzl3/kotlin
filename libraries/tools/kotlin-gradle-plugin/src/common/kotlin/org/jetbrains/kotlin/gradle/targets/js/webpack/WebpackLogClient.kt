/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.webpack

import org.gradle.api.logging.Logger
import org.jetbrains.kotlin.gradle.internal.LogType
import org.jetbrains.kotlin.gradle.internal.TeamCityMessageCommonClient

class WebpackLogClient(
    clientType: LogType,
    log: Logger
) : TeamCityMessageCommonClient(clientType, log) {
    override fun regularText(text: String) {
        if (WEBPACK_INITIAL_REGEX.matches(text)) {
            printMessage(text, LogType.LIFECYCLE)
        } else {
            super.regularText(text)
        }
    }

    override fun printMessage(text: String, type: LogType?) {
        if (WEBPACK_COMPILED_REGEX.matches(text)) {
            super.printMessage(text, LogType.LIFECYCLE)
        } else {
            super.printMessage(text, type)
        }
    }

    companion object {
        internal val WEBPACK_COMPILED_REGEX = "webpack (.+) compiled (.+) in .+\\s".toRegex()
        internal val WEBPACK_INITIAL_REGEX = "<i> \\[.+] .+\\s".toRegex()
    }
}