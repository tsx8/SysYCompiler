# Repository Guidelines

## Project Structure & Module Organization
Java sources live in `src/top/tsxb/compiler`, grouped into `driver` (Compiler entry + `Pipeline`), `frontend` (lexer/parser/semantic), and `backend/{llvm,mips,opti}`. Each backend sub-package contains its respective `Stage` entry (e.g., `IrGenStage`, `MipsGenStage`, `OptimizeStage`) and builders (`IrBuilder`, `MipsBuilder`). Tests and helpers sit in `test/top/tsxb/compiler`, while executable fixtures belong in `testcases/<stage>/<case>` (each case supplies `testfile.txt`, `ans.txt`, and optional `in.txt`). Runtime dependencies (`assets/libsysy`, `assets/mars.jar`) support execution, and any compiled classes or logs should be written under `out/`.

## Build, Test, and Development Commands
Use JDK 17+; the repo deliberately avoids Maven/Gradle, so compile directly. Example (bash; swap `:` for `;` on Windows):
```bash
find src -name "*.java" > sources.txt && javac -encoding UTF-8 -d out/classes @sources.txt
java -cp out/classes top.tsxb.compiler.driver.Compiler
find test -name "*.java" > tests.txt && javac -encoding UTF-8 -cp out/classes -d out/tests @tests.txt
# Run all tests:
java -cp "out/classes:out/tests" top.tsxb.compiler.CompilerTest
# Run specific test cases:
java -cp "out/classes:out/tests" top.tsxb.compiler.CompilerTest testcase1 testcase2
```
Set `CompilerConfig.CURRENT_HOMEWORK`, `OBJECT_CODE`, and `OPTIMIZE` before running and clean `out/logs` between benchmark sessions.

## Coding Style & Naming Conventions
Indent four spaces, keep `UpperCamelCase` classes, `lowerCamelCase` members, and `UPPER_SNAKE_CASE` constants. Implement new traversals by extending the existing `CstVisitor`/`AstVisitor` hierarchies and define grammar additions through the DSL utilities in `Parser.java`. Use `var` only when the type is obvious, prefer `final` for shared dependencies, emit diagnostics through `ErrorReporter`/`BacktrackMgr`, and extend `IrBuilder`/`MipsBuilder` incrementally instead of creating new output layers.

## Testing Guidelines
`CompilerTest` picks cases from `testcases/<CURRENT_HOMEWORK>` and writes logs under `out/logs/<timestamp>`, so run it after every semantic, IR, or MIPS change. You can pass specific test case names as arguments to run only those cases (e.g., `java ... CompilerTest testcase1`). Keep folder names short so console alignment remains readable, and version-control both `testfile.txt` and `ans.txt` for every new case. Capture `FinalCycle` measurements with `assets/mars.jar` whenever backend logic changes.

## Commit & Pull Request Guidelines
Commits follow the `type: short imperative summary` style already in history (`feat:`, `refactor:`, `docs:`). Pull requests must call out the affected stage or builder, the compiler switches used (`CURRENT_HOMEWORK`, `OBJECT_CODE`, `OPTIMIZE`), the commands/tests executed, and any performance impact (FinalCycle or IR diff). Link homework issues when relevant and update `CHANGELOG` for stage milestones.

## Configuration & Optimization Notes
`CompilerConfig` centralizes runtime switches: `CURRENT_HOMEWORK` gates parser/semantic/llvm/mips flows (note: `optimize` is NOT a valid homework stage), `OBJECT_CODE` selects IR vs MIPS output, and `OPTIMIZE` is a boolean toggle that enables middle-end passes. Currently, middle-end optimizations include **Mem2Reg**, **Dead Code Elimination (DCE)**, and **Constant Propagation/Folding**. Backend optimizations include **multiplication/division** optimization and **Linear Scan Register Allocation**. Optimize toward the official score `FinalCycle = DIV*15 + MULT*5 + (JUMP/BRANCH)*2 + MEM*3 + OTHER*1`, prioritizing memory- and branch-reduction even if total instruction count rises slightly.
