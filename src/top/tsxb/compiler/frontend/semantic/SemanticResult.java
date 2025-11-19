package top.tsxb.compiler.frontend.semantic;

import top.tsxb.compiler.ir.ast.CompUnit;
import top.tsxb.compiler.ir.symtab.SymbolTable;

public record SemanticResult(CompUnit astRoot, SymbolTable symtab) {}
