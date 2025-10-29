package top.tsxb.compiler.frontend;

import top.tsxb.compiler.common.CompilerStage;
import top.tsxb.compiler.common.ErrorReporter;
import top.tsxb.compiler.frontend.cst.TokenStream;
import top.tsxb.compiler.frontend.lexer.Lexer;

public class LexerStage implements CompilerStage<String, TokenStream> {
    @Override
    public StageResult<TokenStream> process(String sourceCode, ErrorReporter errorReporter) {
        Lexer lexer = new Lexer(sourceCode, errorReporter);
        TokenStream tokenStream = lexer.scan();
        String report = report(tokenStream.getOutput(), errorReporter);

        return new StageResult<>(tokenStream, report);
    }
}
