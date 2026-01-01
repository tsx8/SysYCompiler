package top.tsxb.compiler.backend.mips;

public enum MipsRegister {
    ZERO("$zero", 0),
    AT("$at", 1),
    V0("$v0", 2), V1("$v1", 3),
    A0("$a0", 4), A1("$a1", 5), A2("$a2", 6), A3("$a3", 7),
    T0("$t0", 8), T1("$t1", 9), T2("$t2", 10), T3("$t3", 11),
    T4("$t4", 12), T5("$t5", 13), T6("$t6", 14), T7("$t7", 15),
    S0("$s0", 16), S1("$s1", 17), S2("$s2", 18), S3("$s3", 19),
    S4("$s4", 20), S5("$s5", 21), S6("$s6", 22), S7("$s7", 23),
    T8("$t8", 24), T9("$t9", 25),
    K0("$k0", 26), K1("$k1", 27),
    GP("$gp", 28),
    SP("$sp", 29),
    FP("$fp", 30),
    RA("$ra", 31);

    private final String name;
    private final int id;

    MipsRegister(String name, int id) {
        this.name = name;
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public boolean isCallerSaved() {
        return (id >= 8 && id <= 15) || (id >= 24 && id <= 25) || (id >= 2 && id <= 7);
    }

    public boolean isCalleeSaved() {
        return id >= 16 && id <= 23 || id == 30;
    }

    public static MipsRegister[] getAllocatable() {
        return new MipsRegister[]{
            T4, T5, T6, T7, T8, T9,
            S0, S1, S2, S3, S4, S5, S6, S7
        };
    }

    @Override
    public String toString() {
        return name;
    }
}
