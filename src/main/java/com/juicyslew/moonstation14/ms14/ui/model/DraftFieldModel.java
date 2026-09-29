package com.juicyslew.moonstation14.ms14.ui.model;

import java.math.BigDecimal;
import java.util.Objects;

/** One outstanding edit. Accepted values are authoritative snapshots, not guesses from submitted text. */
public final class DraftFieldModel {
    public enum State { CLEAN, EDITING, INVALID, PENDING, REJECTED }
    public record Intent(long requestId, long baseRevision, String value) {}

    private final int maxLength;
    private final BigDecimal minimum;
    private final BigDecimal maximum;
    private String authority = "";
    private String draft = "";
    private State state = State.CLEAN;
    private long revision = -1;
    private long nextRequestId;
    private Intent pending;

    private DraftFieldModel(int maxLength, BigDecimal minimum, BigDecimal maximum) {
        if (maxLength < 1 || maxLength > 1024 || (minimum != null && (maximum == null || minimum.compareTo(maximum) > 0)))
            throw new IllegalArgumentException("invalid field limits");
        this.maxLength = maxLength;
        this.minimum = minimum;
        this.maximum = maximum;
    }

    public static DraftFieldModel text(int maxLength) { return new DraftFieldModel(maxLength, null, null); }
    public static DraftFieldModel number(int maxLength, BigDecimal minimum, BigDecimal maximum) {
        return new DraftFieldModel(maxLength, Objects.requireNonNull(minimum), Objects.requireNonNull(maximum));
    }

    public void edit(String value) {
        Objects.requireNonNull(value);
        if (pending != null) return; // no ambiguous multiple in-flight edits
        draft = value.length() > maxLength ? value.substring(0, maxLength) : value;
        state = valid(draft) ? State.EDITING : State.INVALID;
    }

    public boolean valid() { return valid(draft); }

    private boolean valid(String value) {
        if (value.length() > maxLength) return false;
        if (minimum == null) return true;
        try {
            BigDecimal number = new BigDecimal(value);
            return number.compareTo(minimum) >= 0 && number.compareTo(maximum) <= 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    /** Returns null for invalid, unchanged, or already pending input. */
    public Intent submit() {
        if (pending != null || !valid(draft) || draft.equals(authority) || revision < 0) return null;
        pending = new Intent(++nextRequestId, revision, draft);
        state = State.PENDING;
        return pending;
    }

    /** A newer snapshot does not imply acceptance. Only an explicit matching acknowledgement completes an edit. */
    public void snapshot(long revision, String value) {
        Objects.requireNonNull(value);
        if (revision <= this.revision) return;
        if (!valid(value)) throw new IllegalArgumentException("invalid authoritative value");
        this.revision = revision;
        authority = value;
        if (pending == null && (state == State.CLEAN || state == State.REJECTED)) {
            draft = value;
            state = State.CLEAN;
        }
    }

    /** Acknowledgements must refer to a revision newer than the submitted base revision. */
    public boolean resolve(long requestId, long revision, String value, boolean accepted) {
        if (pending == null || pending.requestId() != requestId || revision <= pending.baseRevision()
                || revision < this.revision || (revision == this.revision && !authority.equals(value))) return false;
        snapshot(revision, value);
        // Equal-revision acknowledgements are valid if the snapshot arrived first.
        pending = null;
        if (accepted) {
            draft = authority;
            state = State.CLEAN;
        } else {
            draft = authority;
            state = State.REJECTED;
        }
        return true;
    }

    public void discard() {
        if (pending != null) return;
        draft = authority;
        state = State.CLEAN;
    }

    public String draft() { return draft; }
    public String authority() { return authority; }
    public State state() { return state; }
    public Intent pending() { return pending; }
    public long revision() { return revision; }
}
