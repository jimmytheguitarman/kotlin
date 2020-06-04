/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.js.engine

import com.intellij.openapi.util.text.StringUtil

private val LINE_SEPARATOR = System.getProperty("line.separator")!!
private val END_MARKER = "<END>$LINE_SEPARATOR"

abstract class ProcessBasedScriptEngine(
    private val executablePath: String
) : ScriptEngine {

    private var process: Process? = null
    private val buffer = ByteArray(1024)

    val stamps = mutableListOf<Pair<String, Long>>()

    var last = 0L

    fun stamp(m: String) {
        val diff = System.currentTimeMillis() - last
        stamps.add(m to diff)
        last = System.currentTimeMillis()
    }

    override fun eval(script: String): String {
        stamp("start eval")
        val vm = getOrCreateProcess()
        stamp("got vm")

        val stdin = vm.outputStream
        val stdout = vm.inputStream
        val stderr = vm.errorStream

        if (!vm.isAlive) {
            println("$$$ ALARM: process is not alive")
        }

        val writer = stdin.writer()
        writer.write(StringUtil.convertLineSeparators(script, "\\n") + "\n")
        writer.flush()

        stamp("sent message")

        val out = StringBuilder()

        while (vm.isAlive) {
            val n = stdout.available()
            if (n == 0) continue
            stamp("have message")

            val count = stdout.read(buffer)

            stamp("got message")

            val s = String(buffer, 0, count)
            out.append(s)

            if (out.endsWith(END_MARKER)) break
        }

        stamp("finish reading")

        if (stderr.available() > 0) {
            stamp("reading stderr")
            val err = StringBuilder()

            while (vm.isAlive && stderr.available() > 0) {
                val count = stderr.read(buffer)
                val s = String(buffer, 0, count)
                err.append(s)
                // TODO check and remove
                if (count <= 0 && stderr.available() > 0) {
                    println("$$$ AAA")
                }
//                if (count <= 0) break
            }

            stamp("finish reading stderr")

            error("ERROR:\n$err\nOUTPUT:\n$out")
        }

        stamp("end eval")
        return out.removeSuffix(END_MARKER).removeSuffix(LINE_SEPARATOR).toString()
    }

    override fun loadFile(path: String) {
        eval("load('${path.replace('\\', '/')}');")
    }

    override fun release() {
        process?.destroy()
        process = null
    }

    override fun saveState() {
        eval("!saveState")
    }

    override fun restoreState() {
        eval("!restoreState")
    }

    private fun getOrCreateProcess(): Process {
        val p = process

        if (p != null && p.isAlive) return p

        process = null

        val builder = ProcessBuilder(
            executablePath,
            "js/js.engines/src/org/jetbrains/kotlin/js/engine/repl.js",
        )
        return builder.start().also {
            process = it
        }
    }
}
