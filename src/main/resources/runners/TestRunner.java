// src/main/resources/runners/TestRunner.java

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class TestCaseInput {
    public String id;
    public String name;
    public List<String> input;
    public List<String> expected_output;
}

class TestCaseResult {
    public String testCaseId;
    public String testCaseName;
    public boolean passed;
    public List<String> actualOutput;
    public List<String> expectedOutput;
    public String errorMessage;
    public long executionTimeMs;
}

class FinalOutput {
    public String compile_error = null;
    public String runner_error = null;
    public List<TestCaseResult> test_results = new ArrayList<>();
}


public class TestRunner {
    public static void main(String[] args) {
        FinalOutput finalOutput = new FinalOutput();
        ObjectMapper objectMapper = new ObjectMapper();

        if (args.length < 1) {
            finalOutput.runner_error = "Test data JSON file path not provided as argument.";
            try {
                System.out.println(objectMapper.writeValueAsString(finalOutput));
            } catch (Exception e) {
            }
            return;
        }
        String testDataFilePath = args[0];

        try {
            String testCasesJson = new String(Files.readAllBytes(new File(testDataFilePath).toPath()));
            List<TestCaseInput> testCases = objectMapper.readValue(testCasesJson, new TypeReference<List<TestCaseInput>>() {
            });

            for (TestCaseInput tc : testCases) {
                TestCaseResult result = new TestCaseResult();
                result.testCaseId = tc.id;
                result.testCaseName = tc.name;
                result.expectedOutput = tc.expected_output != null ? tc.expected_output : new ArrayList<>();
                result.actualOutput = new ArrayList<>();

                PrintStream originalOut = System.out;
                PrintStream originalErr = System.err;
                ByteArrayOutputStream baosOut = new ByteArrayOutputStream();
                ByteArrayOutputStream baosErr = new ByteArrayOutputStream();
                System.setOut(new PrintStream(baosOut));
                System.setErr(new PrintStream(baosErr));

                long startTime = System.nanoTime();
                try {
                    StudentCode.main_function(tc.input != null ? tc.input.toArray(new String[0]) : new String[0]);

                    String consoleOutput = baosOut.toString().trim();
                    if (!consoleOutput.isEmpty()) {
                        result.actualOutput.addAll(Arrays.asList(consoleOutput.split("\\r?\\n")));
                    }

                    String studentErrOutput = baosErr.toString().trim();
                    if (!studentErrOutput.isEmpty()) {
                        result.errorMessage = (result.errorMessage == null ? "" : result.errorMessage + "\n") + "Student Code STDERR: " + studentErrOutput;
                    }

                } catch (Throwable t) {
                    result.errorMessage = t.getClass().getName() + ": " + t.getMessage();
                    if (t.getCause() != null) {
                        result.errorMessage += "\nCause: " + t.getCause().getClass().getName() + ": " + t.getCause().getMessage();
                    }
                } finally {
                    long endTime = System.nanoTime();
                    result.executionTimeMs = (endTime - startTime) / 1_000_000;
                    System.setOut(originalOut);
                    System.setErr(originalErr);
                }

                if (result.errorMessage == null) {
                    if (result.actualOutput.size() == result.expectedOutput.size()) {
                        result.passed = true;
                        for (int i = 0; i < result.actualOutput.size(); i++) {
                            if (!result.actualOutput.get(i).trim().equals(result.expectedOutput.get(i).trim())) {
                                result.passed = false;
                                break;
                            }
                        }
                    } else if (result.actualOutput.isEmpty() && result.expectedOutput.isEmpty()) {
                        result.passed = true;
                    } else {
                        result.passed = false;
                    }
                } else {
                    result.passed = false;
                }
                finalOutput.test_results.add(result);
            }
        } catch (Exception e) {
            finalOutput.runner_error = "Error in Java TestRunner: " + e.getClass().getName() + ": " + e.getMessage();
        }

        try {
            System.out.println(objectMapper.writeValueAsString(finalOutput));
        } catch (Exception e) {
            System.out.println("{\"compile_error\": null, \"runner_error\": \"CRITICAL: Failed to serialize final JSON output: " + e.getMessage().replace("\"", "\\\"") + "\", \"test_results\": []}");
        }
    }
}