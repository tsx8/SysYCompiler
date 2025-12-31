package top.tsxb.compiler.driver;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import top.tsxb.compiler.backend.IrGenStage;
import top.tsxb.compiler.backend.MipsGenStage;
import top.tsxb.compiler.common.CompilerStage;
import top.tsxb.compiler.common.ErrorEntry;
import top.tsxb.compiler.common.ErrorReporter;
import top.tsxb.compiler.frontend.LexerStage;
import top.tsxb.compiler.frontend.ParserStage;
import top.tsxb.compiler.frontend.SemanticStage;

/**
 * The type Pipeline.
 */
public class Pipeline {
    private final Map<String, CompilerStage<?, ?>> stages = new LinkedHashMap<>();

    /**
     * Instantiates a new Pipeline.
     */
    public Pipeline() {
        stages.put("lexer", new LexerStage());
        stages.put("parser", new ParserStage());
        stages.put("semantic", new SemanticStage());
        stages.put("llvm", new IrGenStage());
        stages.put("mips", new MipsGenStage());
    }

    /**
     * Run.
     */
    public void run() {
        try {
            String sourceCode = Files.readString(Paths.get(CompilerConfig.SOURCE_FILE));
            PipelineResult result = executeInternal(sourceCode, CompilerConfig.CURRENT_HOMEWORK);

            String filePath = result.hasErrors ? CompilerConfig.ERROR_FILE : CompilerConfig.OUTPUT_FILE;

            Path path = Paths.get(filePath);
            Path parentDir = path.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            try (PrintWriter writer = new PrintWriter(filePath)) {
                writer.print(result.report);
            }
        } catch (Exception e) {
            System.err.println("Fatal Compiler Error: " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }

    public String run(String sourceCode, String targetStage) {
        return executeInternal(sourceCode, targetStage).report;
    }

    private PipelineResult executeInternal(String sourceCode, String targetStage) {
        ErrorReporter localReporter = new ErrorReporter();
        Object artefact = sourceCode;
        String currentReport = "";

        for (Map.Entry<String, CompilerStage<?, ?>> entry : stages.entrySet()) {
            String stageName = entry.getKey();

            if ((stageName.equals("llvm") || stageName.equals("mips")) && localReporter.hasErrors()) {
                return new PipelineResult(formatErrorReport(localReporter), true);
            }

            @SuppressWarnings("unchecked")
            CompilerStage<Object, Object> stage = (CompilerStage<Object, Object>)entry.getValue();

            try {
                CompilerStage.StageResult<Object> result = stage.process(artefact, localReporter);
                artefact = result.artefact();
                currentReport = result.report();
            } catch (Exception e) {
                if (!localReporter.hasErrors()) {
                    throw e;
                }
                return new PipelineResult(formatErrorReport(localReporter), true);
            }

            if (stageName.equals(targetStage)) {
                if (localReporter.hasErrors()) {
                    return new PipelineResult(formatErrorReport(localReporter), true);
                }
                return new PipelineResult(currentReport, false);
            }
        }

        return new PipelineResult(currentReport, localReporter.hasErrors());
    }

    private String formatErrorReport(ErrorReporter reporter) {
        return reporter.getErrors().stream()
        .map(ErrorEntry::submission)
        .collect(Collectors.joining(System.lineSeparator()));
    }

    private record PipelineResult(String report, boolean hasErrors) {
    }
}
