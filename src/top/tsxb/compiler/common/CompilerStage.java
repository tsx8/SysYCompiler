package top.tsxb.compiler.common;

import java.util.stream.Collectors;

@FunctionalInterface
public interface CompilerStage<I, A> {
    StageResult<A> process(I input, ErrorReporter errorReporter);

    default String report(String output, ErrorReporter errorReporter) {
        if (errorReporter.hasErrors()) {
            return errorReporter.getErrors().stream().map(ErrorEntry::submission)
                .collect(Collectors.joining(System.lineSeparator()));
        } else {
            return output;
        }
    }

    record StageResult<A>(A artefact, String report) {
    }
}
