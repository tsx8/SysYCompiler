# Repository Guidelines

This document provides essential information for contributors to the SysYCompiler project.

## Project Structure & Module Organization

The compiler follows a multi-stage pipeline structure:

- [src/top/tsxb/compiler/](src/top/tsxb/compiler/):
    - `frontend/`: Lexer, Parser (CST), and Semantic Analysis (AST/Symbol Table).
    - `ir/`: SSA-based Intermediate Representation definitions.
    - `backend/`: LLVM IR generation, MIPS code generation, and optimization passes.
    - `common/`: Shared utilities and base classes.
    - `driver/`: Entry point ([Compiler.java](src/top/tsxb/compiler/driver/Compiler.java)) and pipeline management.
- [test/](test/): Custom testing framework source code.
- [testcases/](testcases/): Test cases organized by stage (e.g., `lexer/`, `llvm/`, `mips/`).
- [assets/](assets/): Grammar definitions (`SysY.g4`), runtime library (`libsysy/`), MIPS simulator (`mars.jar`), and homework specifications.
- [docs/](docs/): Project documentation written in Typst (modified from `typst-ori` template).
    - `optimize.typ`: Optimization documentation.
    - `report.typ`: Design documentation.
    - `summary.typ`: Summary and reflections.

## Build, Test, and Development Commands

The project uses raw `javac` for compilation. Run these commands in PowerShell:

- **Compile Source**:
  ```powershell
  Get-ChildItem -Path src -Filter *.java -Recurse | ForEach-Object { $_.FullName } > sources.txt; javac -encoding UTF-8 -d out/classes "@sources.txt"
  ```
- **Run Compiler**:
  ```powershell
  java -cp out/classes top.tsxb.compiler.driver.Compiler
  ```
- **Run All Tests**:
  ```powershell
  java -cp "out/classes;out/tests" top.tsxb.compiler.CompilerTest
  ```
- **Run Specific Test**:
  ```powershell
  java -cp "out/classes;out/tests" top.tsxb.compiler.CompilerTest <testcase_name>
  ```

## Coding Style & Naming Conventions

- **Language**: Java 8+.
- **Indentation**: 4 spaces.
- **Naming**: `PascalCase` for classes, `camelCase` for methods and variables.
- **Patterns**: Strictly follow the **Visitor Pattern** for CST (`CstVisitor`) and AST (`AstVisitor`) traversals.
- **Error Handling**: Use `ErrorReporter` for diagnostics; avoid throwing exceptions for user-level errors.
- **Determinism**: Use `LinkedHashSet` or `LinkedHashMap` when iterating over IR elements to ensure consistent optimization results.

## Testing Guidelines

- **Framework**: Custom suite in [CompilerTest.java](test/top/tsxb/compiler/CompilerTest.java).
- **Coverage**: Ensure new features pass all relevant cases in [testcases/](testcases/).
- **Execution**: Always run specific tests first to verify local changes before running the full suite.
- **Caveat**: Avoid parallel testing when using `mars.jar` due to known timeout issues.

## Documentation Guidelines

The project documentation is located in the `docs/` directory and is written in Typst.

- **Validation**: When modifying documentation, ensure to compile the source file to check for errors or important warnings:
  ```powershell
  typst compile docs/<filename>.typ
  ```

## Commit & Pull Request Guidelines

- **Commit Messages**: Follow the `type: description` convention (e.g., `feat`, `fix`, `perf`, `refactor`, `docs`, `tests`).
- **Pull Requests**: Provide a concise summary of changes and link any related issues.

## Architecture Overview

The compiler features a standard frontend-middle-backend split. The middle-end performs SSA-based optimizations (Mem2Reg, GVN, DCE) managed by `PassManager`. The backend targets MIPS with graph-coloring register allocation and peephole optimizations.
