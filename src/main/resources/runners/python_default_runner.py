import json
import sys
import traceback
import time
from io import StringIO

student_code_error = None
main_function_instance = None

try:
    from student_code import main_function
    main_function_instance = main_function
except ImportError:
    student_code_error = "Failed to import student_code.py or main_function not found."
except Exception as e:
    student_code_error = f"Error during import of student_code: {type(e).__name__}: {str(e)}"

def run_test_case(test_case_data, student_func):
    if student_code_error:
        return {
            "testCaseId": test_case_data["id"],
            "testCaseName": test_case_data["name"],
            "passed": False,
            "actualOutput": None,
            "expectedOutput": test_case_data.get("expected_output"),
            "errorMessage": student_code_error,
            "executionTimeMs": 0
        }

    old_stdout = sys.stdout
    sys.stdout = captured_output = StringIO()
    old_stderr = sys.stderr
    sys.stderr = captured_error_output = StringIO()

    actual_outputs_list = []
    error_msg = None
    start_time_tc = time.perf_counter()

    try:
        tc_input = test_case_data.get("input", [])
        if tc_input:
             student_func(*tc_input)
        else:
             student_func()

        printed_output = captured_output.getvalue().strip()
        if printed_output:
            actual_outputs_list.extend(printed_output.splitlines())

    except Exception as e_runtime:
        error_msg = f"{type(e_runtime).__name__}: {str(e_runtime)}"

    finally:
        sys.stdout = old_stdout
        sys.stderr = old_stderr
        execution_time_tc_ms = (time.perf_counter() - start_time_tc) * 1000

    stderr_val = captured_error_output.getvalue().strip()
    if stderr_val:
        if error_msg:
            error_msg += f"\\nSTDERR: {stderr_val}"
        else:
            error_msg = f"STDERR: {stderr_val}"

    is_passed = False
    expected_tc_output = test_case_data.get("expected_output", [])

    if error_msg is None:
        if len(actual_outputs_list) == len(expected_tc_output):
            is_passed = all(ao.strip() == eo.strip() for ao, eo in zip(actual_outputs_list, expected_tc_output))
        elif not actual_outputs_list and not expected_tc_output:
            is_passed = True

    return {
        "testCaseId": test_case_data["id"],
        "testCaseName": test_case_data["name"],
        "passed": is_passed,
        "actualOutput": actual_outputs_list if actual_outputs_list else None,
        "expectedOutput": expected_tc_output if expected_tc_output else None,
        "errorMessage": error_msg,
        "executionTimeMs": round(execution_time_tc_ms)
    }

if __name__ == "__main__":
    all_results = []
    compile_error_output = None

    if student_code_error:
        compile_error_output = student_code_error

        try:
            with open("test_data.json", "r", encoding="utf-8") as f_init_tests:
                initial_test_cases = json.load(f_init_tests)
            for tc_init in initial_test_cases:
                all_results.append(run_test_case(tc_init, None))
        except Exception as e_setup_fail:
             all_results.append({
                "testCaseId": "setup_failure", "testCaseName": "Setup Failure",
                "passed": False, "errorMessage": f"Critical error reading tests during import fail: {str(e_setup_fail)}",
                "executionTimeMs": 0
            })
    else:
        try:
            with open("test_data.json", "r", encoding="utf-8") as f_tests:
                test_cases_list = json.load(f_tests)
            for test_case_item in test_cases_list:
                all_results.append(run_test_case(test_case_item, main_function_instance))
        except FileNotFoundError:
            all_results.append({"testCaseId": "error_no_tests", "testCaseName": "Test Data Error", "passed": False, "errorMessage": "test_data.json not found."})
        except json.JSONDecodeError:
            all_results.append({"testCaseId": "error_json_decode", "testCaseName": "Test Data Error", "passed": False, "errorMessage": "Error decoding test_data.json."})
        except Exception as e_main_run:
            all_results.append({"testCaseId": "error_runtime_main", "testCaseName": "Main Runner Error", "passed": False, "errorMessage": f"Unexpected error in main runner: {str(e_main_run)}"})

    final_output_structure = {
        "compile_error": compile_error_output,
        "stdout_keseluruhan": "",
        "stderr_keseluruhan": "",
        "test_results": all_results
    }
    print(json.dumps(final_output_structure))