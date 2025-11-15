package top.tsxb.compiler.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The type Backtrack mgr.
 */
public class BacktrackMgr {
    private final List<Backtrackable<?>> backtrackables = new ArrayList<>();

    /**
     * Register.
     *
     * @param backtrackable the backtrackable
     */
    public void register(Backtrackable<?> backtrackable) {
        backtrackables.add(Objects.requireNonNull(backtrackable));
    }

    /**
     * Save snapshot.
     *
     * @return the snapshot
     */
    public Snapshot save() {
        List<Object> mementos = new ArrayList<>(backtrackables.size());
        for (Backtrackable<?> backtrackable : backtrackables) {
            mementos.add(backtrackable.save());
        }
        return new Snapshot(mementos);
    }

    /**
     * Restore.
     *
     * @param snapshot the snapshot
     */
    @SuppressWarnings("unchecked")
    public void restore(Snapshot snapshot) {
        if (snapshot == null || snapshot.mementos().size() != backtrackables.size()) {
            throw new IllegalArgumentException("Snapshot is invalid or does not match manager state.");
        }
        for (int i = 0; i < snapshot.mementos.size(); i++) {
            @SuppressWarnings("rawtypes")
            Backtrackable backtrackable = backtrackables.get(i);
            backtrackable.restore(snapshot.mementos().get(i));
        }
    }

    /**
     * The type Snapshot.
     */
    public record Snapshot(List<Object> mementos) {
    }
}
