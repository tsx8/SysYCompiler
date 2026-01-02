# Repository Guidelines

## Project Structure & Module Organization
Java sources live in `src/top/tsxb/compiler`, grouped into `driver` (Compiler entry + thread-safe `Pipeline`), `frontend` (lexer/parser/semantic), `backend/{llvm,mips,opti}`, and `ir/{base,constant,inst,structure}`. Each backend sub-package contains its respective `Stage` entry (e.g., `IrGenStage`, `MipsGenStage`, `OptimizeStage`) and builders (`IrBuilder`, `MipsBuilder`). The `common` package houses utilities like `ErrorReporter` and `Runner` (for LLVM execution). Tests and helpers sit in `test/top/tsxb/compiler`, while executable fixtures belong in `testcases/<stage>/<case>` (each case supplies `testfile.txt`, `ans.txt`, and optional `in.txt`). Runtime dependencies (`assets/libsysy`, `assets/mars.jar`) support execution, and any compiled classes or logs should be written under `out/`.

## Build, Test, and Development Commands
Use JDK 17+; the repo deliberately avoids Maven/Gradle, so compile directly.

### Bash (Linux/macOS/Git Bash)
```bash
find src -name "*.java" > sources.txt && javac -encoding UTF-8 -d out/classes @sources.txt
java -cp out/classes top.tsxb.compiler.driver.Compiler
find test -name "*.java" > tests.txt && javac -encoding UTF-8 -cp out/classes -d out/tests @tests.txt
# Run all tests:
java -cp "out/classes:out/tests" top.tsxb.compiler.CompilerTest
# Run specific test cases:
java -cp "out/classes:out/tests" top.tsxb.compiler.CompilerTest testcase1 testcase2
```

### PowerShell (Windows)
```powershell
Get-ChildItem -Path src -Filter *.java -Recurse | ForEach-Object { $_.FullName } > sources.txt; javac -encoding UTF-8 -d out/classes "@sources.txt"
java -cp out/classes top.tsxb.compiler.driver.Compiler
Get-ChildItem -Path test -Filter *.java -Recurse | ForEach-Object { $_.FullName } > tests.txt; javac -encoding UTF-8 -cp out/classes -d out/tests "@tests.txt"
# Run all tests:
java -cp "out/classes;out/tests" top.tsxb.compiler.CompilerTest
# Run specific test cases:
java -cp "out/classes;out/tests" top.tsxb.compiler.CompilerTest testcase1 testcase2
```
Set `CompilerConfig.CURRENT_HOMEWORK`, `OBJECT_CODE`, and `OPTIMIZE` before running and clean `out/logs` between benchmark sessions.

## Coding Style & Naming Conventions
Indent four spaces, keep `UpperCamelCase` classes, `lowerCamelCase` members, and `UPPER_SNAKE_CASE` constants. Implement new traversals by extending the existing `CstVisitor`/`AstVisitor` hierarchies and define grammar additions through the DSL utilities in `Parser.java`. Use `var` only when the type is obvious, prefer `final` for shared dependencies, emit diagnostics through `ErrorReporter`/`BacktrackMgr`, and extend `IrBuilder`/`MipsBuilder` incrementally instead of creating new output layers.

## Testing Guidelines
`CompilerTest` picks cases from `testcases/<CURRENT_HOMEWORK>` and writes logs under `out/logs/<timestamp>`, so run it after every semantic, IR, or MIPS change. By default, it uses **parallel execution** to leverage multi-core processors. **Note: Parallel testing has a known bug where `mars.jar` may timeout and cause a CRASH; if this occurs, it can be ignored or the test can be re-run.** To avoid this and speed up the process, it is recommended to **test only a small subset of relevant testcases** by passing their names as arguments (e.g., `java ... CompilerTest testcase1 testcase2`). For manual LLVM integration and execution, use the `Runner` utility in the `common` package. Keep folder names short so console alignment remains readable, and version-control both `testfile.txt` and `ans.txt` for every new case. Capture `FinalCycle` measurements with `assets/mars.jar` whenever backend logic changes.

## Commit & Pull Request Guidelines
Commits follow the `type: short imperative summary` style already in history (`feat:`, `refactor:`, `docs:`). Pull requests must call out the affected stage or builder, the compiler switches used (`CURRENT_HOMEWORK`, `OBJECT_CODE`, `OPTIMIZE`), the commands/tests executed, and any performance impact (FinalCycle or IR diff). Link homework issues when relevant and update `CHANGELOG` for stage milestones.

## Configuration & Optimization Notes
`CompilerConfig` centralizes runtime switches: `CURRENT_HOMEWORK` gates parser/semantic/llvm/mips flows (note: `optimize` is NOT a valid homework stage), `OBJECT_CODE` selects IR vs MIPS output, and `OPTIMIZE` is a boolean toggle that enables middle-end passes.

### Middle-end Optimizations (IR Level)
Middle-end passes are managed by `PassManager` and run iteratively (up to 15 times) until convergence:
- **Function Inlining**: Replaces function calls with the function body to reduce overhead.
- **Mem2Reg**: Promotes `alloca` instructions to SSA registers using dominator tree analysis, significantly reducing memory operations.
- **GVN (Global Value Numbering)**: Eliminates redundant computations by identifying and merging equivalent expressions, and performs memory forwarding to eliminate redundant loads.
- **GCM (Global Code Motion)**: Moves instructions to the least frequently executed basic blocks (hoisting/sinking) while respecting data dependencies and loop nesting levels.
- **Loop Unrolling**: Expands loop bodies to reduce branch overhead and expose more optimization opportunities.
- **Loop Strength Reduction**: Replaces expensive operations (e.g., induction variable multiplication) with cheaper ones (e.g., addition).
- **Global Localization**: Promotes global variables to local ones within functions where possible, enabling further optimizations like Mem2Reg.
- **Constant Folding & Propagation**: Evaluates constant expressions at compile time and propagates the results.
- **Simplify CFG**: Removes unreachable blocks, merges single-successor blocks, and simplifies branch instructions.
- **Dead Code Elimination (DCE)**: Removes instructions whose results are never used.

### Backend Optimizations (MIPS Level)
Backend optimizations focus on efficient code generation and resource usage:
- **Graph Coloring Register Allocation**: Assigns physical registers to virtual registers using an interference graph and heuristics (spill cost, hints), minimizing stack spills and maximizing register reuse.
- **Global Variable Register Allocation**: Assigns physical registers to frequently accessed global variables (promoted or cached) to reduce memory traffic.
- **Global Address Caching**: Caches global variable addresses in registers within `MipsBuilder` to avoid redundant `la` (load address) instructions.
- **Liveness & Loop Analysis**: Provides data flow information and loop depth heuristics to prioritize register allocation for hot code paths.
- **Stack Frame Optimization**: Eliminates the frame pointer (`$fp`) and optimizes stack space allocation to reduce function prologue/epilogue overhead.
- **Division/Multiplication Optimization**: Replaces expensive `div` and `mult` instructions with sequences of `sll`, `sra`, `add`, and `sub` using magic numbers (via `DivOptimizer`) and constant multiplication heuristics.
- **Block Reordering & Layout**: Reorders basic blocks to maximize fall-through branches and reduce explicit `j` instructions.
- **Phi Elimination Optimization**: Minimizes redundant jumps and moves during SSA deconstruction by intelligently ordering moves and handling cycles.

Optimize toward the official score `FinalCycle = DIV*15 + MULT*5 + (JUMP/BRANCH)*2 + MEM*3 + OTHER*1`, prioritizing memory- and branch-reduction even if total instruction count rises slightly.
