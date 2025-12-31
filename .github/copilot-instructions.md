# SysYCompiler Copilot Instructions

## Project Overview
SysYCompiler is a Java-based compiler for the SysY language. While it currently targets LLVM IR, the ultimate goal is to generate **MIPS machine code** for performance-oriented speed ranking.

## Architecture & Data Flow
- **Pipeline**: Orchestrated by `top.tsxb.compiler.driver.Pipeline`. It executes stages sequentially.
- **Stages**: Each stage implements `top.tsxb.compiler.common.CompilerStage`.
    - `LexerStage`: `String` (Source) -> `TokenStream`
    - `ParserStage`: `TokenStream` -> `CstNode` (Concrete Syntax Tree)
    - `SemanticStage`: `CstNode` -> `AstNode` (Abstract Syntax Tree) + Type Checking
    - `IrGenStage`: `AstNode` -> `Module` (LLVM IR)
    - **Upcoming**: `MipsGenStage`: `Module` (LLVM IR) -> MIPS Assembly
- **Tree Traversal**: Both CST and AST use the **Visitor Pattern**. Implement `CstVisitor` or `AstVisitor` for new traversals.
- **Error Handling & Backtracking**: Use `top.tsxb.compiler.common.ErrorReporter` and `top.tsxb.compiler.common.BacktrackMgr`. The `BacktrackMgr` allows saving and restoring the state of both the `TokenStream` and `ErrorReporter`, which is critical for the recursive descent parser.

## Critical Workflows
- **Configuration**: All global settings are in `top.tsxb.compiler.driver.CompilerConfig`.
    - Change `CURRENT_HOMEWORK` to switch between stages (e.g., `"lexer"`, `"parser"`, `"semantic"`, `"llvm"`, `"mips"`).
    - `OBJECT_CODE` can be set to `"llvm"` or `"mips"`.
    - Ensure `CLANG_PATH`, `LLVM_LINK_PATH`, and `LLI_PATH` are correctly set for IR execution.
- **Running the Compiler**: Execute the `main` method in `top.tsxb.compiler.driver.Compiler`.
- **Running Tests**: Execute `top.tsxb.compiler.CompilerTest`. It automatically picks up test cases from `testcases/` based on the `CURRENT_HOMEWORK` setting.

## Development Patterns & Optimization Goals
- **MIPS Generation**:
    - Use LLVM IR as the intermediate representation.
    - Target MARS 4.5 simulator (basic and pseudo instructions only, no macros).
- **Optimization (Speed Ranking)**:
    - **Register Allocation**: Implement global allocation (e.g., Graph Coloring) and a temporary register pool.
    - **Data Flow Analysis**: Required for advanced optimizations like Dead Code Elimination (DCE) and Loop Invariant Code Motion (LICM).
    - **Switchable Optimization**: Ensure optimizations can be toggled via `CompilerConfig`.
- **Adding a Syntax Rule**:
    1. Update `assets/SysY.g4` for reference.
    2. Update `top.tsxb.compiler.frontend.parser.Parser`. It uses a manual recursive descent approach with `ParserCombinator` rules.
    3. Add corresponding `CstNode` types in `top.tsxb.compiler.frontend.parser.cst`.
- **Adding an AST Node**:
    1. Create a new class in `top.tsxb.compiler.frontend.semantic.ast`.
    2. Update `AstVisitor` interface.
    3. Update `AstBuilder` to construct the new node from CST.
- **IR Generation**:
    1. IR structures are in `top.tsxb.compiler.ir`.
    2. `IrBuilder` (in `backend/llvm`) implements `AstVisitor` to generate IR.

## MIPS & MARS Simulator
- **Simulator**: Use `assets/mars.jar` (MARS 4.5).
- **Execution**: `java -jar assets/mars.jar [options] program.asm`.
    - Common options: `nc` (no copyright), `db` (delayed branching - **DISABLED** for this project), `p` (project mode).
- **Register Conventions**:
    - `$0 ($zero)`: Constant 0.
    - `$v0-$v1`: Return values.
    - `$a0-$a3`: Arguments (extra arguments on stack).
    - `$t0-$t7, $t8-$t9`: Temporary registers.
    - `$s0-$s7`: Saved/Global registers (for local variables/params).
    - `$sp`: Stack pointer, `$ra`: Return address, `$fp`: Frame pointer.
- **Constraints**: Use basic and pseudo instructions only; **no macro instructions**.
- **Performance Metric (FinalCycle)**:
    - `FinalCycle = DIV*15 + MULT*5 + (JUMP/BRANCH)*2 + MEM*3 + OTHER*1`.
    - Goal: Minimize `FinalCycle` while maintaining correctness.

## Code Quality & Boilerplate Reduction
- **Parser DSL**: Use the `ParserCombinator` DSL (`seq`, `or`, `many`, `opt`, `term`, `rule`) in `top.tsxb.compiler.frontend.parser.Parser` to define grammar rules concisely, reducing "grammar noise".
- **CST Helpers**: Use the `Cst` helper class (static inner class in `AstBuilder`) for common CST operations like `find`, `findAll`, `peel`, and `buildAll` to avoid repetitive child indexing and null checks.
- **Modern Java Features**: Leverage `record` for data carriers (e.g., `StageResult`), `switch` expressions for pattern matching (e.g., in `AstBuilder`), and `Optional` for safe value handling.
- **Visitor Pattern**: Consistently use the Visitor pattern for tree traversals (CST, AST, IR) to separate logic from data structures and maintain a clean separation of concerns.

## External Dependencies
- **LLVM Toolchain**: `clang`, `llvm-link`, and `lli` are required for the `llvm` stage and tests.
- **MIPS Simulator**: MARS 4.5 (`assets/mars.jar`) is used for executing and benchmarking MIPS code.
- **Runtime Library**: `assets/libsysy` contains the SysY runtime library (`libsysy.c`). It is precompiled to `lib.ll` during testing.
