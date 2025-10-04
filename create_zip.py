import os
import zipfile
import sys
import glob

def detect_test_directories():
    all_matches = glob.glob('testfile*')
    detected_dirs = sorted([d for d in all_matches if os.path.isdir(d)])
    return detected_dirs

def validate_files(base_dirs):
    print("开始校验文件完整性...")
    all_found = True
    for dir_name in base_dirs:
        dir_index = dir_name.replace('testfile', '')
        if not dir_index.isdigit():
            print(f"警告: 目录 '{dir_name}' 名称不规范，跳过校验。")
            continue

        required_files = [
            f"{dir_name}.txt",
            f"input{dir_index}.txt",
            f"output{dir_index}.txt"
        ]
        
        for file_name in required_files:
            file_path = os.path.join(dir_name, file_name)
            if not os.path.isfile(file_path):
                print(f"错误: 文件 '{file_path}' 不存在！")
                all_found = False
    
    if all_found:
        print("校验成功！所有文件均已找到。")
    else:
        print("\n校验失败。请根据以上错误提示检查你的文件结构。")
        
    return all_found

def create_submission_zip(zip_filename, base_dirs):
    print(f"\n正在创建压缩文件: {zip_filename}")
    try:
        with zipfile.ZipFile(zip_filename, 'w', zipfile.ZIP_DEFLATED) as zipf:
            added_filenames = set()

            for dir_name in base_dirs:
                print(f"  -> 正在添加目录: {dir_name}")
                dir_index = dir_name.replace('testfile', '')
                if not dir_index.isdigit():
                    print(f"警告: 目录 '{dir_name}' 名称不规范，跳过压缩。")
                    continue
                
                files_to_add = [
                    f"{dir_name}.txt",
                    f"input{dir_index}.txt",
                    f"output{dir_index}.txt"
                ]
                
                for file_name in files_to_add:
                    local_path = os.path.join(dir_name, file_name)
                    archive_path = os.path.basename(local_path)
                    
                    if archive_path in added_filenames:
                        print(f"     - 警告: 文件名冲突! '{archive_path}' 已存在于压缩包中，将被覆盖。")

                    if os.path.exists(local_path):
                        zipf.write(local_path, arcname=archive_path)
                        print(f"     - 已添加: '{local_path}' -> '{archive_path}'")
                        added_filenames.add(archive_path)
        
        print(f"\n成功！压缩文件 '{zip_filename}' 已创建。")

    except Exception as e:
        print(f"\n创建压缩文件时发生错误: {e}")
        sys.exit(1)

if __name__ == "__main__":
    test_directories = detect_test_directories()
    
    if not test_directories:
        print("错误: 未在当前目录下找到任何 'testfile*' 文件夹。")
        print("请确保你的目录结构正确，例如存在 'testfile1', 'testfile2' 等文件夹。")
        sys.exit(1)
    
    print(f"检测到以下目录将被处理: {', '.join(test_directories)}")
    
    if not validate_files(test_directories):
        sys.exit(1)

    zip_filename = f"testfiles.zip"
    create_submission_zip(zip_filename, test_directories)