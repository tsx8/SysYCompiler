package top.tsxb.compiler.backend.mips;

import top.tsxb.compiler.ir.base.Value;
import java.util.*;

public class GraphColoringRegAlloc {
    private final List<LiveInterval> intervals;
    private final int K;
    private final List<MipsRegister> allocatableRegs;
    
    private final Map<LiveInterval, Set<LiveInterval>> adjList = new HashMap<>();
    private final Map<LiveInterval, Integer> degree = new HashMap<>();

    private final Set<LiveInterval> simplifyWorklist = new HashSet<>();
    private final Set<LiveInterval> spillWorklist = new HashSet<>();
    private final Set<LiveInterval> freezeWorklist = new HashSet<>();
    private final Stack<LiveInterval> selectStack = new Stack<>();
    
    private final Set<LiveInterval> moveRelated = new HashSet<>();
    
    private final Map<Value, MipsRegister> regMapping = new LinkedHashMap<>();
    private final Set<MipsRegister> usedCalleeSaved = new LinkedHashSet<>();

    public GraphColoringRegAlloc(List<LiveInterval> intervals) {
        this.intervals = new ArrayList<>(intervals);
        this.allocatableRegs = new ArrayList<>();
        
        for (MipsRegister reg : MipsRegister.getAllocatable()) {
            if (reg.isCallerSaved()) allocatableRegs.add(reg);
        }
        for (MipsRegister reg : MipsRegister.getAllocatable()) {
            if (reg.isCalleeSaved()) allocatableRegs.add(reg);
        }
        
        this.K = allocatableRegs.size();
    }

    public void allocate() {
        build();
        makeWorklist();
        
        while (!simplifyWorklist.isEmpty() || !freezeWorklist.isEmpty() || !spillWorklist.isEmpty()) {
            if (!simplifyWorklist.isEmpty()) {
                simplify();
            } else if (!freezeWorklist.isEmpty()) {
                freeze();
            } else {
                selectSpill();
            }
        }
        
        assignColors();
    }

    private void build() {
        // Initialize graph
        for (LiveInterval interval : intervals) {
            adjList.put(interval, new HashSet<>());
            degree.put(interval, 0);
        }

        for (int i = 0; i < intervals.size(); i++) {
            for (int j = i + 1; j < intervals.size(); j++) {
                LiveInterval u = intervals.get(i);
                LiveInterval v = intervals.get(j);
                if (interferes(u, v)) {
                    addEdge(u, v);
                }
            }
        }
        
        for (LiveInterval u : intervals) {
            for (LiveInterval v : u.getHints()) {
                if (intervals.contains(v)) {
                    if (!interferes(u, v)) {
                        moveRelated.add(u);
                        moveRelated.add(v);
                    }
                }
            }
        }
    }
    
    private boolean interferes(LiveInterval a, LiveInterval b) {
        return a.getStart() < b.getEnd() && b.getStart() < a.getEnd();
    }

    private void addEdge(LiveInterval u, LiveInterval v) {
        if (!adjList.get(u).contains(v)) {
            adjList.get(u).add(v);
            degree.put(u, degree.get(u) + 1);
            
            adjList.get(v).add(u);
            degree.put(v, degree.get(v) + 1);
        }
    }

    private void makeWorklist() {
        for (LiveInterval i : intervals) {
            if (degree.get(i) >= K) {
                spillWorklist.add(i);
            } else if (moveRelated.contains(i)) {
                freezeWorklist.add(i);
            } else {
                simplifyWorklist.add(i);
            }
        }
    }

    private void simplify() {
        Iterator<LiveInterval> it = simplifyWorklist.iterator();
        LiveInterval n = it.next();
        it.remove();
        
        selectStack.push(n);
        for (LiveInterval m : adjList.get(n)) {
            decrementDegree(m);
        }
    }
    
    private void decrementDegree(LiveInterval m) {
        if (selectStack.contains(m)) return; // Already removed
        
        int d = degree.get(m);
        degree.put(m, d - 1);
        
        if (d == K) {
            spillWorklist.remove(m);
            if (moveRelated.contains(m)) {
                freezeWorklist.add(m);
            } else {
                simplifyWorklist.add(m);
            }
        }
    }
    
    private void freeze() {
        Iterator<LiveInterval> it = freezeWorklist.iterator();
        LiveInterval u = it.next();
        it.remove();
        simplifyWorklist.add(u);
    }

    private void selectSpill() {
        LiveInterval m = null;
        double minCost = Double.MAX_VALUE;
        
        for (LiveInterval node : spillWorklist) {
            double cost = node.getWeight() / degree.get(node);
            if (cost < minCost) {
                minCost = cost;
                m = node;
            }
        }
        
        if (m == null) m = spillWorklist.iterator().next();
        
        spillWorklist.remove(m);
        selectStack.push(m);
        for (LiveInterval neighbor : adjList.get(m)) {
            decrementDegree(neighbor);
        }
    }

    private void assignColors() {
        while (!selectStack.isEmpty()) {
            LiveInterval n = selectStack.pop();
            Set<MipsRegister> okColors = new HashSet<>(allocatableRegs);

            for (LiveInterval w : adjList.get(n)) {
                MipsRegister wReg = w.getReg();
                if (wReg != null) {
                    okColors.remove(wReg);
                }
            }
            
            MipsRegister color = null;
            
            for (LiveInterval hint : n.getHints()) {
                MipsRegister hintReg = hint.getReg();
                if (hintReg != null && okColors.contains(hintReg)) {
                    color = hintReg;
                    break;
                }
            }
            
            if (color == null) {
                if (n.isSpansCall()) {
                    for (MipsRegister r : okColors) {
                        if (r.isCalleeSaved()) {
                            color = r;
                            break;
                        }
                    }
                } else {
                    for (MipsRegister r : okColors) {
                        if (r.isCallerSaved()) {
                            color = r;
                            break;
                        }
                    }
                }
            }
            
            if (color == null && !okColors.isEmpty()) {
                color = okColors.iterator().next();
            }
            
            if (color != null) {
                n.setReg(color);
                regMapping.put(n.getValue(), color);
                if (color.isCalleeSaved()) {
                    usedCalleeSaved.add(color);
                }
            }
        }
    }

    public Map<Value, MipsRegister> getRegMapping() {
        return regMapping;
    }

    public Set<MipsRegister> getUsedCalleeSaved() {
        return usedCalleeSaved;
    }
}
