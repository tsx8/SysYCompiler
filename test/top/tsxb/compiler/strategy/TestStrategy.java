package top.tsxb.compiler.strategy;

import java.io.IOException;

import top.tsxb.compiler.model.TestCase;
import top.tsxb.compiler.model.TestResult;
import top.tsxb.compiler.runner.TestLogger;

public interface TestStrategy {
    TestResult execute(TestCase testCase, TestLogger logger);

    default void prepare() throws IOException {}

    default void cleanup() throws IOException {}
}
