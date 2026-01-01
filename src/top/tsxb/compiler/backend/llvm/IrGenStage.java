package top.tsxb.compiler.backend.llvm;

import top.tsxb.compiler.common.CompilerStage;
import top.tsxb.compiler.common.ErrorReporter;
import top.tsxb.compiler.frontend.semantic.ast.AstNode;
import top.tsxb.compiler.ir.structure.Module;

public class IrGenStage implements CompilerStage<AstNode, Module> {
    @Override
    public StageResult<Module> process(AstNode astRoot, ErrorReporter errorReporter) {
        Module module = new Module();
        IrBuilder builder = new IrBuilder(module);

        astRoot.accept(builder);

        String report = report(module.toString(), errorReporter);
        return new StageResult<>(module, report);
    }
}
