package top.tsxb.compiler.frontend;

import top.tsxb.compiler.common.CompilerStage;
import top.tsxb.compiler.common.ErrorReporter;
import top.tsxb.compiler.frontend.ast.AstNode;
import top.tsxb.compiler.frontend.ast.CompUnit;
import top.tsxb.compiler.frontend.ast.SymbolCollector;
import top.tsxb.compiler.frontend.ast.TypeChecker;
import top.tsxb.compiler.frontend.cst.AstBuilder;
import top.tsxb.compiler.frontend.cst.CstNode;
import top.tsxb.compiler.frontend.semantic.SymbolTable;

public class SemanticStage implements CompilerStage<CstNode, AstNode> {
    @Override
    public StageResult<AstNode> process(CstNode cstRoot, ErrorReporter errorReporter) {
        AstBuilder astBuilder = new AstBuilder();
        CompUnit astRoot = astBuilder.build(cstRoot);

        SymbolTable symbolTable = new SymbolTable();

        SymbolCollector symbolCollector = new SymbolCollector(symbolTable, errorReporter);
        astRoot.accept(symbolCollector);

        TypeChecker typeChecker = new TypeChecker(symbolTable, errorReporter);
        astRoot.accept(typeChecker);

        String report = report(symbolTable.getOutput(), errorReporter);

        return new StageResult<>(astRoot, report);
    }
}
