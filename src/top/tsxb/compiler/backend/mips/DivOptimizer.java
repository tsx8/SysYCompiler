package top.tsxb.compiler.backend.mips;

public final class DivOptimizer {
    private DivOptimizer() {}

    public static MultiplierInfo chooseMultiplier(int d) {
        if (d == 0) {
            throw new IllegalArgumentException("Division by zero");
        }
        long ad = Math.abs((long)d);
        long t = 1L << 31;
        long anc = t - 1 - (t % ad);
        long q1 = t / anc;
        long r1 = t - q1 * anc;
        long q2 = t / ad;
        long r2 = t - q2 * ad;
        long p = 31;
        long delta;
        do {
            p++;
            q1 <<= 1;
            r1 <<= 1;
            if (r1 >= anc) {
                q1++;
                r1 -= anc;
            }
            q2 <<= 1;
            r2 <<= 1;
            if (r2 >= ad) {
                q2++;
                r2 -= ad;
            }
            delta = ad - r2;
        } while (q1 < delta || (q1 == delta && r1 == 0));

        long multiplier = q2 + 1;
        int shift = (int)(p - 32);
        return new MultiplierInfo(multiplier, shift);
    }

    public record MultiplierInfo(long multiplier, int shift) {
    }
}
