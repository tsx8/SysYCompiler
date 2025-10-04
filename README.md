# 作业1 - 文法解读

要求见 [Question.md](Question.md)。

项目结构如下：

```
.
├── doc/
│   └── Question.md
├── testfile1/
│   ├── testfile1.txt
│   ├── input1.txt
│   └── output1.txt
├── testfile2/
│   ├── testfile2.txt
│   ├── input2.txt
│   └── output2.txt
├── ...
├── .gitignore
├── judge.py
├── run_test.py
├── create_zip.py
└── README.md
```

- 每个 `testfile*` 文件夹下都是具体的测试程序以及对应的样例。
- `judge.py` 是使用 AI 编写的测评程序，测试对应的测试程序是否满足题目要求。
- `run_test.py` 是使用 AI 编写的编译程序，能够将对应的测试程序补全头文件和 `getint()` 函数之后进行测试，并根据文件夹下的输入文件生成对应的输出文件。
- `create_zip.py` 是使用 AI 编写的打包程序，一次性将 `testfile*` 文件夹下的所有测试程序和用例打包为 `testfiles.zip`。
