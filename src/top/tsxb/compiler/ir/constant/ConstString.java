package top.tsxb.compiler.ir.constant;

import top.tsxb.compiler.ir.type.ArrType;
import top.tsxb.compiler.ir.type.IntType;

public class ConstString extends Constant {
    private final String content;

    public ConstString(String content) {
        super(new ArrType(IntType.I8, content.replace("\\n", "x").length() + 1), "");
        this.content = content;
    }

    @Override
    public String getRef() {
        StringBuilder sb = new StringBuilder();
        sb.append("c\"");
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '\\' && i + 1 < content.length() && 'n' == content.charAt(i + 1)) {
                sb.append("\\0A");
                i++;
            } else {
                sb.append(c);
            }
        }
        sb.append("\\00\"");
        return sb.toString();
    }
}
