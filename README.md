# SysYCompiler

> Fxxk u C++

这是我的编译技术课设编译器源代码仓库。

## INTRO

《编译技术》要求实现一个类 C 语言的 SysY 语言的编译器，主要分为文法解读、词法分析、语法分析、语义分析、代码生成I、代码生成II 和竞速排序这几个作业。每个作业的官方文档详见 `assets/`。

`assets/` 还存放着：

1. SysY 语言的文法定义，有 Markdown 形式（官方提供）和 Antlr4 形式（AI 转写）。2
2. llvm ir 中间代码运行所需的 `libsysy` 库的源代码，我进行了重构，去掉了对标准库的依赖，删除了今年不使用的几个运行时函数。
3. 课程组提供的 `mars.jar`，用于 mips 汇编代码测试运行和性能统计。
4. 课程组提供的竞速排序代码优化文档。
5. 我在竞速排序阶段使用的油猴脚本。安装之后打开排行榜页面生效，可以记录历史提交数据的周期数，以及计算所有测试样例的平均排名。

`docs/` 目录中存放的是课程组要求提交的优化文档、设计文档和总结感想文档。

`src/` 是编译器源代码目录，`test/` 是测试代码目录，`testcases/` 是测试数据目录。

其他详见 `AGENTS.md`。AI 总结得已经很到位了。具体内容还是见代码和文档吧。

对本仓库的分支说明如下：

- `master` 分支存放编译器主体代码，是第二次作业开始维护的代码。
- `hw1` 分支存放第一次作业“文法解读”的代码。

对本仓库的标签说明如下：

- `lexer`: 词法分析作业的最后提交
- `parser`: 语法分析作业的最后提交
- `sema`: 语义分析作业的最后提交
- `llvmir`: 代码生成I 作业的中间代码最后提交
- `mips`: 代码生成II 作业的目标代码最后提交
- `speed`: 竞速排序最后提交

## CHANGELOG

- [2026.01.05] Confirmed last commit on OJ.
- [2026.01.04] Finished docs part.
- [2026.01.04] Finished opti part.
- [2025.12.31] Finished mips part.
- [2025.12.07] Finished llvm_ir part.
- [2025.11.06] Finished semantic part.
- [2025.10.30] Finished parser part.
- [2025.10.13] Finished lexer part.
- [2025.10.04] Refactor from the old repo.