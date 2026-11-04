package com.minibank.card.domain;

/**
 * The card skin.
 *
 * <p>Deliberately an identifier and nothing else: no colour, gradient or texture is described here.
 * The visual definition lives in the web client's stylesheet, because a design change should be a
 * CSS change rather than a database migration. The backend's only job is to remember which one the
 * customer chose and to reject a value the client made up.
 */
public enum CardDesign {
    AURORA,
    MIDNIGHT,
    OCEAN,
    SUNSET,
    CRYSTAL,
    CARBON,
    HOLOGRAPHIC
}
