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
        for (Pass pass : passes) {
            if (errorReporter.hasErrors()) {
                break;
            }
            pass.run(module, errorReporter);
        }
    }

    public static PassManager createDefault() {
        PassManager manager = new PassManager();
        // Add default optimization passes here as they become available.
        return manager;
    }
}
