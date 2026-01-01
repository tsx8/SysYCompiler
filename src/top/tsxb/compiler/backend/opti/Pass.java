package top.tsxb.compiler.backend.opti;

import top.tsxb.compiler.ir.structure.Module;

@FunctionalInterface
public interface Pass {
    boolean run(Module module);

    default String name() {
        return getClass().getSimpleName();
    }
}
