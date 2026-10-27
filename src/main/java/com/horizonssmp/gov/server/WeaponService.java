package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.GovTime;
import com.horizonssmp.gov.common.TownCore;
import com.horizonssmp.gov.common.WeaponKeys;
import com.horizonssmp.gov.common.enums.DutyState;
import com.horizonssmp.gov.common.enums.HistoryKind;
import com.horizonssmp.gov.common.enums.PermitClass;
import com.horizonssmp.gov.common.enums.PermitStatus;
import com.horizonssmp.gov.common.enums.WeaponCategory;
import com.horizonssmp.gov.common.enums.WeaponClass;
import com.horizonssmp.gov.common.records.AreaPermitRecord;
import com.horizonssmp.gov.common.records.PermitRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;
import com.horizonssmp.gov.common.records.WeaponRecord;
import com.horizonssmp.gov.config.GovConfig;


public final class WeaponService {

    public static final int MAX_TNT = 16;
    public static final long TRANSFER_WINDOW_MS = GovTime.days(1);
    public static final long REGISTRATION_WINDOW_MS = GovTime.days(1);
    public static final long MAX_AREA_MINUTES = 7L * 24L * 60L;
    public static final int MAX_AREA_RADIUS = 512;
    public static final int MIN_AREA_RADIUS = 1;
    public static final long MAX_SUSPENSION_MINUTES = 365L * 24L * 60L;
    public static final String WEAPON_ID_PREFIX = "w-";
    public static final String ARTICLE_EXPLOSIVES = "5.07";
    public static final String ARTICLE_MILITARY = "5.08";
    public static final String ARTICLE_TRANSFER = "5.09";
    public static final String ARTICLE_SELF_DEFENSE = "5.11";
    public static final String ARTICLE_SCHEDULE = "5.13";
    public static final String ARTICLE_CARRY = "5.14";
    public static final String ARTICLE_MINING = "8.09";
    public static final String ARTICLE_REGISTRY = "8.12";
    public static final String ARTICLE_PRIVACY = "9.04";
    static final Pattern SERIAL = Pattern.compile("^[A-Z0-9][A-Z0-9_-]{2,31}$");
    static final Pattern DIMENSION = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");
    static final Pattern ITEM_ID = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");

    /** Who is acting, and what they can do. */
    public record Actor(UUID uuid, String name, Set<GovCapability> capabilities) {

        public Actor {
            name = name == null ? "" : name;
            capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        }

        public boolean can(final GovCapability capability) {
            return capability != null && capabilities.contains(capability);
        }
    }

    /** The outcome of a registry or permit call. */
    public record Outcome(boolean ok, ErrorCode code, String message, String id) {

        public Outcome {
            message = message == null ? "" : message;
            id = id == null ? "" : id;
        }

        public static Outcome ok(final String message) {
            return new Outcome(true, null, message, "");
        }

        public static Outcome ok(final String message, final String id) {
            return new Outcome(true, null, message, id);
        }

        public static Outcome refused(final ErrorCode code, final String message) {
            return new Outcome(false, code, message, "");
        }
    }

    /** One registry row with the owner's display name materialized. */
    public record WeaponRow(WeaponRecord weapon, String ownerName) {
    }

    /** One page of the registry. */
    public record WeaponPage(List<WeaponRow> rows, int total, int page, int pages) {

        public WeaponPage {
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }

    /** One page of permits. */
    public record PermitPage(List<PermitRecord> rows, int total, int page, int pages) {

        public PermitPage {
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }

    /** One page of explosion-area permits. */
    public record AreaPage(List<AreaPermitRecord> rows, int total, int page, int pages) {

        public AreaPage {
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }

    /** The actor's facts for an enforcement decision, without a ServerPlayer. */
    public record Subject(
            UUID uuid, String name, Set<GovCapability> capabilities, PlayerRecord record) {

        public Subject {
            name = name == null ? "" : name;
            capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        }

        public boolean can(final GovCapability capability) {
            return capability != null && capabilities.contains(capability);
        }

        public boolean onDuty() {
            return record != null && record.duty() == DutyState.ON_DUTY;
        }
    }

    /** The interaction boundary being checked. */
    public enum Action {
        PICKUP,
        USE,
        PLACE,
        ATTACK,
        IGNITE,
        EXPLODE
    }

    /** One enforcement answer. A denial always carries a readable message and an audit summary. */
    public record Decision(boolean allowed, ErrorCode code, String message, String auditReason) {

        public Decision {
            message = message == null ? "" : message;
            auditReason = auditReason == null ? "" : auditReason;
        }

        public static Decision allow() {
            return new Decision(true, null, "", "");
        }

        public static Decision deny(
                final ErrorCode code, final String message, final String auditReason) {
            return new Decision(false, code, message, auditReason);
        }
    }


    private WeaponService() {
    }

public static String canonicalSerial(final String raw) { return WeaponServiceSerials.canonicalSerial(raw); }
public static String invalidSerialMessage(final String raw) { return WeaponServiceSerials.invalidSerialMessage(raw); }
public static WeaponRecord bySerial(final GovSavedData data, final String raw) { return WeaponServiceSerials.bySerial(data, raw); }
public static WeaponRecord find(final GovSavedData data, final String raw) { return WeaponServiceSerials.find(data, raw); }
public static String noSuchWeaponMessage(final String raw) { return WeaponServiceSerials.noSuchWeaponMessage(raw); }
public static String ownerName(final GovSavedData data, final UUID ownerUuid) { return WeaponServiceSerials.ownerName(data, ownerUuid); }
public static WeaponService.Outcome register( final GovSavedData data, final WeaponService.Actor actor, final UUID ownerUuid, final String ownerName, final String rawClass, final String rawSerial, final String rawType, final long now) { return WeaponServiceRegistry.register(data, actor, ownerUuid, ownerName, rawClass, rawSerial, rawType, now); }
public static WeaponService.Outcome registerDealerSale( final GovSavedData data, final WeaponService.Actor actor, final UUID ownerUuid, final String ownerName, final String rawClass, final String rawSerial, final String rawType, final long soldAt, final long now) { return WeaponServiceRegistry.registerDealerSale(data, actor, ownerUuid, ownerName, rawClass, rawSerial, rawType, soldAt, now); }
public static WeaponService.Outcome register( final GovSavedData data, final WeaponService.Actor actor, final UUID ownerUuid, final String ownerName, final String rawClass, final String rawSerial, final String rawType, final long now, final long soldAt, final boolean dealerSale) { return WeaponServiceRegistry.register(data, actor, ownerUuid, ownerName, rawClass, rawSerial, rawType, now, soldAt, dealerSale); }
public static PermitRecord permitForRegistration( final GovSavedData data, final UUID ownerUuid, final WeaponCategory category, final long now) { return WeaponServiceRegistry.permitForRegistration(data, ownerUuid, category, now); }
public static WeaponService.Outcome issuePermit( final GovSavedData data, final WeaponService.Actor actor, final UUID holderUuid, final String holderName, final String rawClass, final String rawNote, final long seasonEndMillis, final long now) { return WeaponServicePermits.issuePermit(data, actor, holderUuid, holderName, rawClass, rawNote, seasonEndMillis, now); }
public static WeaponService.Outcome revokePermit( final GovSavedData data, final WeaponService.Actor actor, final UUID holderUuid, final String holderName, final String rawClass, final String rawReason, final long now) { return WeaponServicePermits.revokePermit(data, actor, holderUuid, holderName, rawClass, rawReason, now); }
public static WeaponService.Outcome suspendPermit( final GovSavedData data, final WeaponService.Actor actor, final UUID holderUuid, final String holderName, final String rawClass, final String rawMinutes, final String rawReason, final long now) { return WeaponServicePermits.suspendPermit(data, actor, holderUuid, holderName, rawClass, rawMinutes, rawReason, now); }
public static String noSuchPermitMessage( final GovSavedData data, final UUID holderUuid, final String holderName, final PermitClass permitClass) { return WeaponServicePermits.noSuchPermitMessage(data, holderUuid, holderName, permitClass); }
public static PermitRecord activePermit( final GovSavedData data, final UUID holderUuid, final PermitClass permitClass, final long now) { return WeaponServicePermits.activePermit(data, holderUuid, permitClass, now); }
public static PermitRecord blastingPermit( final GovSavedData data, final UUID holderUuid, final long now) { return WeaponServicePermits.blastingPermit(data, holderUuid, now); }
public static boolean militaryAuthority( final GovSavedData data, final WeaponService.Subject subject, final long now) { return WeaponServicePermits.militaryAuthority(data, subject, now); }
public static boolean onDutyClassG( final GovSavedData data, final WeaponService.Subject subject, final long now) { return WeaponServicePermits.onDutyClassG(data, subject, now); }
public static String canonicalDimension(final String raw) { return WeaponServiceAreas.canonicalDimension(raw); }
public static WeaponService.Outcome createArea( final GovSavedData data, final WeaponService.Actor actor, final String rawDimension, final String rawX, final String rawY, final String rawZ, final String rawRadius, final String rawMinutes, final String rawReason, final long now) { return WeaponServiceAreas.createArea(data, actor, rawDimension, rawX, rawY, rawZ, rawRadius, rawMinutes, rawReason, now); }
public static WeaponService.Outcome revokeArea( final GovSavedData data, final WeaponService.Actor actor, final String rawId, final String rawReason, final long now) { return WeaponServiceAreas.revokeArea(data, actor, rawId, rawReason, now); }
public static String noSuchAreaMessage(final String raw) { return WeaponServiceAreas.noSuchAreaMessage(raw); }
public static AreaPermitRecord activeAreaAt( final GovSavedData data, final String dimension, final double x, final double y, final double z, final long now) { return WeaponServiceAreas.activeAreaAt(data, dimension, x, y, z, now); }
public static WeaponService.Decision evaluate( final GovSavedData data, final WeaponService.Subject subject, final WeaponClassifier.Classification classification, final WeaponService.Action action, final WeaponGateway gateway, final List<TownCore> townCores, final long now) { return WeaponServiceEnforcement.evaluate(data, subject, classification, action, gateway, townCores, now); }
public static WeaponService.Decision evaluateAt( final GovSavedData data, final WeaponService.Subject subject, final WeaponClassifier.Classification classification, final WeaponService.Action action, final WeaponGateway gateway, final WeaponGateway.Position position, final List<TownCore> townCores, final long now) { return WeaponServiceEnforcement.evaluateAt(data, subject, classification, action, gateway, position, townCores, now); }
public static WeaponService.Decision evaluateTnt( final GovSavedData data, final WeaponService.Subject subject, final WeaponClassifier.Classification classification, final WeaponService.Action action, final WeaponGateway gateway, final WeaponGateway.Position position, final boolean town, final long now) { return WeaponServiceEnforcement.evaluateTnt(data, subject, classification, action, gateway, position, town, now); }
public static WeaponService.Decision evaluateMilitaryOrdance( final GovSavedData data, final WeaponService.Subject subject, final WeaponClassifier.Classification classification, final WeaponService.Action action, final WeaponGateway gateway, final WeaponGateway.Position position, final boolean town, final long now, final boolean nuclear) { return WeaponServiceEnforcement.evaluateMilitaryOrdance(data, subject, classification, action, gateway, position, town, now, nuclear); }
public static WeaponService.Decision evaluateHeavy( final GovSavedData data, final WeaponService.Subject subject, final WeaponClassifier.Classification classification, final WeaponService.Action action, final boolean town, final long now) { return WeaponServiceEnforcement.evaluateHeavy(data, subject, classification, action, town, now); }
public static WeaponService.Decision evaluateFirearm( final GovSavedData data, final WeaponService.Subject subject, final WeaponClassifier.Classification classification, final WeaponService.Action action, final boolean town, final long now) { return WeaponServiceEnforcement.evaluateFirearm(data, subject, classification, action, town, now); }
public static String denials( final WeaponService.Action action, final WeaponClassifier.Classification classification, final String reason) { return WeaponServiceEnforcement.denials(action, classification, reason); }
public static int tntCount(final WeaponGateway gateway, final UUID player) { return WeaponServiceEnforcement.tntCount(gateway, player); }
public static boolean canDetonateTnt( final GovSavedData data, final WeaponService.Subject subject, final String dimension, final double x, final double y, final double z, final int carriedTnt, final long now) { return WeaponServiceEnforcement.canDetonateTnt(data, subject, dimension, x, y, z, carriedTnt, now); }
public static WeaponService.Outcome registerPrecheck( final GovSavedData data, final String rawClass, final String rawSerial, final String rawType) { return WeaponServicePrechecks.registerPrecheck(data, rawClass, rawSerial, rawType); }
public static WeaponService.Outcome transferPrecheck(final GovSavedData data, final String rawSerial) { return WeaponServicePrechecks.transferPrecheck(data, rawSerial); }
public static WeaponService.Outcome permitIssuePrecheck(final String rawClass) { return WeaponServicePrechecks.permitIssuePrecheck(rawClass); }
public static WeaponService.Outcome permitPrecheck( final GovSavedData data, final UUID holderUuid, final String rawClass) { return WeaponServicePrechecks.permitPrecheck(data, holderUuid, rawClass); }
public static WeaponService.Outcome areaCreatePrecheck( final String rawDimension, final String rawX, final String rawY, final String rawZ, final String rawRadius, final String rawMinutes, final String rawReason) { return WeaponServicePrechecks.areaCreatePrecheck(rawDimension, rawX, rawY, rawZ, rawRadius, rawMinutes, rawReason); }
public static WeaponClassifier.Classification classify( final String rawItemId, final WeaponClassifier classifier) { return WeaponServicePrechecks.classify(rawItemId, classifier); }
public static String weaponType(final String rawType, final WeaponCategory category) { return WeaponServiceHelpers.weaponType(rawType, category); }
public static String newWeaponId(final GovSavedData data) { return WeaponServiceHelpers.newWeaponId(data); }
public static String newAreaId(final GovSavedData data) { return WeaponServiceHelpers.newAreaId(data); }
public static int parseInt(final String raw, final int fallback) { return WeaponServiceHelpers.parseInt(raw, fallback); }
public static long parseLong(final String raw, final long fallback) { return WeaponServiceHelpers.parseLong(raw, fallback); }
public static boolean isInt(final String raw) { return WeaponServiceHelpers.isInt(raw); }
public static String clip(final String text, final int max) { return WeaponServiceHelpers.clip(text, max); }
public static PermitRecord permitRecord( final GovSavedData data, final UUID holderUuid, final PermitClass permitClass) { return WeaponServiceHelpers.permitRecord(data, holderUuid, permitClass); }
public static String permitLabel(final String rawClass) { return WeaponServiceHelpers.permitLabel(rawClass); }
public static String permitSummary(final GovSavedData data, final UUID holderUuid, final long now) { return WeaponServiceHelpers.permitSummary(data, holderUuid, now); }
public static String permitNote(final PermitRecord permit) { return WeaponServiceHelpers.permitNote(permit); }
public static boolean registrable(final WeaponCategory category) { return WeaponServiceHelpers.registrable(category); }
public static WeaponCategory registrableCategory(final String rawClass) { return WeaponServiceHelpers.registrableCategory(rawClass); }
public static Optional<PermitClass> permitClass(final String rawClass) { return WeaponServiceHelpers.permitClass(rawClass); }
public static WeaponService.Outcome transfer( final GovSavedData data, final WeaponService.Actor actor, final String rawSerial, final UUID newOwnerUuid, final String newOwnerName, final long now) { return WeaponServiceTransfer.transfer(data, actor, rawSerial, newOwnerUuid, newOwnerName, now); }
public static WeaponService.Outcome transfer( final GovSavedData data, final WeaponService.Actor actor, final String rawSerial, final UUID newOwnerUuid, final String newOwnerName, final long requestedAt, final long now) { return WeaponServiceTransfer.transfer(data, actor, rawSerial, newOwnerUuid, newOwnerName, requestedAt, now); }
public static WeaponService.Outcome revoke( final GovSavedData data, final WeaponService.Actor actor, final String rawSerial, final String rawReason, final long now) { return WeaponServiceTransfer.revoke(data, actor, rawSerial, rawReason, now); }
public static WeaponService.Outcome reportLoss( final GovSavedData data, final WeaponService.Actor actor, final String rawSerial, final String rawReason) { return WeaponServiceTransfer.reportLoss(data, actor, rawSerial, rawReason); }
public static boolean canViewAll(final Set<GovCapability> capabilities) { return WeaponServicePages.canViewAll(capabilities); }
public static WeaponService.WeaponPage page( final GovSavedData data, final UUID viewerUuid, final Set<GovCapability> capabilities, final String rawFilter, final UUID ownerFilter, final int rawPage) { return WeaponServicePages.page(data, viewerUuid, capabilities, rawFilter, ownerFilter, rawPage); }
public static String canonicalFilter(final String raw) { return WeaponServicePages.canonicalFilter(raw); }
public static boolean matchesFilter(final WeaponRecord weapon, final String filter) { return WeaponServicePages.matchesFilter(weapon, filter); }
public static WeaponService.PermitPage permitsPage( final GovSavedData data, final UUID holderUuid, final int rawPage, final long now) { return WeaponServicePages.permitsPage(data, holderUuid, rawPage, now); }
public static WeaponService.AreaPage areaPage(final GovSavedData data, final int rawPage, final long now) { return WeaponServicePages.areaPage(data, rawPage, now); }
public static Map<String, String> listFields( final GovSavedData data, final UUID viewerUuid, final String viewerName, final Set<GovCapability> capabilities, final String rawFilter, final UUID ownerFilter, final int page, final List<String> onlineNames) { return WeaponServiceFields.listFields(data, viewerUuid, viewerName, capabilities, rawFilter, ownerFilter, page, onlineNames); }
public static Map<String, String> detailFields( final GovSavedData data, final WeaponRecord weapon) { return WeaponServiceFields.detailFields(data, weapon); }
public static Map<String, String> classificationFields( final WeaponClassifier.Classification classification) { return WeaponServiceFields.classificationFields(classification); }
public static Map<String, String> permitListFields( final GovSavedData data, final UUID holderUuid, final String holderName, final Set<GovCapability> capabilities, final int page, final long now) { return WeaponServiceFields.permitListFields(data, holderUuid, holderName, capabilities, page, now); }
public static String permitReason(final PermitRecord permit) { return WeaponServiceFields.permitReason(permit); }
public static Map<String, String> areaListFields( final GovSavedData data, final Set<GovCapability> capabilities, final int page, final long now) { return WeaponServiceFields.areaListFields(data, capabilities, page, now); }

}
