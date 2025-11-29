package top.tsxb.compiler.ir;

import top.tsxb.compiler.ir.value.User;
import top.tsxb.compiler.ir.value.Value;

public record Use(User user, Value value) {
}
