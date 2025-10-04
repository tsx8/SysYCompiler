import sys
import os
import subprocess
import re

GETINT_IMPL = """
int getint() {
    int t;
    scanf("%d", &t);
    while (getchar() != '\\n');
    return t;
}
"""

def run_test_case(folder_path):
    print(f"--- 正在处理测试文件夹: {folder_path} ---")

    if not os.path.isdir(folder_path):
        print(f"错误: 文件夹 '{folder_path}' 不存在。")
        return

    folder_name = os.path.basename(folder_path)
    
    match = re.search(r'\d+$', folder_name)
    if not match:
        print(f"错误: 无法从文件夹名称 '{folder_name}' 中提取编号。")
        return
    case_number = match.group(0)

    source_file = os.path.join(folder_path, f"{folder_name}.txt")
    input_file = os.path.join(folder_path, f"input{case_number}.txt")
    output_file = os.path.join(folder_path, f"output{case_number}.txt")
    
    temp_c_file = "temp_source.c"
    temp_executable = "temp_executable.exe"

    if not os.path.exists(source_file):
        print(f"错误: 源文件 '{source_file}' 未找到。")
        return
    if not os.path.exists(input_file):
        print(f"错误: 输入文件 '{input_file}' 未找到。")
        return

    try:
        with open(source_file, 'r', encoding='utf-8') as f:
            sysy_code = f.read()
        
        c_code = f"#include <stdio.h>\n\n{GETINT_IMPL}\n{sysy_code}"

        with open(temp_c_file, 'w', encoding='utf-8') as f:
            f.write(c_code)
        print(f"已生成临时C文件: {temp_c_file}")

    except Exception as e:
        print(f"错误: 读写文件时出错 - {e}")
        return

    print("正在使用 gcc 编译...")
    compile_command = ["gcc", temp_c_file, "-o", temp_executable, "-w"]
    compile_result = subprocess.run(compile_command, capture_output=True, text=True)

    if compile_result.returncode != 0:
        print("!!! 编译失败 !!!")
        print("编译器错误信息:")
        print(compile_result.stderr)
        if os.path.exists(temp_c_file):
            os.remove(temp_c_file)
        return
    
    print("编译成功！")

    print(f"正在运行程序，输入来自: {input_file}")
    try:
        with open(input_file, 'r', encoding='utf-8') as f_in:
            run_result = subprocess.run(
                [f"./{temp_executable}"],
                stdin=f_in,
                capture_output=True,
                text=True,
                timeout=5
            )
        
        if run_result.returncode != 0:
            print(f"!!! 程序运行时发生错误 (返回码: {run_result.returncode}) !!!")
            print("程序错误输出:")
            print(run_result.stderr)
        else:
            print("程序运行成功。")

        program_output = run_result.stdout
        with open(output_file, 'w', encoding='utf-8') as f_out:
            f_out.write(program_output)
        print(f"已将程序输出写入到: {output_file}")

    except subprocess.TimeoutExpired:
        print("!!! 程序运行超时 !!!")
    except Exception as e:
        print(f"!!! 运行或写入输出时发生未知错误: {e} !!!")

    finally:
        if os.path.exists(temp_c_file):
            os.remove(temp_c_file)
        if os.path.exists(temp_executable):
            os.remove(temp_executable)
        print("临时文件已清理。")
        print(f"--- 处理完成: {folder_path} ---\n")


if __name__ == '__main__':
    if len(sys.argv) != 2:
        print("用法: python run_test.py <文件夹路径>")
        print("例如: python run_test.py testfile1")
        sys.exit(1)
    
    target_folder = sys.argv[1]
    run_test_case(target_folder)