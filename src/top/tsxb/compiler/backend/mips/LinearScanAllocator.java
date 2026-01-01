package top.tsxb.compiler.backend.mips;

import top.tsxb.compiler.ir.base.Value;
import java.util.*;

public class LinearScanAllocator {
    private final List<LiveInterval> intervals;
    private final List<MipsRegister> freeRegs = new LinkedList<>();
    private final List<LiveInterval> active = new ArrayList<>();
    private final Map<Value, MipsRegister> regMapping = new HashMap<>();
    private final Set<MipsRegister> usedCalleeSaved = new HashSet<>();

    public LinearScanAllocator(List<LiveInterval> intervals) {
        this.intervals = intervals;
        for (MipsRegister reg : MipsRegister.getAllocatable()) {
            if (reg.isCallerSaved()) {
                freeRegs.add(reg);
            }
        }
        for (MipsRegister reg : MipsRegister.getAllocatable()) {
            if (reg.isCalleeSaved()) {
                freeRegs.add(reg);
            }
        }
    }

    public void allocate() {
        for (LiveInterval i : intervals) {
            expireOldIntervals(i);
            if (freeRegs.isEmpty()) {
                spillAtInterval(i);
            } else {
                MipsRegister reg = getMipsRegister(i);
                freeRegs.remove(reg);

                i.setReg(reg);
                regMapping.put(i.getValue(), reg);
                if (reg.isCalleeSaved()) {
                    usedCalleeSaved.add(reg);
                }
                active.add(i);
                active.sort(Comparator.comparingInt(LiveInterval::getEnd));
            }
        }
    }

    private MipsRegister getMipsRegister(LiveInterval i) {
        MipsRegister reg = null;
        if (i.isSpansCall()) {
            // Strongly prioritize callee-saved ($s) for intervals spanning calls
            for (MipsRegister r : freeRegs) {
                if (r.isCalleeSaved()) {
                    reg = r;
                    break;
                }
            }
        } else {
            // Prioritize caller-saved ($t) for intervals not spanning calls
            for (MipsRegister r : freeRegs) {
                if (r.isCallerSaved()) {
                    reg = r;
                    break;
                }
            }
        }

        // Fallback to the first available register if preferred type not found
        if (reg == null) {
            reg = freeRegs.get(0);
        }
        return reg;
    }

    private void expireOldIntervals(LiveInterval i) {
        Iterator<LiveInterval> it = active.iterator();
        while (it.hasNext()) {
            LiveInterval j = it.next();
            if (j.getEnd() >= i.getStart()) break;
            it.remove();
            freeRegs.add(0, j.getReg()); // Add back to free list
        }
    }

    private void spillAtInterval(LiveInterval i) {
        // Find the best candidate to spill in active list
        // We want to spill an interval that ends later than i, 
        // and preferably one that has the register type i wants.
        LiveInterval spill = null;
        List<LiveInterval> candidates = new ArrayList<>();
        for (LiveInterval a : active) {
            if (a.getEnd() > i.getEnd()) {
                candidates.add(a);
            }
        }

        if (!candidates.isEmpty()) {
            if (i.isSpansCall()) {
                // i wants $s, so try to spill an interval that has $s
                for (int j = candidates.size() - 1; j >= 0; j--) {
                    if (candidates.get(j).getReg().isCalleeSaved()) {
                        spill = candidates.get(j);
                        break;
                    }
                }
            } else {
                // i wants $t, so try to spill an interval that has $t
                for (int j = candidates.size() - 1; j >= 0; j--) {
                    if (candidates.get(j).getReg().isCallerSaved()) {
                        spill = candidates.get(j);
                        break;
                    }
                }
            }
            // If no preferred register type found among candidates, pick the one that ends latest
            if (spill == null) {
                spill = candidates.get(candidates.size() - 1);
            }
        }

        if (spill != null) {
            i.setReg(spill.getReg());
            regMapping.put(i.getValue(), i.getReg());
            regMapping.remove(spill.getValue());
            spill.setSpilled(true);
            spill.setReg(null);
            active.remove(spill);
            active.add(i);
            active.sort(Comparator.comparingInt(LiveInterval::getEnd));
        } else {
            i.setSpilled(true);
        }
    }

    public Map<Value, MipsRegister> getRegMapping() {
        return regMapping;
    }

    public Set<MipsRegister> getUsedCalleeSaved() {
        return usedCalleeSaved;
    }
}
