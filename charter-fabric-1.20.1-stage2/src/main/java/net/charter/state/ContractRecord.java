package net.charter.state;

import java.util.UUID;

import net.minecraft.nbt.NbtCompound;

/**
 * One contract between two parties: the {@code creator}, who drew it up, and the {@code signer}, who put their
 * name to it. A contract with Charter itself has {@link #CHARTER} as its creator.
 *
 * <p>The items a contract is written on are only copies: the record in the world's saved data is what counts.
 * Once the record is gone, every copy is just ink.
 */
public final class ContractRecord {
	/** The creator of a contract signed with Charter itself. */
	public static final UUID CHARTER = new UUID(0L, 0L);
	public static final String CHARTER_NAME = "Charter";

	public final UUID id;
	public final UUID creator;
	public final String creatorName;
	public final UUID signer;
	public final String signerName;
	public final long created;

	public ContractRecord(UUID id, UUID creator, String creatorName, UUID signer, String signerName, long created) {
		this.id = id;
		this.creator = creator;
		this.creatorName = creatorName;
		this.signer = signer;
		this.signerName = signerName;
		this.created = created;
	}

	/** True when the signer signed with Charter itself rather than with another player. */
	public boolean withCharter() {
		return CHARTER.equals(creator);
	}

	NbtCompound toNbt() {
		NbtCompound tag = new NbtCompound();
		tag.putUuid("Id", id);
		tag.putUuid("Creator", creator);
		tag.putString("CreatorName", creatorName);
		tag.putUuid("Signer", signer);
		tag.putString("SignerName", signerName);
		tag.putLong("Created", created);
		return tag;
	}

	/** Reads a record, or returns null when the data is damaged. */
	static ContractRecord fromNbt(NbtCompound tag) {
		if (!tag.containsUuid("Id") || !tag.containsUuid("Creator") || !tag.containsUuid("Signer")) {
			return null;
		}
		return new ContractRecord(tag.getUuid("Id"), tag.getUuid("Creator"), tag.getString("CreatorName"),
				tag.getUuid("Signer"), tag.getString("SignerName"), tag.getLong("Created"));
	}
}
