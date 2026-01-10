package top.tsxb.compiler.backend.mips;

import java.util.ArrayList;
import java.util.List;

import top.tsxb.compiler.ir.base.Value;

public class LiveInterval implements Comparable<LiveInterval> {
    private final Value value;
    private final List<LiveInterval> hints = new ArrayList<>();
    private final List<LiveInterval> phiHints = new ArrayList<>();
    private final List<LiveInterval> avoidSameRegWith = new ArrayList<>();
    private int start;
    private int end;
    private MipsRegister reg;
    private boolean spansCall;
    private double weight;

    public LiveInterval(Value value) {
        this.value = value;
        this.start = Integer.MAX_VALUE;
        this.end = Integer.MIN_VALUE;
        this.spansCall = false;
        this.weight = 0.0;
    }

    public void addPhiHint(LiveInterval interval) {
        if (interval != null && !phiHints.contains(interval)) {
            phiHints.add(interval);
        }
    }

    public List<LiveInterval> getHints() {
        return hints;
    }

    public List<LiveInterval> getPhiHints() {
        return phiHints;
    }

    public void addAvoidSameRegWith(LiveInterval interval) {
        if (interval != null && interval != this && !avoidSameRegWith.contains(interval)) {
            avoidSameRegWith.add(interval);
        }
    }

    public List<LiveInterval> getAvoidSameRegWith() {
        return avoidSameRegWith;
    }

    public Value getValue() {
        return value;
    }

    public double getWeight() {
        return weight;
    }

    public void addWeight(double weight) {
        this.weight += weight;
    }

    public int getStart() {
        return start;
    }

    public void setStart(int start) {
        this.start = start;
    }

    public int getEnd() {
        return end;
    }

    public void setEnd(int end) {
        this.end = end;
    }

    public void addRange(int start, int end) {
        this.start = Math.min(this.start, start);
        this.end = Math.max(this.end, end);
    }

    public MipsRegister getReg() {
        return reg;
    }

    public void setReg(MipsRegister reg) {
        this.reg = reg;
    }

    public boolean isSpansCall() {
        return spansCall;
    }

    public void setSpansCall(boolean spansCall) {
        this.spansCall = spansCall;
    }

    @Override
    public int compareTo(LiveInterval o) {
        if (this.start != o.start) {
            return Integer.compare(this.start, o.start);
        }
        if (this.end != o.end) {
            return Integer.compare(this.end, o.end);
        }
        return this.value.getName().compareTo(o.value.getName());
    }

    @Override
    public String toString() {
        return String.format("%s: [%d, %d] -> %s", value.getName(), start, end, reg != null ? reg : "stack");
    }
}
