/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.js.test

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.kotlin.js.engine.ScriptEngine
import org.jetbrains.kotlin.js.engine.ScriptEngineNashorn
import org.jetbrains.kotlin.js.engine.ScriptEngineV8
import org.junit.Assert

fun createScriptEngine(): ScriptEngine {
    return if (java.lang.Boolean.getBoolean("kotlin.js.useNashorn")) ScriptEngineNashorn() else ScriptEngineV8()
}

fun ScriptEngine.overrideAsserter() {
    eval("this['kotlin-test'].kotlin.test.overrideAsserter_wbnzx$(this['kotlin-test'].kotlin.test.DefaultAsserter);")
}

fun ScriptEngine.runTestFunction(
    testModuleName: String?,
    testPackageName: String?,
    testFunctionName: String,
    withModuleSystem: Boolean
): String? {
    var script = when {
        withModuleSystem -> BasicBoxTest.KOTLIN_TEST_INTERNAL + ".require('" + testModuleName!! + "')"
        testModuleName === null -> "this"
        else -> testModuleName
    }

    if (testPackageName !== null) {
        script += ".$testPackageName"
    }

    script += ".$testFunctionName()"

    return eval(script)
}

abstract class AbstractJsTestChecker {
    fun check(
        files: List<String>,
        testModuleName: String?,
        testPackageName: String?,
        testFunctionName: String,
        expectedResult: String,
        withModuleSystem: Boolean
    ) {
        val actualResult = run(files, testModuleName, testPackageName, testFunctionName, withModuleSystem)
        Assert.assertEquals(expectedResult, actualResult.toString().normalize())
    }

    private fun run(
        files: List<String>,
        testModuleName: String?,
        testPackageName: String?,
        testFunctionName: String,
        withModuleSystem: Boolean
    ) = run(files) {
        runTestFunction(testModuleName, testPackageName, testFunctionName, withModuleSystem)
    }


    fun run(files: List<String>) {
        run(files) { null }
    }

    fun checkStdout(files: List<String>, expectedResult: String) {
        run(files) {
            val actualResult = eval(GET_KOTLIN_OUTPUT)
            Assert.assertEquals(expectedResult, actualResult.normalize())
        }
    }

    private fun String.normalize() = StringUtil.convertLineSeparators(this)

    // TODO change return type to `String?`?
    protected abstract fun run(files: List<String>, f: ScriptEngine.() -> Any?): Any?
}

fun ScriptEngine.runAndRestoreContext(f: ScriptEngine.() -> Any?): Any? {
    return try {
        saveState()
        f()
    } finally {
        restoreState()
    }
}

abstract class AbstractNashornJsTestChecker : AbstractJsTestChecker() {

    private var engineUsageCnt = 0

    private var engineCache: ScriptEngineNashorn? = null

    protected val engine: ScriptEngineNashorn
        get() = engineCache ?: createScriptEngineForTest().also {
            engineCache = it
        }

    protected open fun beforeRun() {}

    override fun run(files: List<String>, f: ScriptEngine.() -> Any?): Any? {
        // Recreate the engine once in a while
        if (engineUsageCnt++ > 100) {
            engineUsageCnt = 0
            engineCache = null
        }

        beforeRun()

        return engine.runAndRestoreContext {
            files.forEach { loadFile(it) }
            f()
        }
    }

    protected abstract val preloadedScripts: List<String>

    protected open fun createScriptEngineForTest(): ScriptEngineNashorn {
        val engine = ScriptEngineNashorn()

        preloadedScripts.forEach { engine.loadFile(it) }

        return engine
    }
}

const val SETUP_KOTLIN_OUTPUT = "kotlin.kotlin.io.output = new kotlin.kotlin.io.BufferedOutput();"
const val GET_KOTLIN_OUTPUT = "kotlin.kotlin.io.output.buffer;"

object NashornJsTestChecker : AbstractNashornJsTestChecker() {

    override fun beforeRun() {
        engine.eval(SETUP_KOTLIN_OUTPUT)
    }

    override val preloadedScripts = listOf(
        BasicBoxTest.TEST_DATA_DIR_PATH + "nashorn-polyfills.js",
        BasicBoxTest.DIST_DIR_JS_PATH + "kotlin.js",
        BasicBoxTest.DIST_DIR_JS_PATH + "kotlin-test.js"
    )

    override fun createScriptEngineForTest(): ScriptEngineNashorn {
        val engine = super.createScriptEngineForTest()

        engine.overrideAsserter()

        return engine
    }
}

class NashornIrJsTestChecker : AbstractNashornJsTestChecker() {
    override val preloadedScripts = listOf(
        BasicBoxTest.TEST_DATA_DIR_PATH + "nashorn-polyfills.js",
        "libraries/stdlib/js-v1/src/js/polyfills.js"
    )
}

object V8JsTestChecker : AbstractJsTestChecker() {
    private val engineTL = object : ThreadLocal<ScriptEngine>() {
        override fun initialValue() =
            ScriptEngineV8().apply {
                listOf(
                    BasicBoxTest.DIST_DIR_JS_PATH + "kotlin.js",
                    BasicBoxTest.DIST_DIR_JS_PATH + "kotlin-test.js"
                ).forEach { loadFile(it) }

                overrideAsserter()
            }

        override fun remove() {
            get().release()
        }
    }

    private val engine get() = engineTL.get()

    override fun run(files: List<String>, f: ScriptEngine.() -> Any?): Any? {
        engine.eval(SETUP_KOTLIN_OUTPUT)
        return engine.runAndRestoreContext {
            files.forEach { loadFile(it) }
            f()
        }
    }
}

object V8IrJsTestChecker : AbstractJsTestChecker() {
    private val engine = ScriptEngineV8()

    override fun run(files: List<String>, f: ScriptEngine.() -> Any?): Any? {
        val v = try {
            files.forEach { engine.loadFile(it) }
            engine.f()
        } catch (t: Throwable) {
            try {
                engine.release()
            } finally {
                // Don't mask the original exception
                throw t
            }
        }
        engine.release()

        return v
    }
}
