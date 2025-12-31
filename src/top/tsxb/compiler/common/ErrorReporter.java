package top.tsxb.compiler.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The type Error reporter.
 */
public class ErrorReporter implements Backtrackable<Integer> {
    private final List<ErrorEntry> errors;

    /**
     * Instantiates a new Error reporter.
     */
    public ErrorReporter() {
        errors = new ArrayList<>();
    }

    /**
     * Report.
     *
     * @param lineNumber the line number
     * @param type the type
     * @param args the args
     */
    public void report(int lineNumber, ErrorType type, Object... args) {
        String message = String.format(type.getDescription(), args);
        errors.add(new ErrorEntry(lineNumber, type.getCode(), message));
    }

    /**
     * Has errors boolean.
     *
     * @return the boolean
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * Gets all reported errors, sorted by line number.
     *
     * @return A sorted list of errors.
     */
    public List<ErrorEntry> getErrors() {
        List<ErrorEntry> sortedErrors = new ArrayList<>(this.errors);
        Collections.sort(sortedErrors);
        return Collections.unmodifiableList(sortedErrors);
    }

    /**
     * Save int.
     *
     * @return the int
     */
    public Integer save() {
        return errors.size();
    }

    /**
     * Restore.
     *
     * @param size the size
     */
    public void restore(Integer size) {
        if (size < 0 || size > errors.size()) {
            return;
        }
        while (errors.size() > size) {
            errors.remove(errors.size() - 1);
        }
    }
}
