/**
 * The persisted government data records and their NBT serialization.
 *
 * <h2>Why explicit {@code CompoundTag} read/write instead of {@code Codec}</h2>
 *
 * <p>These records are serialized with hand-written {@link
 * com.horizonssmp.gov.common.records.NbtSupport} readers and writers rather than with Mojang
 * {@code Codec}s. The reasons, in order of weight:
 *
 * <ol>
 *   <li><b>Tolerant reads are a requirement, not a nicety.</b> {@code gov.dat} holds the only copy
 *       of issued fines, warrants, impounds and registrations. A missing key, a wrong tag type or a
 *       truncated list must degrade to a default value. A {@code Codec} does the opposite: it fails
 *       the whole decode, and {@code SavedData} deserialization failing means the data is silently
 *       replaced by an empty instance. Explicit readers make "never lose the file" the default.</li>
 *   <li><b>No registry or ops plumbing.</b> A {@code Codec} for these records needs a {@code
 *       RegistryOps}/{@code HolderLookup.Provider} when nested codecs touch registry objects, and
 *       {@code SavedData}'s deserializer only hands us a provider. None of these records reference
 *       a registry entry, so the plumbing would be pure ceremony.</li>
 *   <li><b>Schema is read by humans.</b> {@code gov.dat} is inspected with an NBT viewer during
 *       support work. Explicit keys with explicit defaults are readable; a generated codec shape is
 *       not.</li>
 *   <li><b>Cost.</b> Twelve records with twenty-odd nested fields would need roughly the same number
 *       of lines of {@code RecordCodecBuilder} boilerplate, but with worse failure behaviour.</li>
 * </ol>
 *
 * <p>The trade-off accepted: no automatic data fixing. It is covered instead by {@code
 * GovSavedData.schemaVersion}, which is written on every save and checked on load, so a future
 * breaking change has one obvious place to migrate from.
 *
 * <h2>Conventions</h2>
 *
 * <ul>
 *   <li>Timestamps are epoch milliseconds, {@code 0} meaning "never". They are absolute wall-clock
 *       values so an audit entry stays meaningful across restarts.</li>
 *   <li>Enums are stored by {@code name()} and read back through a per-enum {@code parse} that
 *       falls back to a documented default instead of throwing.</li>
 *   <li>Capability sets are stored as their string ids and read back through {@link
 *       com.horizonssmp.gov.common.GovCapability#byId(String)}; unknown ids are dropped, so a file
 *       written by a newer build still loads.</li>
 *   <li>Nested blocks ({@code suspect}, {@code employment}, {@code jail}, {@code impound}, {@code
 *       location}) are always present in memory. Missing on disk means the nested record's
 *       {@code NONE} value, never {@code null}, so no caller needs a null check.</li>
 * </ul>
 */
package com.horizonssmp.gov.common.records;
