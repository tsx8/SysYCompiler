package top.tsxb.compiler.frontend;

import top.tsxb.compiler.common.CompilerStage;
import top.tsxb.compiler.common.ErrorReporter;
import top.tsxb.compiler.frontend.parser.cst.CstNode;
import top.tsxb.compiler.frontend.semantic.AstBuilder;
import top.tsxb.compiler.frontend.semantic.TypeChecker;
import top.tsxb.compiler.frontend.semantic.ast.AstNode;
import top.tsxb.compiler.frontend.semantic.ast.CompUnit;
import top.tsxb.compiler.frontend.semantic.sym.SymbolTable;

public class SemanticStage implements CompilerStage<CstNode, AstNode> {
    @Override
    public StageResult<AstNode> process(CstNode cstRoot, ErrorReporter errorReporter) {
        AstBuilder astBuilder = new AstBuilder();
        CompUnit astRoot = astBuilder.build(cstRoot);

        SymbolTable symbolTable = new SymbolTable();

        TypeChecker typeChecker = new TypeChecker(symbolTable, errorReporter);
        astRoot.accept(typeChecker);

        String report = report(symbolTable.getOutput(), errorReporter);

        return new StageResult<>(astRoot, report);
    }
}
