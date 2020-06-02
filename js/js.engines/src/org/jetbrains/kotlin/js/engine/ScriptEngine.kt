/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.js.engine

interface ScriptEngine {
    fun eval(script: String): String
    // TODO Add API to load few files at once
    fun loadFile(path: String)
    fun release()

    fun saveState()
    fun restoreState()
}

interface ScriptEngineWithTypedResult : ScriptEngine {
    fun <R> evalWithTypedResult(script: String): R
}
