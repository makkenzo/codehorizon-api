#!/bin/sh
set -e

COMPILE_ERROR=""
RUNNER_ERROR=""
TEST_RESULTS="[]"

javac StudentCode.java TestRunner.java 2> compile_output.txt
COMPILE_EXIT_CODE=$?

if [ $COMPILE_EXIT_CODE -ne 0 ]; then
    COMPILE_ERROR=$(cat compile_output.txt | sed 's/"/\\"/g' | sed ':a;N;$!ba;s/\n/\\n/g')
    echo "{\"compile_error\": \"$COMPILE_ERROR\", \"runner_error\": null, \"test_results\": []}"
    exit 0
fi

java -cp .:jackson-core.jar:jackson-annotations.jar:jackson-databind.jar TestRunner test_data.json 2> runner_exec_error.txt
RUN_EXIT_CODE=$?


if [ $RUN_EXIT_CODE -ne 0 ]; then
    RUNNER_ERROR_CONTENT=$(cat runner_exec_error.txt | sed 's/"/\\"/g' | sed ':a;N;$!ba;s/\n/\\n/g')
    echo "{\"compile_error\": null, \"runner_error\": \"Java TestRunner execution failed: $RUNNER_ERROR_CONTENT Exit code: $RUN_EXIT_CODE\", \"test_results\": []}"
    exit 0
else
    # DockerService заберет этот stdout.
    # Этот 'else' блок здесь для ясности, что stdout от `java TestRunner` и есть наш результат.
    # Если java TestRunner ничего не вывел, то это тоже проблема, которую DockerService отловит (пустой stdout)
    : # No-op, stdout is already captured
fi

rm -f *.class compile_output.txt runner_exec_error.txt