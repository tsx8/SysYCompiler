# SysYCompiler AI Agent Guidelines

## Big Picture Architecture
The compiler follows a multi-stage pipeline ([Pipeline.java](src/top/tsxb/compiler/driver/Pipeline.java)):
- **Frontend**: Lexer -> Parser (CST) -> Semantic (AST + Symbol Table).
- **Middle-end**: IR Generation (SSA) -> Optimization Passes ([PassManager.java](src/top/tsxb/compiler/backend/opti/PassManager.java)).
- **Backend**: MIPS Code Generation -> Register Allocation -> Peephole Opt.

### Key Data Flows
- **CST to AST**: [AstBuilder.java](src/top/tsxb/compiler/frontend/semantic/AstBuilder.java) implements `CstVisitor` to transform CST nodes into AST.
- **AST to IR**: [IrBuilder.java](src/top/tsxb/compiler/backend/llvm/IrBuilder.java) implements `AstVisitor` to emit SSA-based IR.
- **IR to MIPS**: [MipsBuilder.java](src/top/tsxb/compiler/backend/mips/MipsBuilder.java) iterates over IR structures to generate assembly.

## Project Structure
- `src/top/tsxb/compiler/`:
    - `driver/`: Entry point and `Pipeline` management.
    - `frontend/`: `lexer`, `parser` (CST), `semantic` (AST/Symbol Table).
    - `ir/`: SSA-based Intermediate Representation (`base`, `inst`, `structure`).
    - `backend/`: `llvm` (IR Gen), `mips` (Code Gen), `opti` (Middle-end passes).
- `testcases/`: Organized by stage (e.g., `testcases/mips/testcase1`).

## Developer Workflow
No Maven/Gradle; use raw `javac`.

### Build & Run (PowerShell)
- **Compile**: `Get-ChildItem -Path src -Filter *.java -Recurse | ForEach-Object { $_.FullName } > sources.txt; javac -encoding UTF-8 -d out/classes "@sources.txt"`
- **Run**: `java -cp out/classes top.tsxb.compiler.driver.Compiler`

### Testing
- **Run All**: `java -cp "out/classes;out/tests" top.tsxb.compiler.CompilerTest`
- **Run Specific**: `java -cp "out/classes;out/tests" top.tsxb.compiler.CompilerTest testcase1`
- **Caveat**: Parallel testing has a known bug where `mars.jar` may timeout. Test specific cases first.

## Project Conventions
- **Visitor Pattern**: Use `CstVisitor` for CST and `AstVisitor` for AST.
- **Error Reporting**: Use `ErrorReporter` for diagnostics; avoid throwing exceptions for user errors.
- **SSA IR**: IR is strictly SSA. Use `Mem2RegPass` to promote `alloca` to registers.
- **Config**: [CompilerConfig.java](src/top/tsxb/compiler/driver/CompilerConfig.java) gates stages (`CURRENT_HOMEWORK`), output (`OBJECT_CODE`), and `OPTIMIZE`.

## Optimization Passes
### Middle-end (IR Level)
Managed by [PassManager.java](src/top/tsxb/compiler/backend/opti/PassManager.java), running iteratively (max 15):
- **Mem2Reg**: Promotes `alloca` to SSA registers.
- **GVN/GCM**: Global Value Numbering and Global Code Motion.
- **Inlining/Unrolling**: Function inlining and loop unrolling.
- **DCE/SimplifyCFG**: Dead code elimination and CFG simplification.
- **Others**: Constant Folding, Loop Strength Reduction, Global Localization.

### Backend (MIPS Level)
Target: `FinalCycle = DIV*15 + MULT*5 + (JUMP/BRANCH)*2 + MEM*3 + OTHER*1`.
- **RegAlloc**: Graph Coloring for virtual registers; Global Variable caching.
- **Instruction Opt**: `DivOptimizer` (magic numbers), Peephole optimization.
- **Frame Opt**: Frame pointer elimination, stack space optimization.

## 🔗 Integration & Dependencies
- **Runtime**: `assets/libsysy` (SysY library).
- **Simulator**: `assets/mars.jar` (MIPS execution/profiling).
- **Logs**: Results and `FinalCycle` stats in `out/logs/`.
