package top.tsxb.compiler.backend;

import top.tsxb.compiler.backend.mips.MipsBuilder;
import top.tsxb.compiler.common.CompilerStage;
import top.tsxb.compiler.common.ErrorReporter;

public class MipsGenStage implements CompilerStage<top.tsxb.compiler.ir.structure.Module, String> {
    @Override
    public StageResult<String> process(top.tsxb.compiler.ir.structure.Module module, ErrorReporter errorReporter) {
        MipsBuilder builder = new MipsBuilder(module);
        String mipsCode = builder.build();
        return new StageResult<>(mipsCode, mipsCode);
    }
}
