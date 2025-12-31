package top.tsxb.compiler.backend;

import java.util.Objects;

import top.tsxb.compiler.backend.opti.PassManager;
import top.tsxb.compiler.common.CompilerStage;
import top.tsxb.compiler.common.ErrorReporter;
import top.tsxb.compiler.ir.structure.Module;

public class OptimizeStage implements CompilerStage<Module, Module> {
    private final PassManager passManager;

    public OptimizeStage() {
        this(PassManager.createDefault());
    }

    public OptimizeStage(PassManager passManager) {
        this.passManager = Objects.requireNonNull(passManager, "passManager");
    }

    @Override
    public StageResult<Module> process(Module module, ErrorReporter errorReporter) {
        passManager.runAll(module, errorReporter);
        String report = report(module.toString(), errorReporter);
        return new StageResult<>(module, report);
    }
}
