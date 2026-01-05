package top.tsxb.compiler.backend.opti;

import java.util.ArrayList;
import java.util.List;

import top.tsxb.compiler.common.ErrorReporter;
import top.tsxb.compiler.ir.structure.Module;

public class PassManager {
    private final List<Pass> prePasses = new ArrayList<>();
    private final List<Pass> fixedPointPasses = new ArrayList<>();
    private final List<Pass> postPasses = new ArrayList<>();
    private final List<Pass> cleanupPasses = new ArrayList<>();

    public static PassManager createDefault() {
        PassManager manager = new PassManager();

        // Pre: normalize IR once.
        manager.prePasses.add(new GlobalLocalizationPass());
        manager.prePasses.add(new Mem2RegPass());

        // Fixed-point: only the passes that enable each other (constants <-> inlining <-> CFG cleanup).
        manager.fixedPointPasses.add(new IpsccpPass());
        manager.fixedPointPasses.add(new SccpPass());
        manager.fixedPointPasses.add(new FunctionInliningPass());
        // Inlining can re-introduce scalar allocas into the caller; re-run Mem2Reg to avoid spill-heavy codegen.
        manager.fixedPointPasses.add(new Mem2RegPass());
        manager.fixedPointPasses.add(new TailRecursionEliminationPass());
        manager.fixedPointPasses.add(new DeadCodeEliminationPass());
        manager.fixedPointPasses.add(new SimplifyCfgPass());

        // Post: run once after the IR stabilizes, to avoid quadratic blow-ups from re-running them in a global loop.
        manager.postPasses.add(new LoopUnrollingPass());
        manager.postPasses.add(new SimplifyCfgPass());
        manager.postPasses.add(new GvnPass());
        manager.postPasses.add(new LicmPass());
        manager.postPasses.add(new LoopStrengthReductionPass());
        manager.postPasses.add(new GcmPass());

        // Cleanup: small local fixed-point to clean up after late passes.
        manager.cleanupPasses.add(new DeadCodeEliminationPass());
        manager.cleanupPasses.add(new SimplifyCfgPass());

        return manager;
    }

    public void runAll(Module module, ErrorReporter errorReporter) {
        runPassList(module, errorReporter, prePasses);
        runFixedPoint(module, errorReporter, fixedPointPasses, 64);
        runPassList(module, errorReporter, postPasses);
        runFixedPoint(module, errorReporter, cleanupPasses, 16);
    }

    private void runPassList(Module module, ErrorReporter errorReporter, List<Pass> passes) {
        for (Pass pass : passes) {
            if (errorReporter.hasErrors()) {
                return;
            }
            pass.run(module);
        }
    }

    private void runFixedPoint(Module module, ErrorReporter errorReporter, List<Pass> passes, int maxIterations) {
        int iterations = 0;
        boolean changed = true;
        while (changed && iterations < maxIterations) {
            if (errorReporter.hasErrors()) {
                return;
            }
            changed = false;
            for (Pass pass : passes) {
                if (errorReporter.hasErrors()) {
                    return;
                }
                boolean passChanged = pass.run(module);
                changed |= passChanged;
            }
            iterations++;
        }
    }
}
