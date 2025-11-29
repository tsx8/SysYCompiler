package top.tsxb.compiler.model;

import java.nio.file.Path;

public record TestCase(String name, Path sourceFile, Path expectedOutputFile, Path inputFile) {
}
