package top.tsxb.compiler.frontend.semantic;

import top.tsxb.compiler.frontend.semantic.ast.CompUnit;
import top.tsxb.compiler.frontend.semantic.sym.SymbolTable;

public record SemanticResult(CompUnit astRoot, SymbolTable symtab) {}
