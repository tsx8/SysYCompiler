package top.tsxb.compiler.frontend;

import top.tsxb.compiler.common.CompilerStage;
import top.tsxb.compiler.common.ErrorReporter;
import top.tsxb.compiler.frontend.semantic.AstBuilder;
import top.tsxb.compiler.frontend.semantic.SemanticResult;
import top.tsxb.compiler.frontend.semantic.TypeChecker;
import top.tsxb.compiler.ir.ast.CompUnit;
import top.tsxb.compiler.ir.cst.CstNode;
import top.tsxb.compiler.ir.symtab.SymbolTable;

public class SemanticStage implements CompilerStage<CstNode, SemanticResult> {
    @Override
    public StageResult<SemanticResult> process(CstNode cstRoot, ErrorReporter errorReporter) {
        AstBuilder astBuilder = new AstBuilder();
        CompUnit astRoot = astBuilder.build(cstRoot);

        SymbolTable symbolTable = new SymbolTable();

        TypeChecker typeChecker = new TypeChecker(symbolTable, errorReporter);
        astRoot.accept(typeChecker);

        String report = report(symbolTable.getOutput(), errorReporter);

        SemanticResult semanticResult = new SemanticResult(astRoot, symbolTable);

        return new StageResult<>(semanticResult, report);
    }
}
