package top.tsxb.compiler.frontend;

import top.tsxb.compiler.common.CompilerStage;
import top.tsxb.compiler.common.ErrorReporter;
import top.tsxb.compiler.frontend.cst.CstNode;
import top.tsxb.compiler.frontend.cst.TokenStream;
import top.tsxb.compiler.frontend.parser.Parser;
import top.tsxb.compiler.frontend.cst.SyntaxPrinter;

public class ParserStage implements CompilerStage<TokenStream, CstNode> {
    @Override
    public StageResult<CstNode> process(TokenStream tokenStream, ErrorReporter errorReporter) {
        Parser parser = new Parser(tokenStream, errorReporter);
        CstNode compUnit = parser.parse();
        String report = report(compUnit.accept(new SyntaxPrinter()), errorReporter);

        return new StageResult<>(compUnit, report);
    }
}
