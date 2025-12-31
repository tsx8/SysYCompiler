# SysYCompiler Copilot Instructions

## Project Overview
SysYCompiler is a Java-based compiler for the SysY language. It follows a multi-stage pipeline architecture, transforming source code through CST, AST, and LLVM IR, ultimately generating **MIPS machine code**. The current focus is **Speed Ranking Optimization** to minimize the execution cycle count on the MARS simulator.

## Architecture & Data Flow
- **Pipeline**: Orchestrated by [Pipeline.java](../src/top/tsxb/compiler/driver/Pipeline.java). It executes stages sequentially.
- **Stages**: Each stage implements `CompilerStage`.
    - `LexerStage`: `String` -> `TokenStream`
    - `ParserStage`: `TokenStream` -> `CstNode` (Concrete Syntax Tree)
    - `SemanticStage`: `CstNode` -> `AstNode` (Abstract Syntax Tree) + Type Checking
    - `IrGenStage`: `AstNode` -> `Module` (LLVM IR). Logic in [IrBuilder.java](../src/top/tsxb/compiler/backend/llvm/IrBuilder.java).
    - `MipsGenStage`: `Module` -> MIPS Assembly. Logic in [MipsBuilder.java](../src/top/tsxb/compiler/backend/mips/MipsBuilder.java).
- **Tree Traversal**: Both CST and AST use the **Visitor Pattern**. Implement `CstVisitor` or `AstVisitor` for new traversals.
- **Error Handling**: Use `ErrorReporter` and `BacktrackMgr`. `BacktrackMgr` allows saving/restoring state of `TokenStream` and `ErrorReporter` for recursive descent.

## Critical Workflows
- **Configuration**: All global settings are in [CompilerConfig.java](../src/top/tsxb/compiler/driver/CompilerConfig.java).
    - `CURRENT_HOMEWORK`: Switch between stages (e.g., `"lexer"`, `"parser"`, `"semantic"`, `"llvm"`, `"mips"`).
    - `OBJECT_CODE`: Set to `"llvm"` or `"mips"`.
    - `OPTIMIZE`: Global toggle for all optimization passes. When `false`, the compiler should generate baseline, unoptimized code.
- **Running**: Execute the `main` method in [Compiler.java](../src/top/tsxb/compiler/driver/Compiler.java).
- **Testing**: Execute [CompilerTest.java](../test/top/tsxb/compiler/CompilerTest.java). It picks test cases from `testcases/` based on `CURRENT_HOMEWORK`.

## Optimization & Speed Ranking
- **Goal**: Minimize `FinalCycle` on MARS 4.5.
- **Metric**: `FinalCycle = DIV*15 + MULT*5 + (JUMP/BRANCH)*2 + MEM*3 + OTHER*1`.
- **Strategy**:
    - Implement optimizations (e.g., Register Allocation, DCE, Inline) that can be globally toggled via `CompilerConfig.OPTIMIZE`.
    - Prioritize reducing memory operations (`MEM*3`) and branches (`JUMP/BRANCH*2`).

## Development Patterns
- **Parser DSL**: Use the `ParserCombinator` DSL in [Parser.java](../src/top/tsxb/compiler/frontend/parser/Parser.java) (e.g., `seq`, `or`, `many`, `opt`, `term`, `rule`) to define grammar rules.
- **CST Helpers**: Use the `Cst` static helper class in [AstBuilder.java](../src/top/tsxb/compiler/frontend/semantic/AstBuilder.java) for common operations like `find`, `findAll`, `peel`, and `buildAll`.
- **MIPS Generation**:
    - Target **MARS 4.5** simulator (basic and pseudo instructions only, no macros).
    - Register conventions: `$v0-$v1` (returns), `$a0-$a3` (args), `$t0-$t9` (temps), `$s0-$s7` (saved).
    - Performance Metric: `FinalCycle = DIV*15 + MULT*5 + (JUMP/BRANCH)*2 + MEM*3 + OTHER*1`.

## External Dependencies
- **LLVM**: `clang`, `llvm-link`, and `lli` are required for IR execution.
- **MIPS**: MARS 4.5 ([assets/mars.jar](../assets/mars.jar)) is used for execution and benchmarking.
- **Runtime**: [libsysy.c](../assets/libsysy/libsysy.c) provides SysY runtime functions.
