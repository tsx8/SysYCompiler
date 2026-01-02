# Repository Guidelines

This document provides essential information for contributors to the SysYCompiler project.

## Project Structure & Module Organization

The compiler is organized into a multi-stage pipeline:

- `src/top/tsxb/compiler/`:
    - `frontend/`: Lexer, Parser (CST), and Semantic Analysis (AST/Symbol Table).
    - `ir/`: SSA-based Intermediate Representation definitions.
    - `backend/`: LLVM IR generation, MIPS code generation, and optimization passes.
    - `driver/`: Entry point ([Compiler.java](src/top/tsxb/compiler/driver/Compiler.java)) and pipeline management.
- `test/`: Test framework source code.
- `testcases/`: Organized by stage (e.g., `lexer/`, `llvm/`, `mips/`).
- `assets/`: Grammar definitions (`SysY.g4`), runtime library (`libsysy/`), MIPS simulator (`mars.jar`) and informations about every homework.

## Build, Test, and Development Commands

The project uses raw `javac` for compilation. Always run specific test first to avoid long time testing.

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
- **Naming**: Use `PascalCase` for classes and `camelCase` for methods and variables.
- **Patterns**: Strictly follow the **Visitor Pattern** for CST (`CstVisitor`) and AST (`AstVisitor`) traversals.
- **Error Handling**: Use `ErrorReporter` for diagnostics instead of throwing exceptions for user-level errors.

## Testing Guidelines

- **Framework**: Custom testing suite in `top.tsxb.compiler.CompilerTest`.
- **Coverage**: Ensure new features pass all relevant cases in `testcases/`.
- **Caveat**: Avoid parallel testing if using `mars.jar` due to known timeout issues.

## Commit & Pull Request Guidelines

- **Commit Messages**: Always use English. Follow the `type: description` convention:
    - `feat`: New features.
    - `fix`: Bug fixes.
    - `perf`: Optimization changes.
    - `refactor`: Code restructuring.
    - `docs`: Documentation updates.
    - `tests`: Adding or updating tests.
- **Pull Requests**: Provide a concise summary of changes and link any related issues.

## Architecture Overview

The compiler follows a standard frontend-middle-backend split. The middle-end performs SSA-based optimizations (Mem2Reg, GVN, DCE, etc.) managed by `PassManager`. The backend targets MIPS with graph-coloring register allocation and peephole optimizations.
