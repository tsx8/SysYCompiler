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
                MipsRegister reg = freeRegs.remove(0);
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
        LiveInterval spill = active.get(active.size() - 1);
        if (spill.getEnd() > i.getEnd()) {
            i.setReg(spill.getReg());
            regMapping.put(i.getValue(), i.getReg());
            regMapping.remove(spill.getValue());
            spill.setSpilled(true);
            spill.setReg(null);
            active.remove(active.size() - 1);
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
