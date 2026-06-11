package com.fueledbychai.datarecording;

/**
 * The mutation an incremental book delta applies to a price level. {@code DELETE} removes
 * the level (size goes to zero); {@code ADD}/{@code CHANGE} set it to {@code newSize}.
 * Venues that report only "set level to size S" map to {@code CHANGE} (or {@code DELETE}
 * when S == 0); the add/cancel distinction is recovered downstream by diffing against the
 * maintained book.
 */
public enum EventAction {
    ADD, CHANGE, DELETE
}
