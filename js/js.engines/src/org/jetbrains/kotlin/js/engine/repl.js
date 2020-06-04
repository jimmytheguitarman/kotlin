/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

// ~/.jsvu/v8 js/js.engines/src/org/jetbrains/kotlin/js/engine/repl.js
// TODO create polyfills for node.js. What for?

/*
Some of non-standard APIs available in standalone JS engines:

            v8   sm  jsc
load         +    +    +    load and evaluate a file
print        +    +    +    print to stdout
printErr     +    +    +    print to stderr
read         +    +    +    read a file as a text (v8, sm, jsc) or binary (sm, jsc)
readline     +    +    +    read line from stdin
readbuffer   +    -    -    read a binary file in v8
quit         +    +    +    stop the process

V8:
https://v8.dev/docs/d8
https://github.com/v8/v8/blob/4b9b23521e6fd42373ebbcb20ebe03bf445494f9/src/d8.cc
https://riptutorial.com/v8/example/25393/useful-built-in-functions-and-objects-in-d8

SpiderMonkey:
https://developer.mozilla.org/en-US/docs/Mozilla/Projects/SpiderMonkey/Introduction_to_the_JavaScript_shell

JavaScriptCore:
https://trac.webkit.org/wiki/JSC
https://github.com/WebKit/webkit/blob/master/Source/JavaScriptCore/jsc.cpp
*/

(function () {
    let activeRealmIndex = 0;

    function saveState() {
        if (activeRealmIndex > 0) throw Error("Could not save more than one state, activeRealmIndex = " + activeRealmIndex);

        activeRealmIndex = Realm.createAllowCrossRealmAccess();

        const newGlobal = Realm.global(activeRealmIndex);
        for (const k of Object.getOwnPropertyNames(this)) {
            if (newGlobal[k] === void 0) {
                newGlobal[k] = this[k]
            }
        }
    }

    function restoreState() {
        if (activeRealmIndex <= 0) throw Error("Could not restore unsaved state, activeRealmIndex = " + activeRealmIndex);

        Realm.dispose(activeRealmIndex);
        activeRealmIndex = 0;
    }

    // noinspection InfiniteLoopJS
    while (true) {
        let code = readline().replace(/\\n/g, '\n');

        try {
            switch (code) {
                case "!saveState":
                    saveState();
                    break;
                case "!restoreState":
                    restoreState();
                    break;
                default:
                    print(Realm.eval(activeRealmIndex, code));
            }
        } catch(e) {
            printErr(e.stack != null ? e.stack : e.toString());
            printErr('\nCODE:\n' + code);
        }

        print('<END>');
    }
})()
