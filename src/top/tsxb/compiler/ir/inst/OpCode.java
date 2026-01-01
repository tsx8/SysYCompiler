package top.tsxb.compiler.ir.inst;

public enum OpCode {
    // Terminator
    RET("ret"), BR("br"),
    // Binary
    ADD("add"), SUB("sub"), MUL("mul"), SDIV("sdiv"), SREM("srem"),
    // Comparison
    ICMP("icmp"),
    // Memory
    ALLOCA("alloca"), LOAD("load"), STORE("store"), GEP("getelementptr"), PHI("phi"),
    // Cast
    ZEXT("zext"),
    // Other
    CALL("call");

    private final String name;

    OpCode(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
