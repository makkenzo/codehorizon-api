const fs = require('fs');
const vm = require('vm');
const process = require('process');
const { performance } = require('perf_hooks');

let studentCodeError = null;
let mainFunctionInstance = null;

try {
    const studentCodeContent = fs.readFileSync('./student_code.js', 'utf8');
    const script = new vm.Script(studentCodeContent + '\\nmodule.exports = { main_function };');
    const context = {
        module: { exports: {} },
        require: require,
        console: console,
        process: process,
    };
    vm.createContext(context);
    script.runInContext(context);
    mainFunctionInstance = context.module.exports.main_function;

    if (typeof mainFunctionInstance !== 'function') {
        studentCodeError = 'main_function is not a function or not exported from student_code.js.';
        mainFunctionInstance = null;
    }
} catch (e) {
    studentCodeError = `Error during import/evaluation of student_code.js: ${e.name}: ${e.message}`;
    if (e.stack) {
        studentCodeError += `\\nStack: ${e.stack}`;
    }
}

function runTestCase(testCaseData, studentFunc) {
    if (studentCodeError) {
        return {
            testCaseId: testCaseData.id,
            testCaseName: testCaseData.name,
            passed: false,
            actualOutput: null,
            expectedOutput: testCaseData.expected_output || [],
            errorMessage: studentCodeError,
            executionTimeMs: 0,
        };
    }
    if (typeof studentFunc !== 'function') {
        return {
            testCaseId: testCaseData.id,
            testCaseName: testCaseData.name,
            passed: false,
            actualOutput: null,
            expectedOutput: testCaseData.expected_output || [],
            errorMessage: "Student's main_function is not available.",
            executionTimeMs: 0,
        };
    }

    let capturedOutputLines = [];
    let capturedErrorLines = [];

    const originalConsoleLog = console.log;
    console.log = (...args) => {
        const line = args
            .map((arg) => {
                if (typeof arg === 'object' && arg !== null) {
                    try {
                        return JSON.stringify(arg);
                    } catch (e) {
                        return String(arg);
                    }
                }
                return String(arg);
            })
            .join(' ');
        capturedOutputLines.push(line);
    };

    let actualOutputsList = [];
    let errorMsg = null;
    const startTimeTc = performance.now();

    try {
        const tcInput = testCaseData.input || [];

        studentFunc(tcInput);

        actualOutputsList = [...capturedOutputLines];
    } catch (eRuntime) {
        errorMsg = `${eRuntime.name}: ${eRuntime.message}`;
        if (eRuntime.stack) {
            errorMsg += `\\nStack: ${eRuntime.stack}`;
        }
    } finally {
        console.log = originalConsoleLog;
        capturedOutputLines = [];
    }

    const executionTimeTcMs = performance.now() - startTimeTc;

    let isPassed = false;
    const expectedTcOutput = testCaseData.expected_output || [];

    if (errorMsg === null) {
        if (actualOutputsList.length === expectedTcOutput.length) {
            isPassed = actualOutputsList.every((ao, i) => String(ao).trim() === String(expectedTcOutput[i]).trim());
        } else if (actualOutputsList.length === 0 && expectedTcOutput.length === 0) {
            isPassed = true;
        }
    }

    return {
        testCaseId: testCaseData.id,
        testCaseName: testCaseData.name,
        passed: isPassed,
        actualOutput: actualOutputsList.length > 0 ? actualOutputsList : null,
        expectedOutput: expectedTcOutput.length > 0 ? expectedTcOutput : null,
        errorMessage: errorMsg,
        executionTimeMs: Math.round(executionTimeTcMs),
    };
}

if (require.main === module) {
    const allResults = [];
    let compileErrorOutput = null;

    if (studentCodeError) {
        compileErrorOutput = studentCodeError;

        try {
            const initialTestCasesContent = fs.readFileSync('test_data.json', 'utf8');
            const initialTestCases = JSON.parse(initialTestCasesContent);
            for (const tcInit of initialTestCases) {
                allResults.push(runTestCase(tcInit, null));
            }
        } catch (eSetupFail) {
            allResults.push({
                testCaseId: 'setup_failure',
                testCaseName: 'Setup Failure',
                passed: false,
                errorMessage: `Critical error reading tests during import fail: ${eSetupFail.message}`,
                executionTimeMs: 0,
            });
        }
    } else {
        try {
            const testCasesContent = fs.readFileSync('test_data.json', 'utf8');
            const testCasesList = JSON.parse(testCasesContent);
            for (const testCaseItem of testCasesList) {
                allResults.push(runTestCase(testCaseItem, mainFunctionInstance));
            }
        } catch (e) {
            let errorId = 'error_runtime_main';
            let errorName = 'Main Runner Error';
            if (e instanceof SyntaxError) {
                errorId = 'error_json_decode';
                errorName = 'Test Data Error';
            } else if (e.code === 'ENOENT') {
                errorId = 'error_no_tests';
                errorName = 'Test Data Error';
            }
            allResults.push({
                testCaseId: errorId,
                testCaseName: errorName,
                passed: false,
                errorMessage: `Error in main runner: ${e.name}: ${e.message}`,
            });
        }
    }

    const finalOutputStructure = {
        compile_error: compileErrorOutput,
        stdout_keseluruhan: '',
        stderr_keseluruhan: '',
        test_results: allResults,
    };
    process.stdout.write(JSON.stringify(finalOutputStructure) + '\\n');
}
