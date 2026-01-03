package top.tsxb.compiler.backend.mips;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;

import top.tsxb.compiler.ir.base.Value;

public class GraphColoringRegAlloc {
    private final List<LiveInterval> intervals;
    private final int K;
    private final List<MipsRegister> allocatableRegs;
    private final Map<LiveInterval, Set<LiveInterval>> adjList = new LinkedHashMap<>();
    private final Map<LiveInterval, Integer> degree = new LinkedHashMap<>();
    private final Set<LiveInterval> simplifyWorklist = new LinkedHashSet<>();
    private final Set<LiveInterval> spillWorklist = new LinkedHashSet<>();
    private final Set<LiveInterval> freezeWorklist = new LinkedHashSet<>();
    private final Stack<LiveInterval> selectStack = new Stack<>();
    private final Set<LiveInterval> selectStackSet = new LinkedHashSet<>();
    // Coalescing fields
    private final Set<Move> worklistMoves = new LinkedHashSet<>();
    private final Set<Move> activeMoves = new LinkedHashSet<>();
    private final Map<LiveInterval, Set<Move>> moveList = new LinkedHashMap<>();
    private final Map<LiveInterval, LiveInterval> alias = new LinkedHashMap<>();
    private final Map<Value, MipsRegister> regMapping = new LinkedHashMap<>();
    private final Set<MipsRegister> usedCalleeSaved = new LinkedHashSet<>();
    public GraphColoringRegAlloc(List<LiveInterval> intervals) {
        this.intervals = new ArrayList<>(intervals);
        this.allocatableRegs = new ArrayList<>();

        for (MipsRegister reg : MipsRegister.getAllocatable()) {
            if (reg.isCallerSaved())
                allocatableRegs.add(reg);
        }
        for (MipsRegister reg : MipsRegister.getAllocatable()) {
            if (reg.isCalleeSaved())
                allocatableRegs.add(reg);
        }

        this.K = allocatableRegs.size();
    }

    public void allocate() {
        build();
        makeWorklist();

        while (!simplifyWorklist.isEmpty() || !worklistMoves.isEmpty() || !freezeWorklist.isEmpty()
            || !spillWorklist.isEmpty()) {
            if (!simplifyWorklist.isEmpty()) {
                simplify();
            } else if (!worklistMoves.isEmpty()) {
                coalesce();
            } else if (!freezeWorklist.isEmpty()) {
                freeze();
            } else {
                selectSpill();
            }
        }

        assignColors();
    }

    private void build() {
        for (LiveInterval interval : intervals) {
            adjList.put(interval, new LinkedHashSet<>());
            degree.put(interval, 0);
            moveList.put(interval, new LinkedHashSet<>());
            alias.put(interval, interval);
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
            for (LiveInterval v : u.getPhiHints()) {
                if (intervals.contains(v) && u != v && !adjList.get(u).contains(v)) {
                    Move m = new Move(u, v, true);
                    if (!worklistMoves.contains(m)) {
                        worklistMoves.add(m);
                        moveList.get(u).add(m);
                        moveList.get(v).add(m);
                    }
                }
            }
        }

        for (LiveInterval u : intervals) {
            for (LiveInterval v : u.getHints()) {
                if (intervals.contains(v) && !adjList.get(u).contains(v)) {
                    Move m = new Move(u, v, false);
                    if (!worklistMoves.contains(m)) {
                        worklistMoves.add(m);
                        moveList.get(u).add(m);
                        moveList.get(v).add(m);
                    }
                }
            }
        }
    }

    private boolean interferes(LiveInterval a, LiveInterval b) {
        return a.getStart() < b.getEnd() && b.getStart() < a.getEnd();
    }

    private void addEdge(LiveInterval u, LiveInterval v) {
        if (u == v)
            return;
        Set<LiveInterval> uAdj = adjList.get(u);
        Set<LiveInterval> vAdj = adjList.get(v);
        if (uAdj == null || vAdj == null)
            return;

        if (!uAdj.contains(v)) {
            uAdj.add(v);
            vAdj.add(u);

            Integer uDeg = degree.get(u);
            Integer vDeg = degree.get(v);
            if (uDeg != null)
                degree.put(u, uDeg + 1);
            if (vDeg != null)
                degree.put(v, vDeg + 1);
        }
    }

    private void makeWorklist() {
        for (LiveInterval i : intervals) {
            if (degree.get(i) >= K) {
                spillWorklist.add(i);
            } else if (isMoveRelated(i)) {
                freezeWorklist.add(i);
            } else {
                simplifyWorklist.add(i);
            }
        }
    }

    private LiveInterval getAlias(LiveInterval n) {
        if (alias.get(n) == n)
            return n;
        LiveInterval a = getAlias(alias.get(n));
        alias.put(n, a);
        return a;
    }

    private boolean isMoveRelated(LiveInterval n) {
        return !nodeMoves(n).isEmpty();
    }

    private Set<Move> nodeMoves(LiveInterval n) {
        Set<Move> res = new LinkedHashSet<>();
        for (Move m : moveList.get(n)) {
            if (activeMoves.contains(m) || worklistMoves.contains(m)) {
                res.add(m);
            }
        }
        return res;
    }

    private Set<LiveInterval> adjacent(LiveInterval n) {
        Set<LiveInterval> res = new LinkedHashSet<>();
        Set<LiveInterval> adj = adjList.get(n);
        if (adj == null)
            return res;
        for (LiveInterval m : adj) {
            if (!selectStackSet.contains(m) && getAlias(m) == m) {
                res.add(m);
            }
        }
        return res;
    }

    private void simplify() {
        LiveInterval n = simplifyWorklist.iterator().next();
        simplifyWorklist.remove(n);

        selectStack.push(n);
        selectStackSet.add(n);
        for (LiveInterval m : adjacent(n)) {
            decrementDegree(m);
        }
    }

    private void decrementDegree(LiveInterval m) {
        Integer d = degree.get(m);
        if (d == null)
            return;

        degree.put(m, d - 1);

        if (d == K) {
            Set<LiveInterval> nodes = new LinkedHashSet<>(adjacent(m));
            nodes.add(m);
            for (LiveInterval node : nodes) {
                enableMoves(node);
            }
            spillWorklist.remove(m);
            if (isMoveRelated(m)) {
                freezeWorklist.add(m);
            } else {
                simplifyWorklist.add(m);
            }
        }
    }

    private void enableMoves(LiveInterval n) {
        for (Move m : nodeMoves(n)) {
            if (activeMoves.contains(m)) {
                activeMoves.remove(m);
                worklistMoves.add(m);
            }
        }
    }

    private void addWorkList(LiveInterval u) {
        Integer uDegree = degree.get(u);
        if (uDegree != null && uDegree < K && !isMoveRelated(u)) {
            freezeWorklist.remove(u);
            simplifyWorklist.add(u);
        }
    }

    private void coalesce() {
        Move m = worklistMoves.iterator().next();
        LiveInterval x = getAlias(m.u);
        LiveInterval y = getAlias(m.v);

        LiveInterval u, v;
        if (y.getReg() != null) {
            u = y;
            v = x;
        } else {
            u = x;
            v = y;
        }

        worklistMoves.remove(m);

        if (u == v) {
            addWorkList(u);
        } else if (v.getReg() != null || adjList.get(u).contains(v)) {
            addWorkList(u);
            addWorkList(v);
        } else if (conservative(u, v)) {
            combine(u, v);
            addWorkList(u);
        } else {
            activeMoves.add(m);
        }
    }

    private boolean conservative(LiveInterval u, LiveInterval v) {
        int k = 0;
        Set<LiveInterval> union = new LinkedHashSet<>(adjacent(u));
        union.addAll(adjacent(v));
        for (LiveInterval n : union) {
            Integer nDeg = degree.get(n);
            if (nDeg != null && nDeg >= K) {
                k++;
            }
        }
        return k < K;
    }

    private void combine(LiveInterval u, LiveInterval v) {
        if (freezeWorklist.contains(v)) {
            freezeWorklist.remove(v);
        } else {
            spillWorklist.remove(v);
        }
        simplifyWorklist.remove(v);

        alias.put(v, u);
        moveList.get(u).addAll(moveList.get(v));
        enableMoves(v);

        for (LiveInterval t : new LinkedHashSet<>(adjacent(v))) {
            addEdge(t, u);
            decrementDegree(t);
        }

        if (degree.get(u) >= K && freezeWorklist.contains(u)) {
            freezeWorklist.remove(u);
            spillWorklist.add(u);
        }
    }

    private void freeze() {
        LiveInterval u = freezeWorklist.iterator().next();
        freezeWorklist.remove(u);
        simplifyWorklist.add(u);
        freezeMoves(u);
    }

    private void freezeMoves(LiveInterval u) {
        for (Move m : new LinkedHashSet<>(nodeMoves(u))) {
            LiveInterval x = m.u;
            LiveInterval y = m.v;
            LiveInterval v;
            if (getAlias(y) == getAlias(u)) {
                v = getAlias(x);
            } else {
                v = getAlias(y);
            }
            activeMoves.remove(m);
            Integer vDegree = degree.get(v);
            if (vDegree != null && nodeMoves(v).isEmpty() && vDegree < K) {
                freezeWorklist.remove(v);
                simplifyWorklist.add(v);
            }
        }
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

        if (m == null)
            m = spillWorklist.iterator().next();

        spillWorklist.remove(m);
        simplifyWorklist.add(m);
        freezeMoves(m);
    }

    private void assignColors() {
        while (!selectStack.isEmpty()) {
            LiveInterval n = selectStack.pop();
            selectStackSet.remove(n);
            Set<MipsRegister> okColors = new LinkedHashSet<>(allocatableRegs);

            for (LiveInterval w : adjList.get(n)) {
                LiveInterval aliasW = getAlias(w);
                MipsRegister wReg = aliasW.getReg();
                if (wReg != null) {
                    okColors.remove(wReg);
                }
                MipsRegister mappedReg = regMapping.get(aliasW.getValue());
                if (mappedReg != null) {
                    okColors.remove(mappedReg);
                }
            }

            if (okColors.isEmpty()) {
                continue;
            }

            MipsRegister color = null;

            for (LiveInterval hint : n.getPhiHints()) {
                MipsRegister hintReg = getAlias(hint).getReg();
                if (hintReg != null && okColors.contains(hintReg)) {
                    color = hintReg;
                    break;
                }
            }

            if (color == null) {
                for (LiveInterval hint : n.getHints()) {
                    MipsRegister hintReg = getAlias(hint).getReg();
                    if (hintReg != null && okColors.contains(hintReg)) {
                        color = hintReg;
                        break;
                    }
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

            if (color == null) {
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

        for (LiveInterval n : intervals) {
            LiveInterval a = getAlias(n);
            if (a != n && a.getReg() != null) {
                n.setReg(a.getReg());
                regMapping.put(n.getValue(), a.getReg());
            }
        }
    }

    public Map<Value, MipsRegister> getRegMapping() {
        return regMapping;
    }

    public Set<MipsRegister> getUsedCalleeSaved() {
        return usedCalleeSaved;
    }

    private record Move(LiveInterval u, LiveInterval v, boolean isPhi) {

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Move move = (Move)o;
            return (Objects.equals(u, move.u) && Objects.equals(v, move.v))
                || (Objects.equals(u, move.v) && Objects.equals(v, move.u));
        }

        @Override
        public int hashCode() {
            return u.hashCode() ^ v.hashCode();
        }
    }
}
