import re
import sys
import os

COVERAGE_RULES = [
    # CompUnit: 编译单元
    {'description': 'CompUnit: 存在全局声明(Decl)', 'regex': re.compile(r'^\s*(const\s+)?(static\s+)?int\s+\w+\s*[,=\[;]'), 'covered': False},
    {'description': 'CompUnit: 存在其他函数定义(FuncDef)', 'regex': re.compile(r'^\s*(void|int)\s+\w+\s*\([^)]*\)\s*\{', re.MULTILINE), 'covered': False},
    
    # Decl: 声明
    {'description': 'Decl: 是常量声明(ConstDecl)', 'regex': re.compile(r'const\s+int'), 'covered': False},
    {'description': 'Decl: 是变量声明(VarDecl)', 'regex': re.compile(r'(?<!const\s)int\s+\w+'), 'covered': False},
    
    # ConstDecl: 常量声明
    {'description': 'ConstDecl: 单个常量定义', 'regex': re.compile(r'const\s+int\s+\w+\s*=\s*[^,;]+;'), 'covered': False},
    {'description': 'ConstDecl: 多个常量定义', 'regex': re.compile(r'const\s+int\s+\w+.*?,\s*\w+.*?;'), 'covered': False},
    
    # ConstDef: 常量定义
    {'description': 'ConstDef: 普通常量', 'regex': re.compile(r'const\s+int\s+\w+\s*=\s*(?!\{)'), 'covered': False},
    {'description': 'ConstDef: 一维数组常量', 'regex': re.compile(r'const\s+int\s+\w+\s*\[[^\]]+\]\s*='), 'covered': False},
    
    # ConstInitval: 常量初值
    {'description': 'ConstInitval: 单个表达式初值', 'regex': re.compile(r'=\s*[\w\d\(\)\+\-\*\/%]+;'), 'covered': False},
    {'description': 'ConstInitval: 数组初值(有内容)', 'regex': re.compile(r'=\s*\{[^\s}].*?\}'), 'covered': False},
    {'description': 'ConstInitval: 数组初值(空)', 'regex': re.compile(r'=\s*\{\s*\}'), 'covered': False},
    
    # VarDecl: 变量声明
    {'description': 'VarDecl: 单个变量定义', 'regex': re.compile(r'^\s*(?<!const\s)(?<!static\s)int\s+\w+(\s*\[[^\]]+\])?\s*(;|=)', re.MULTILINE), 'covered': False},
    {'description': 'VarDecl: 多个变量定义', 'regex': re.compile(r'^\s*(?<!const\s)(?<!static\s)int\s+\w+.*?,\s*\w+.*?;', re.MULTILINE), 'covered': False},
    {'description': 'VarDecl: 单个static变量', 'regex': re.compile(r'static\s+int\s+\w+\s*(;|=)'), 'covered': False},
    {'description': 'VarDecl: 多个static变量', 'regex': re.compile(r'static\s+int\s+\w+.*?,\s*\w+.*?;'), 'covered': False},
    
    # VarDef: 变量定义
    {'description': 'VarDef: 定义时无初值', 'regex': re.compile(r'\sint\s+\w+(\s*\[[^\]]+\])?\s*([,;])'), 'covered': False},
    {'description': 'VarDef: 定义时有初值', 'regex': re.compile(r'\sint\s+\w+(\s*\[[^\]]+\])?\s*='), 'covered': False},
    
    # FuncDef: 函数定义
    {'description': 'FuncDef: 无形参', 'regex': re.compile(r'\b(void|int)\s+\w+\s*\(\s*\)'), 'covered': False},
    {'description': 'FuncDef: 有形参', 'regex': re.compile(r'\b(void|int)\s+\w+\s*\([^)\s][^)]*\)'), 'covered': False},
    
    # FuncType: 函数类型
    {'description': 'FuncType: void类型函数', 'regex': re.compile(r'\bvoid\s+\w+\s*\('), 'covered': False},
    {'description': 'FuncType: int类型函数', 'regex': re.compile(r'\bint\s+(?!(main)\b)\w+\s*\('), 'covered': False},
    
    # FuncFParams: 函数形参表
    {'description': 'FuncFParams: 单个形参', 'regex': re.compile(r'\w+\s*\(\s*int\s+\w+\s*(\[\s*\])?\s*\)'), 'covered': False},
    {'description': 'FuncFParams: 多个形参', 'regex': re.compile(r'\w+\s*\(.*?,.*\)'), 'covered': False},
    
    # FuncFParam: 函数形参
    {'description': 'FuncFParam: 普通变量形参', 'regex': re.compile(r'\(\s*(int\s+\w+[,)])|,\s*int\s+\w+\s*([,)])'), 'covered': False},
    {'description': 'FuncFParam: 一维数组形参', 'regex': re.compile(r'int\s+\w+\s*\[\s*\]'), 'covered': False},
    
    # Block: 语句块
    {'description': 'Block: 空语句块', 'regex': re.compile(r'\{\s*\}'), 'covered': False},
    {'description': 'Block: 有内容的语句块', 'regex': re.compile(r'\{[^\s{}][^}]*\}'), 'covered': False},
    
    # BlockItem: 语句块项
    {'description': 'BlockItem: 块内声明', 'regex': re.compile(r'\{\s*(const\s)?int\s'), 'covered': False},
    {'description': 'BlockItem: 块内语句', 'regex': re.compile(r'\{\s*(\w|if|for|while|break|continue|return|printf)'), 'covered': False},
    
    # Stmt: 语句
    {'description': 'Stmt: 赋值语句', 'regex': re.compile(r'\w+(\[\s*[^\]]+\s*\])?\s*=\s*[^=].*?;'), 'covered': False},
    {'description': 'Stmt: 表达式语句(有Exp)', 'regex': re.compile(r'^\s*(?!if|for|while|return|const|int|void|\{)\w+\(.*\);', re.MULTILINE), 'covered': False},
    {'description': 'Stmt: 表达式语句(无Exp)', 'regex': re.compile(r'^\s*;\s*$', re.MULTILINE), 'covered': False}, # 修正
    {'description': 'Stmt: if语句(无else)', 'regex': re.compile(r'if\s*\(.*?\)[^{};]*;(?!.*\belse\b)'), 'covered': False},
    {'description': 'Stmt: if-else语句', 'regex': re.compile(r'if\s*\(.*?\).*?else', re.DOTALL), 'covered': False},
    {'description': 'Stmt: break语句', 'regex': re.compile(r'\bbreak\s*;'), 'covered': False},
    {'description': 'Stmt: continue语句', 'regex': re.compile(r'\bcontinue\s*;'), 'covered': False},
    {'description': 'Stmt: return语句(无Exp)', 'regex': re.compile(r'\breturn\s*;'), 'covered': False},
    {'description': 'Stmt: return语句(有Exp)', 'regex': re.compile(r'\breturn\s+[^;]+;'), 'covered': False},
    {'description': 'Stmt: printf(无Exp)', 'regex': re.compile(r'printf\s*\(\s*"[^"]*"\s*\);'), 'covered': False},
    {'description': 'Stmt: printf(有Exp)', 'regex': re.compile(r'printf\s*\(.*?,.*?\);'), 'covered': False},

    # Stmt: for 语句 (8种情况)
    {'description': 'Stmt: for语句(无缺省)', 'regex': re.compile(r'for\s*\([^;]+;[^;]+;[^;)]+\)'), 'covered': False},
    {'description': 'Stmt: for语句(缺省条件)', 'regex': re.compile(r'for\s*\([^;]+;\s*;[^;)]+\)'), 'covered': False},
    {'description': 'Stmt: for语句(缺省初始化)', 'regex': re.compile(r'for\s*\(\s*;[^;]+;[^;)]+\)'), 'covered': False},
    {'description': 'Stmt: for语句(缺省迭代)', 'regex': re.compile(r'for\s*\([^;]+;[^;]+;\s*\)'), 'covered': False},
    {'description': 'Stmt: for语句(缺省初始化和条件)', 'regex': re.compile(r'for\s*\(\s*;\s*;[^;)]+\)'), 'covered': False},
    {'description': 'Stmt: for语句(缺省初始化和迭代)', 'regex': re.compile(r'for\s*\(\s*;[^;]+;\s*\)'), 'covered': False},
    {'description': 'Stmt: for语句(缺省条件和迭代)', 'regex': re.compile(r'for\s*\([^;]+;\s*;\s*\)'), 'covered': False},
    {'description': 'Stmt: for语句(全部缺省)', 'regex': re.compile(r'for\s*\(\s*;\s*;\s*\)'), 'covered': False},

    # ForStmt: For语句内的赋值
    {'description': 'ForStmt: 单个赋值', 'regex': re.compile(r'for\s*\(\s*\w+\s*=\s*[^,;]+;'), 'covered': False},
    {'description': 'ForStmt: 多个赋值', 'regex': re.compile(r'for\s*\([^;]*,[^;]+;'), 'covered': False},
    
    # LVal: 左值表达式
    {'description': 'LVal: 普通变量', 'regex': re.compile(r'[=(,\s]\s*\w+\s*(?!\s*\[|\s*\()'), 'covered': False},
    {'description': 'LVal: 数组元素', 'regex': re.compile(r'\w+\s*\[[^\]]+\]'), 'covered': False},
    
    # PrimaryExp: 基本表达式
    {'description': 'PrimaryExp: (Exp)', 'regex': re.compile(r'\([^()]+\)'), 'covered': False},
    {'description': 'PrimaryExp: Number', 'regex': re.compile(r'[=\s\+\-\*\/%<>&|]\s*\d+\b'), 'covered': False},
    
    # UnaryExp: 一元表达式
    {'description': 'UnaryExp: 函数调用(无参)', 'regex': re.compile(r'\b\w+\s*\(\s*\)'), 'covered': False},
    {'description': 'UnaryExp: 函数调用(有参)', 'regex': re.compile(r'\b\w+\s*\([^)\s][^)]*\)'), 'covered': False},
    {'description': 'UnaryExp: 单目运算', 'regex': re.compile(r'[\+\-\!]\s*[\w(]'), 'covered': False},
    
    # UnaryOp: 单目运算符
    {'description': 'UnaryOp: +', 'regex': re.compile(r'=\s*\+\s*[\w(]'), 'covered': False},
    {'description': 'UnaryOp: -', 'regex': re.compile(r'=\s*-\s*[\w(]'), 'covered': False},
    {'description': 'UnaryOp: !', 'regex': re.compile(r'!\s*[\w(]'), 'covered': False},

    # 二元运算符 (使用更严格的匹配模式)
    {'description': 'MulExp: *', 'regex': re.compile(r'[\w)\]]\s*\*\s*[\w(]'), 'covered': False},
    {'description': 'MulExp: /', 'regex': re.compile(r'[\w)\]]\s*\/\s*[\w(]'), 'covered': False},
    {'description': 'MulExp: %', 'regex': re.compile(r'[\w)\]]\s*%\s*[\w(]'), 'covered': False},
    {'description': 'AddExp: +', 'regex': re.compile(r'[\w)\]]\s*\+\s*[\w(]'), 'covered': False},
    {'description': 'AddExp: -', 'regex': re.compile(r'[\w)\]]\s*-\s*[\w(]'), 'covered': False},
    {'description': 'RelExp: <', 'regex': re.compile(r'[\w)\]]\s*<\s*[\w(]'), 'covered': False},
    {'description': 'RelExp: >', 'regex': re.compile(r'[\w)\]]\s*>\s*[\w(]'), 'covered': False},
    {'description': 'RelExp: <=', 'regex': re.compile(r'[\w)\]]\s*<=\s*[\w(]'), 'covered': False},
    {'description': 'RelExp: >=', 'regex': re.compile(r'[\w)\]]\s*>=\s*[\w(]'), 'covered': False},
    {'description': 'EqExp: ==', 'regex': re.compile(r'[\w)\]]\s*==\s*[\w(]'), 'covered': False},
    {'description': 'EqExp: !=', 'regex': re.compile(r'[\w)\]]\s*!=\s*[\w(\-+]'), 'covered': False},
    {'description': 'LAndExp: &&', 'regex': re.compile(r'[\w)\]]\s*&&\s*[\w(]'), 'covered': False},
    {'description': 'LOrExp: ||', 'regex': re.compile(r'[\w)\]]\s*\|\|\s*[\w(]'), 'covered': False},
    
    # ConstExp: 常量表达式
    {'description': 'ConstExp: 存在常量表达式', 'regex': re.compile(r'const\s+int\s+\w+\s*\[\s*[^\]]+\s*\]|const\s+int\s+\w+\s*=\s*[^;{]+;'), 'covered': False}
]

def remove_comments(code):
    code = re.sub(r'//.*', '', code)
    code = re.sub(r'/\*.*?\*/', '', code, flags=re.DOTALL)
    return code

def analyze_sysy_file(file_path):
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            code = f.read()
    except FileNotFoundError:
        print(f"错误: 文件未找到 {file_path}")
        return

    clean_code = remove_comments(code)
    
    is_qualified = True
    reasons = []
    if not re.search(r'int\s+main\s*\(\s*\)\s*\{', clean_code):
        is_qualified = False
        reasons.append("没有找到'int main()'函数定义")
    
    printf_count = len(re.findall(r'\bprintf\b', clean_code))
    if printf_count != 10:
        is_qualified = False
        reasons.append(f"printf语句数量为{printf_count}，不等于10")

    print("--- 分析报告 ---")
    if is_qualified:
        print("程序是否合格: 是")
    else:
        print(f"程序是否合格: 否，原因: {'; '.join(reasons)}")

    level = 'C'
    if re.search(r'(&&|\|\|)', clean_code):
        level = 'A'
    elif re.search(r'\[\s*\]|\[\s*\w+\s*\]', clean_code):
        level = 'B'
    print(f"难度等级判定: {level}级")

    # 重置所有规则的覆盖状态
    for rule in COVERAGE_RULES:
        rule['covered'] = False

    covered_count = 0
    for rule in COVERAGE_RULES:
        if rule['regex'].search(clean_code):
            rule['covered'] = True
            covered_count += 1
    
    total_rules = len(COVERAGE_RULES)
    coverage_percentage = (covered_count / total_rules) * 100
    print(f"文法覆盖率: {covered_count} / {total_rules} = {coverage_percentage:.2f}%")

    if covered_count < total_rules:
        print("\n--- 未覆盖的规则 ---")
        for rule in COVERAGE_RULES:
            if not rule['covered']:
                print(f"- {rule['description']}")
    print("-----------------\n")

if __name__ == '__main__':
    if len(sys.argv) != 2:
        print("用法: python judge.py <文件夹路径>")
        sys.exit(1)
    folder_path = sys.argv[1]
    test_file_name = os.path.basename(folder_path) + ".txt"
    file_path_to_analyze = os.path.join(folder_path, test_file_name)
    print(f"正在分析文件: {file_path_to_analyze}")
    analyze_sysy_file(file_path_to_analyze)