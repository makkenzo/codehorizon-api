const fs = require('fs');
const vm = require('vm');
const process = require('process');
const { performance } = require('perf_hooks');
const util = require('util');

const STUDENT_CODE_FILE = './student_code.js';
const TEST_DATA_FILE = 'test_data.json';
const MAX_OUTPUT_LINES = 100;
const MAX_OUTPUT_LINE_LENGTH = 1024;
const MAX_ERROR_MESSAGE_LENGTH = 2048;

let compileOrImportError = null;
let studentMainFunction = null;

try {
    const studentCodeContent = fs.readFileSync(STUDENT_CODE_FILE, 'utf8');

    const studentScript = new vm.Script(
        `
        let exports = {};
        ${studentCodeContent}
        if (typeof main_function !== 'function') {
            throw new Error("main_function is not defined or not a function in your code.");
        }
        exports.main_function = main_function;
        exports; 
    `,
        { filename: STUDENT_CODE_FILE }
    );

    const tempContext = vm.createContext({
        require: require,
        console: { ...console },
        process: {
            stdout: process.stdout,
            stderr: process.stderr,
            argv: [],
            env: {},
            cwd: () => '/usr/src/app',
        },

        setTimeout,
        clearTimeout,
        setInterval,
        clearInterval,
        Buffer,
        Math,
        JSON,
        Date,
        RegExp,
        Array,
        Object,
        String,
        Number,
        Boolean,
        Symbol,
        Map,
        Set,
        WeakMap,
        WeakSet,
        Promise,
    });
    const scriptExports = studentScript.runInContext(tempContext, { timeout: 5000 });
    studentMainFunction = scriptExports.main_function;
} catch (e) {
    compileOrImportError = `Error loading/compiling student code: ${e.name}: ${e.message}\n${e.stack || ''}`.substring(
        0,
        MAX_ERROR_MESSAGE_LENGTH
    );
    studentMainFunction = null;
}

async function runSingleTestCase(testCaseData, studentFuncRef) {
    if (compileOrImportError) {
        return {
            testCaseId: testCaseData.id,
            testCaseName: testCaseData.name,
            passed: false,
            actualOutput: null,
            expectedOutput: testCaseData.expected_output || [],
            errorMessage: compileOrImportError,
            executionTimeMs: 0,
        };
    }

    if (typeof studentFuncRef !== 'function') {
        return {
            testCaseId: testCaseData.id,
            testCaseName: testCaseData.name,
            passed: false,
            actualOutput: null,
            expectedOutput: testCaseData.expected_output || [],
            errorMessage: "Student's main_function is not available (compilation or definition error).",
            executionTimeMs: 0,
        };
    }

    const capturedConsoleOutput = [];
    let returnedValue = undefined;
    let errorMessage = null;
    let executionTimeMs = 0;

    const sandbox = {
        console: {
            log: (...args) => {
                if (capturedConsoleOutput.length < MAX_OUTPUT_LINES) {
                    const line = args
                        .map((arg) => util.format(arg))
                        .join(' ')
                        .substring(0, MAX_OUTPUT_LINE_LENGTH);
                    capturedConsoleOutput.push(line);
                }
            },
        },
        require: (moduleName) => {
            return require(moduleName);
        },

        setTimeout,
        clearTimeout,
        setInterval,
        clearInterval,
        Buffer,
        Math,
        JSON,
        Date,
        RegExp,
        Array,
        Object,
        String,
        Number,
        Boolean,
        Symbol,
        Map,
        Set,
        WeakMap,
        WeakSet,
        Promise,
    };
    vm.createContext(sandbox);

    const startTime = performance.now();
    try {
        const tcInput = testCaseData.input || [];

        const codeToRunInSandbox = `
            (${studentFuncRef.toString()})(${JSON.stringify(tcInput)});
        `;

        const resultPromiseOrValue = vm.runInContext(codeToRunInSandbox, sandbox, {
            timeout: (testCaseData.timeoutSeconds || 5) * 1000,
            displayErrors: true,
        });

        if (resultPromiseOrValue && typeof resultPromiseOrValue.then === 'function') {
            returnedValue = await resultPromiseOrValue;
        } else {
            returnedValue = resultPromiseOrValue;
        }
    } catch (e) {
        errorMessage = `Runtime error: ${e.name}: ${e.message}\n${e.stack || ''}`.substring(
            0,
            MAX_ERROR_MESSAGE_LENGTH
        );
    } finally {
        executionTimeMs = Math.round(performance.now() - startTime);
    }

    let actualOutputToCompare;

    if (returnedValue !== undefined) {
        if (Array.isArray(returnedValue) && returnedValue.length > 0) {
            actualOutputToCompare = returnedValue.map((item) => String(item).trim());
        } else if (!Array.isArray(returnedValue)) {
            actualOutputToCompare = [String(returnedValue).trim()];
        } else {
            actualOutputToCompare = capturedConsoleOutput.map((line) => line.trim());
        }
    } else {
        actualOutputToCompare = capturedConsoleOutput.map((line) => line.trim());
    }

    if (actualOutputToCompare.length === 0 && capturedConsoleOutput.length === 0 && returnedValue === undefined) {
        actualOutputToCompare = [];
    }

    let isPassed = false;
    const expectedOutput = testCaseData.expected_output || [];

    if (!errorMessage) {
        if (actualOutputToCompare.length === expectedOutput.length) {
            isPassed = actualOutputToCompare.every((ao, i) => String(ao).trim() === String(expectedOutput[i]).trim());
        } else if (actualOutputToCompare.length === 0 && expectedOutput.length === 0) {
            isPassed = true;
        }
    }

    return {
        testCaseId: testCaseData.id,
        testCaseName: testCaseData.name,
        passed: isPassed,
        actualOutput: actualOutputToCompare.length > 0 ? actualOutputToCompare : null,
        expectedOutput: expectedOutput.length > 0 ? expectedOutput : null,
        errorMessage: errorMessage,
        executionTimeMs: executionTimeMs,
    };
}

async function main() {
    const allTestResults = [];
    let overallErrorMessage = null;

    if (compileOrImportError) {
        try {
            const testCasesContent = fs.readFileSync(TEST_DATA_FILE, 'utf8');
            const testCases = JSON.parse(testCasesContent);
            for (const tc of testCases) {
                allTestResults.push(await runSingleTestCase(tc, null));
            }
        } catch (e) {
            overallErrorMessage =
                `Failed to load test data after student code compilation error: ${e.message}`.substring(
                    0,
                    MAX_ERROR_MESSAGE_LENGTH
                );
        }
    } else {
        try {
            const testCasesContent = fs.readFileSync(TEST_DATA_FILE, 'utf8');
            const testCases = JSON.parse(testCasesContent);
            for (const tc of testCases) {
                allTestResults.push(await runSingleTestCase(tc, studentMainFunction));
            }
        } catch (e) {
            overallErrorMessage = `Error in test runner: ${e.name}: ${e.message}\n${e.stack || ''}`.substring(
                0,
                MAX_ERROR_MESSAGE_LENGTH
            );

            if (allTestResults.length === 0) {
                allTestResults.push({
                    testCaseId: 'runner_error',
                    testCaseName: 'Runner Execution Error',
                    passed: false,
                    errorMessage: overallErrorMessage,
                    executionTimeMs: 0,
                });
            }
        }
    }

    const finalOutput = {
        compile_error: compileOrImportError,
        runner_error: overallErrorMessage,
        test_results: allTestResults,
    };

    process.stdout.write(JSON.stringify(finalOutput) + '\n');
}

if (require.main === module) {
    main().catch((criticalError) => {
        const fatalErrorOutput = {
            compile_error: compileOrImportError,
            runner_error: `CRITICAL RUNNER FAILURE: ${criticalError.name}: ${criticalError.message}\n${
                criticalError.stack || ''
            }`.substring(0, MAX_ERROR_MESSAGE_LENGTH),
            test_results: [],
        };
        process.stdout.write(JSON.stringify(fatalErrorOutput) + '\n');
        process.exit(1);
    });
}
