package com.minibank.card.domain;

/** Whether a card exists as a piece of plastic or only online. */
public enum CardType {

    /** Issued physically. In this demo no plastic is produced; the distinction is modelled only. */
    PHYSICAL,

    /** Online only. Cheap to issue and cheap to throw away, which is the point of them. */
    VIRTUAL
}
