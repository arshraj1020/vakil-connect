package com.arshraj.vakilconnect.reference.enums;

/**
 * Distinguishes a state from a union territory.
 *
 * They behave identically as a geographic parent - the distinction exists so
 * the UI can group them, and because conflating the two is factually wrong on
 * an India-first platform.
 */
public enum StateType {
    STATE,
    UNION_TERRITORY
}
