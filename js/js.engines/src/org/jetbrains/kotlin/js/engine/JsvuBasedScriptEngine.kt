/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.js.engine

private val isWindows = "win" in System.getProperty("os.name").toLowerCase()

private fun getEngineExecutablePath(engineName: String, engineVersion: String? = null) =
    System.getProperty("user.home") +
            "/.jsvu/$engineName" +
            (if (engineVersion.isNullOrBlank()) "-$engineVersion" else "") +
            if (isWindows) ".cmd" else ""

sealed class JsvuBasedScriptEngine(
    engineName: String,
    engineVersion: String
) : ProcessBasedScriptEngine(getEngineExecutablePath(engineName, engineVersion))

class ScriptEngineV8 : JsvuBasedScriptEngine("v8", "8.1.307")
