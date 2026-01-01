package top.tsxb.compiler.backend.mips;

import top.tsxb.compiler.ir.base.Value;
import java.util.*;

public class LinearScanAllocator {
    private final List<LiveInterval> intervals;
    private final List<MipsRegister> freeRegs = new LinkedList<>();
    private final List<LiveInterval> active = new ArrayList<>();
    private final Map<Value, MipsRegister> regMapping = new LinkedHashMap<>();
    private final Set<MipsRegister> usedCalleeSaved = new LinkedHashSet<>();

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
                active.sort((a, b) -> {
                    if (a.getEnd() != b.getEnd()) {
                        return Integer.compare(a.getEnd(), b.getEnd());
                    }
                    return a.getValue().getName().compareTo(b.getValue().getName());
                });
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
        // We want to spill an interval with the minimum weight.
        // If weights are equal, we prefer to spill the one that ends later.
        LiveInterval spill = null;
        List<LiveInterval> candidates = new ArrayList<>();
        for (LiveInterval a : active) {
            if (a.getEnd() > i.getEnd()) {
                candidates.add(a);
            }
        }

        if (!candidates.isEmpty()) {
            // Find candidate with minimum weight
            spill = candidates.get(0);
            for (LiveInterval c : candidates) {
                if (c.getWeight() < spill.getWeight()) {
                    spill = c;
                } else if (c.getWeight() == spill.getWeight()) {
                    // Tie-break: prefer spilling the one that matches register type preference
                    boolean spillMatches = i.isSpansCall() ? spill.getReg().isCalleeSaved() : spill.getReg().isCallerSaved();
                    boolean cMatches = i.isSpansCall() ? c.getReg().isCalleeSaved() : c.getReg().isCallerSaved();
                    if (cMatches && !spillMatches) {
                        spill = c;
                    } else if (cMatches == spillMatches) {
                        if (c.getEnd() > spill.getEnd()) {
                            spill = c;
                        } else if (c.getEnd() == spill.getEnd()) {
                            if (c.getValue().getName().compareTo(spill.getValue().getName()) > 0) {
                                spill = c;
                            }
                        }
                    }
                }
            }

            // Compare with i
            if (i.getWeight() < spill.getWeight()) {
                spill = null; // Spill i instead
            } else if (i.getWeight() == spill.getWeight()) {
                if (i.getEnd() > spill.getEnd()) {
                    spill = null; // Spill i instead
                } else if (i.getEnd() == spill.getEnd()) {
                    if (i.getValue().getName().compareTo(spill.getValue().getName()) > 0) {
                        spill = null;
                    }
                }
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
            active.sort((a, b) -> {
                if (a.getEnd() != b.getEnd()) {
                    return Integer.compare(a.getEnd(), b.getEnd());
                }
                return a.getValue().getName().compareTo(b.getValue().getName());
            });
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
