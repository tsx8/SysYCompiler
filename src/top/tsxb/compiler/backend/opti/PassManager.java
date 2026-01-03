package top.tsxb.compiler.backend.opti;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import top.tsxb.compiler.common.ErrorReporter;
import top.tsxb.compiler.ir.structure.Module;

public class PassManager {
    private final List<Pass> passes = new ArrayList<>();

    public PassManager register(Pass pass) {
        passes.add(Objects.requireNonNull(pass, "pass"));
        return this;
    }

    public void runAll(Module module, ErrorReporter errorReporter) {
        int iterations = 0;
        int MAX_ITERATIONS = 15;
        boolean changed = true;
        while (changed && iterations < MAX_ITERATIONS) {
            changed = false;
            for (Pass pass : passes) {
                if (errorReporter.hasErrors()) {
                    return;
                }
                boolean passChanged = pass.run(module);
                if (passChanged) {
                    changed = true;
                }
            }
            iterations++;
        }
    }

    public static PassManager createDefault() {
        PassManager manager = new PassManager();
        manager.register(new FunctionInliningPass());
        manager.register(new GlobalLocalizationPass());
        manager.register(new Mem2RegPass());
        manager.register(new SccpPass());
        manager.register(new IpsccpPass());
        manager.register(new GvnPass());
        manager.register(new SimplifyCfgPass());
        manager.register(new TailRecursionEliminationPass());
        manager.register(new LoopUnrollingPass());
        manager.register(new LoopStrengthReductionPass());
        manager.register(new LicmPass());
        manager.register(new GcmPass());
        manager.register(new DeadCodeEliminationPass());
        return manager;
    }
}
