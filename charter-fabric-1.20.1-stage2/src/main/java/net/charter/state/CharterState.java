package net.charter.state;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.charter.body.BodyPart;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

/**
 * World-wide saved data of the mod (stored in {@code data/charter_state.dat} of the overworld): the contracts
 * that have been signed, which body parts every player has lost, who is exiled and who Charter has judged.
 *
 * <p>Worlds saved by the first versions of the mod kept a plain list of "bound" players and "severed" arms.
 * Those are read as contracts with Charter and as a lost left arm, and are written in the new form from then on.
 */
public final class CharterState extends PersistentState {
	private static final String ID = "charter_state";

	/** Where an exiled player came from, so the exile can end when they walk home. */
	public static final class ExileRecord {
		public String name = "";
		public String originDimension = "minecraft:overworld";
		public double originX;
		public double originY;
		public double originZ;
		public float originYaw;
		public float originPitch;
		public String destDimension = "minecraft:overworld";
		public double destX;
		public double destZ;
		public long since;

		NbtCompound toNbt(UUID id) {
			NbtCompound tag = new NbtCompound();
			tag.putUuid("Id", id);
			tag.putString("Name", name);
			tag.putString("OriginDim", originDimension);
			tag.putDouble("OriginX", originX);
			tag.putDouble("OriginY", originY);
			tag.putDouble("OriginZ", originZ);
			tag.putFloat("OriginYaw", originYaw);
			tag.putFloat("OriginPitch", originPitch);
			tag.putString("DestDim", destDimension);
			tag.putDouble("DestX", destX);
			tag.putDouble("DestZ", destZ);
			tag.putLong("Since", since);
			return tag;
		}

		static ExileRecord fromNbt(NbtCompound tag) {
			ExileRecord record = new ExileRecord();
			record.name = tag.getString("Name");
			record.originDimension = tag.getString("OriginDim");
			record.originX = tag.getDouble("OriginX");
			record.originY = tag.getDouble("OriginY");
			record.originZ = tag.getDouble("OriginZ");
			record.originYaw = tag.getFloat("OriginYaw");
			record.originPitch = tag.getFloat("OriginPitch");
			record.destDimension = tag.getString("DestDim");
			record.destX = tag.getDouble("DestX");
			record.destZ = tag.getDouble("DestZ");
			record.since = tag.getLong("Since");
			return record;
		}
	}

	private final Map<UUID, ContractRecord> contracts = new LinkedHashMap<>();
	private final Map<UUID, Integer> bodies = new LinkedHashMap<>();
	private final Map<UUID, ExileRecord> exiled = new LinkedHashMap<>();
	private final Map<UUID, String> judged = new LinkedHashMap<>();
	private final Set<UUID> pendingExile = new LinkedHashSet<>();

	public static CharterState get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(CharterState::fromNbt, CharterState::new, ID);
	}

	// ------------------------------------------------------------------ contracts
	public ContractRecord addContract(ContractRecord record) {
		contracts.put(record.id, record);
		markDirty();
		return record;
	}

	@Nullable
	public ContractRecord contract(UUID id) {
		return contracts.get(id);
	}

	public boolean removeContract(UUID id) {
		boolean had = contracts.remove(id) != null;
		if (had) {
			markDirty();
		}
		return had;
	}

	public Collection<ContractRecord> allContracts() {
		return Collections.unmodifiableCollection(contracts.values());
	}

	/** Every contract this player has signed, oldest first. */
	public List<ContractRecord> contractsSignedBy(UUID signer) {
		List<ContractRecord> found = new ArrayList<>();
		for (ContractRecord record : contracts.values()) {
			if (record.signer.equals(signer)) {
				found.add(record);
			}
		}
		return found;
	}

	/** Every contract this player has created (never one with Charter itself), oldest first. */
	public List<ContractRecord> contractsCreatedBy(UUID creator) {
		List<ContractRecord> found = new ArrayList<>();
		for (ContractRecord record : contracts.values()) {
			if (record.creator.equals(creator)) {
				found.add(record);
			}
		}
		return found;
	}

	/** The contract in which {@code creator} drew up the terms and {@code signer} signed them, if there is one. */
	@Nullable
	public ContractRecord contractBetween(UUID creator, UUID signer) {
		for (ContractRecord record : contracts.values()) {
			if (record.creator.equals(creator) && record.signer.equals(signer)) {
				return record;
			}
		}
		return null;
	}

	/** A player is "bound" while they are the signer of at least one contract. */
	public boolean isBound(UUID id) {
		for (ContractRecord record : contracts.values()) {
			if (record.signer.equals(id)) {
				return true;
			}
		}
		return false;
	}

	/** Every player who has signed something, in the order of their oldest contract. */
	public Set<UUID> boundPlayers() {
		Set<UUID> found = new LinkedHashSet<>();
		for (ContractRecord record : contracts.values()) {
			found.add(record.signer);
		}
		return found;
	}

	// ------------------------------------------------------------------ bodies (parts lost)
	/** The bit mask of {@link BodyPart}s this player has lost; 0 for a whole body. */
	public int bodyMask(UUID id) {
		Integer mask = bodies.get(id);
		return mask == null ? 0 : mask;
	}

	public void setBodyMask(UUID id, int mask) {
		int normalized = mask & BodyPart.everything();
		if (normalized == 0) {
			if (bodies.remove(id) != null) {
				markDirty();
			}
		} else {
			Integer old = bodies.put(id, normalized);
			if (old == null || old != normalized) {
				markDirty();
			}
		}
	}

	/** Everyone who has lost something, with what they lost. */
	public Map<UUID, Integer> bodyMasks() {
		return Collections.unmodifiableMap(bodies);
	}

	// ------------------------------------------------------------------ exiled
	public boolean isExiled(UUID id) {
		return exiled.containsKey(id);
	}

	public ExileRecord exileRecord(UUID id) {
		return exiled.get(id);
	}

	public void putExile(UUID id, ExileRecord record) {
		exiled.put(id, record);
		markDirty();
	}

	public ExileRecord removeExile(UUID id) {
		ExileRecord record = exiled.remove(id);
		if (record != null) {
			markDirty();
		}
		return record;
	}

	public Map<UUID, ExileRecord> exiledPlayers() {
		return Collections.unmodifiableMap(exiled);
	}

	/** Players who were judged to exile while offline; the exile happens when they next join. */
	public boolean isPendingExile(UUID id) {
		return pendingExile.contains(id);
	}

	public void setPendingExile(UUID id, boolean pending) {
		boolean changed = pending ? pendingExile.add(id) : pendingExile.remove(id);
		if (changed) {
			markDirty();
		}
	}

	// ------------------------------------------------------------------ judged (permanently banned)
	public boolean isJudged(UUID id) {
		return judged.containsKey(id);
	}

	public void judge(UUID id, String name) {
		judged.put(id, name);
		markDirty();
	}

	public boolean pardon(UUID id) {
		boolean had = judged.remove(id) != null;
		if (had) {
			markDirty();
		}
		return had;
	}

	public Map<UUID, String> judgedPlayers() {
		return Collections.unmodifiableMap(judged);
	}

	// ------------------------------------------------------------------ saving
	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		NbtList contractList = new NbtList();
		for (ContractRecord record : contracts.values()) {
			contractList.add(record.toNbt());
		}
		nbt.put("Contracts", contractList);

		NbtList bodyList = new NbtList();
		for (Map.Entry<UUID, Integer> entry : bodies.entrySet()) {
			NbtCompound tag = new NbtCompound();
			tag.putUuid("Id", entry.getKey());
			tag.putInt("Lost", entry.getValue());
			bodyList.add(tag);
		}
		nbt.put("Body", bodyList);

		NbtList exiledList = new NbtList();
		for (Map.Entry<UUID, ExileRecord> entry : exiled.entrySet()) {
			exiledList.add(entry.getValue().toNbt(entry.getKey()));
		}
		nbt.put("Exiled", exiledList);

		NbtList judgedList = new NbtList();
		for (Map.Entry<UUID, String> entry : judged.entrySet()) {
			NbtCompound tag = new NbtCompound();
			tag.putUuid("Id", entry.getKey());
			tag.putString("Name", entry.getValue());
			judgedList.add(tag);
		}
		nbt.put("Judged", judgedList);

		NbtList pendingList = new NbtList();
		for (UUID id : pendingExile) {
			NbtCompound tag = new NbtCompound();
			tag.putUuid("Id", id);
			pendingList.add(tag);
		}
		nbt.put("PendingExile", pendingList);
		return nbt;
	}

	public static CharterState fromNbt(NbtCompound nbt) {
		CharterState state = new CharterState();

		NbtList contractList = nbt.getList("Contracts", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < contractList.size(); i++) {
			ContractRecord record = ContractRecord.fromNbt(contractList.getCompound(i));
			if (record != null) {
				state.contracts.put(record.id, record);
			}
		}
		// worlds from the first versions: "bound" players signed with Charter itself
		NbtList legacyBound = nbt.getList("Bound", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < legacyBound.size(); i++) {
			NbtCompound tag = legacyBound.getCompound(i);
			if (tag.containsUuid("Id")) {
				UUID player = tag.getUuid("Id");
				UUID id = UUID.nameUUIDFromBytes(("charter:legacy:" + player).getBytes(StandardCharsets.UTF_8));
				if (!state.contracts.containsKey(id)) {
					state.contracts.put(id, new ContractRecord(id, ContractRecord.CHARTER, ContractRecord.CHARTER_NAME, player, tag.getString("Name"), 0L));
					state.markDirty();
				}
			}
		}

		NbtList bodyList = nbt.getList("Body", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < bodyList.size(); i++) {
			NbtCompound tag = bodyList.getCompound(i);
			if (tag.containsUuid("Id")) {
				state.setBodyMask(tag.getUuid("Id"), tag.getInt("Lost"));
			}
		}
		// worlds from the first versions: a "severed" player had lost their left arm
		NbtList legacySevered = nbt.getList("Severed", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < legacySevered.size(); i++) {
			NbtCompound tag = legacySevered.getCompound(i);
			if (tag.containsUuid("Id")) {
				UUID player = tag.getUuid("Id");
				state.setBodyMask(player, state.bodyMask(player) | BodyPart.LEFT_ARM.bit());
				state.markDirty();
			}
		}

		NbtList exiledList = nbt.getList("Exiled", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < exiledList.size(); i++) {
			NbtCompound tag = exiledList.getCompound(i);
			if (tag.containsUuid("Id")) {
				state.exiled.put(tag.getUuid("Id"), ExileRecord.fromNbt(tag));
			}
		}
		NbtList judgedList = nbt.getList("Judged", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < judgedList.size(); i++) {
			NbtCompound tag = judgedList.getCompound(i);
			if (tag.containsUuid("Id")) {
				state.judged.put(tag.getUuid("Id"), tag.getString("Name"));
			}
		}
		NbtList pendingList = nbt.getList("PendingExile", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < pendingList.size(); i++) {
			NbtCompound tag = pendingList.getCompound(i);
			if (tag.containsUuid("Id")) {
				state.pendingExile.add(tag.getUuid("Id"));
			}
		}
		return state;
	}
}
