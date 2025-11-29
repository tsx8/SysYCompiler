package top.tsxb.compiler.model;

public sealed interface TestResult {
    record Passed(String actualOutput) implements TestResult {
    }

    record Failed(String actualOutput, String expectedOutput, String reason) implements TestResult {
    }

    record ExecutionError(String summary, String command, String stderr) implements TestResult {
    }
}
