package net.charter.logic;

/** What Charter decides to do with someone it has judged. */
public enum Verdict {
	/** Permanent ban: the player is disconnected and can never join again. */
	BAN,
	/** The player is thrown millions of blocks away, unable to die. */
	EXILE,
	/** The player is only disconnected (no ban list entry). */
	KICK,
	/** The player is crushed to death (and respawns normally). */
	KILL
}
