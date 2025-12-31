package top.tsxb.compiler.backend.opti;

import top.tsxb.compiler.common.ErrorReporter;
import top.tsxb.compiler.ir.structure.Module;

@FunctionalInterface
public interface Pass {
    void run(Module module, ErrorReporter errorReporter);

    default String name() {
        return getClass().getSimpleName();
    }
}
