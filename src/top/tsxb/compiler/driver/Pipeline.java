package top.tsxb.compiler.driver;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import top.tsxb.compiler.common.CompilerStage;
import top.tsxb.compiler.common.ErrorReporter;
import top.tsxb.compiler.frontend.LexerStage;
import top.tsxb.compiler.frontend.ParserStage;
import top.tsxb.compiler.frontend.SemanticStage;

/**
 * The type Pipeline.
 */
public class Pipeline {
    private final Map<String, CompilerStage<?, ?>> stages = new LinkedHashMap<>();
    private ErrorReporter errorReporter;

    /**
     * Instantiates a new Pipeline.
     */
    public Pipeline() {
        stages.put("lexer", new LexerStage());
        stages.put("parser", new ParserStage());
        stages.put("semantic", new SemanticStage());
    }

    /**
     * Run.
     */
    public void run() {
        try {
            String sourceCode = Files.readString(Paths.get(CompilerConfig.SOURCE_FILE));
            String output = run(sourceCode, CompilerConfig.CURRENT_HOMEWORK);
            String filePath;

            if (errorReporter.hasErrors()) {
                filePath = CompilerConfig.ERROR_FILE;
            } else {
                filePath = CompilerConfig.OUTPUT_FILE;
            }

            Path path = Paths.get(filePath);
            Path parentDir = path.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            try (PrintWriter writer = new PrintWriter(filePath)) {
                writer.print(output);
            }
        } catch (Exception e) {
            System.err.println("Fatal Compiler Error: " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }

    public String run(String sourceCode, String targetStage) {
        this.errorReporter = new ErrorReporter();

        Object artefact = sourceCode;
        String report = "";

        for (Map.Entry<String, CompilerStage<?, ?>> entry : stages.entrySet()) {
            String stageName = entry.getKey();

            @SuppressWarnings("unchecked")
            CompilerStage<Object, Object> stage = (CompilerStage<Object, Object>)entry.getValue();

            CompilerStage.StageResult<Object> result = stage.process(artefact, errorReporter);

            artefact = result.artefact();
            report = result.report();

            if (stageName.equals(targetStage)) {
                break;
            }
        }

        return report;
    }
}
