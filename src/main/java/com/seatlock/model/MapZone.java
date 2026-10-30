package com.seatlock.model;

/**
 * One cell of the 3x3 grid a venue's seat map is drawn on, with north at the top. The stage takes
 * one cell and sections fill the others, so an end stage is {@code NORTH}, a stage in the round is
 * {@code CENTER}, and a side or corner stage is any other cell.
 */
public enum MapZone {
    NORTH_WEST, NORTH, NORTH_EAST,
    WEST, CENTER, EAST,
    SOUTH_WEST, SOUTH, SOUTH_EAST;

    /** The zone a section goes to when none is given: across from the stage, facing it. */
    public MapZone facing() {
        return this == CENTER ? SOUTH : values()[values().length - 1 - ordinal()];
    }
}
