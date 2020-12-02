/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.js.engine

import kotlin.concurrent.thread

private val isWindows = "win" in System.getProperty("os.name").toLowerCase()

private fun getEngineExecutablePath(engineName: String, engineVersion: String) =
    System.getProperty("user.home") +
            "/.jsvu/$engineName-$engineVersion" +
            if (isWindows) ".cmd" else ""

sealed class JsvuBasedScriptEngine(
    engineName: String,
    engineVersion: String
) : ProcessBasedScriptEngine(getEngineExecutablePath(engineName, engineVersion))

class ScriptEngineV8 : JsvuBasedScriptEngine("v8", "8.1.307")

fun run(id: Int) {
    val vm = ScriptEngineV8()
    var i = 0
    var t = System.currentTimeMillis()
    while (true) {
        val s = System.currentTimeMillis()
        vm.eval("1")
        val diff = System.currentTimeMillis() - s
        if (diff > 300) {
            println("$$$$id: $i $diff")
        }
        if (diff > 400) {
            println(vm.stamps.joinToString("\n", prefix = "{{{\n", postfix = "}}}\n") { (m, t) -> "$t\t$m" })
        }
        if (System.currentTimeMillis() - t > 5000) {
            println("%$id: $i")
            t = System.currentTimeMillis()
        }

        vm.reset()
        i++
    }
}

fun main() {
    repeat(15) {
        thread {
            run(it)
        }
    }
}
