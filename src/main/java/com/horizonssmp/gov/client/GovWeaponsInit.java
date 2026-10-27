package com.horizonssmp.gov.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.Locale;
import java.util.Arrays;

import static com.horizonssmp.gov.client.GovWeaponsConstants.*;

final class GovWeaponsInit {

    private GovWeaponsInit() {
    }

    static void init(final GovWeaponsPanelBase p) {
        p.labelCitizen = tr("gui.gov.weapons.citizen");
        p.labelCategory = tr("gui.gov.weapons.category");
        p.labelReason = tr("gui.gov.weapons.field.reason");
        p.labelMinutes = tr("gui.gov.weapons.field.minutes");
        p.labelSerial = tr("gui.gov.weapons.field.serial");
        p.labelType = tr("gui.gov.weapons.field.type");
        p.labelOwner = tr("gui.gov.weapons.field.owner");
        p.labelDimension = tr("gui.gov.weapons.field.dimension");
        p.labelRadius = tr("gui.gov.weapons.field.radius");
        p.labelItem = tr("gui.gov.weapons.classify_label");
        p.labelPermit = tr("gui.gov.weapons.field.permit");
        p.labelStatus = tr("gui.gov.weapons.field.status");
        p.labelHolder = tr("gui.gov.weapons.field.holder");
        p.labelIssued = tr("gui.gov.weapons.field.issued");
        p.labelExpires = tr("gui.gov.weapons.field.expires");
        p.labelIssuedBy = tr("gui.gov.weapons.field.issued_by");
        p.labelWindow = tr("gui.gov.weapons.field.valid");
        p.labelPurpose = tr("gui.gov.weapons.field.reason");
        p.labelArea = tr("gui.gov.weapons.area");
        p.labelBox = tr("gui.gov.weapons.field.box");
        p.labelCenter = tr("gui.gov.weapons.field.center");
        p.labelClass = tr("gui.gov.weapons.field.class");
        p.labelRegistered = tr("gui.gov.weapons.field.registered");
        p.labelTransfers = tr("gui.gov.weapons.field.transfers");
        p.labelDealer = tr("gui.gov.weapons.field.dealer");
        p.labelSale = tr("gui.gov.weapons.field.sale");
        p.labelSource = tr("gui.gov.weapons.source");
        p.labelAuthority = tr("gui.gov.weapons.authority");
        p.labelPermitRequired = tr("gui.gov.weapons.permit_required");
        p.labelCitizenAllowed = tr("gui.gov.weapons.citizen_allowed");

        p.buttonFilter = caps(tr("gui.gov.weapons.filter_button"));
        p.buttonRegister = buttonLabel("gui.gov.weapons.register_button");
        p.buttonRecord = caps(tr("gui.gov.weapons.record"));
        p.buttonTransfer = buttonLabel("gui.gov.weapons.transfer_button");
        p.buttonRevoke = buttonLabel("gui.gov.weapons.revoke_button");
        p.buttonLoss = buttonLabel("gui.gov.weapons.loss_button");
        p.buttonIssue = caps(tr("gui.gov.weapons.permit_issue_renew"));
        p.buttonLoad = caps(tr("gui.gov.weapons.load"));
        p.buttonCreateArea = buttonLabel("gui.gov.weapons.area_create_button");
        p.buttonClassify = caps(tr("gui.gov.weapons.classify_button"));
        p.buttonHeld = caps(tr("gui.gov.weapons.held_button"));
        p.buttonSuspend = buttonLabel("gui.gov.weapons.permit_suspend_button");
        p.buttonPrev = caps(tr("gui.gov.previous"));
        p.buttonNext = caps(tr("gui.gov.next"));
        p.buttonRefresh = caps(tr("gui.gov.refresh"));
        p.buttonCancel = caps(tr("gui.gov.weapons.cancel"));
        p.buttonClose = caps(tr("gui.gov.weapons.close"));
        p.buttonRetry = caps(tr("gui.gov.card.retry"));

        p.textLoading = tr("gui.gov.weapons.loading");
        p.textError = tr("gui.gov.weapons.error");
        p.textEmptyAll = tr("gui.gov.weapons.empty.all");
        p.textEmptyOwn = tr("gui.gov.weapons.empty.own");
        p.textPermitEmpty = tr("gui.gov.weapons.permit_empty");
        p.textAreaEmpty = tr("gui.gov.weapons.area_empty");
        p.textNoSelection = tr("gui.gov.weapons.no_selection");
        p.textReasonRequired = tr("gui.gov.weapons.reason_required");
        p.textFieldsRequired = tr("gui.gov.weapons.fields_required");
        p.textClassifyRequired = tr("gui.gov.weapons.classify_required");
        p.textNoCitizenPath = tr("gui.gov.weapons.no_citizen_path");
        p.textExplosivePath = tr("gui.gov.weapons.explosive_path");
        p.textNoPath = tr("gui.gov.weapons.no_path");
        p.textYes = tr("gui.gov.yes");
        p.textNo = tr("gui.gov.no");
        p.textConfirmPrefix = tr("gui.gov.weapons.confirm_prefix");
        p.textPolicyShort = tr("gui.gov.weapons.area_policy_short");
        p.textPolicyLong = tr("gui.gov.weapons.area_policy");
        p.textClassifierPolicy = tr("gui.gov.weapons.classifier_policy");

        p.permitClassLabels = new String[] {
            tr("gui.gov.weapons.permit.l"), tr("gui.gov.weapons.permit.s"),
            tr("gui.gov.weapons.permit.r"), tr("gui.gov.weapons.permit.p"),
            tr("gui.gov.weapons.permit.h"), tr("gui.gov.weapons.permit.d"),
            tr("gui.gov.weapons.permit.g"), tr("gui.gov.weapons.permit.blasting")
        };
        p.filterLabels = new String[] {
            caps(tr("gui.gov.weapons.filter.all")), caps(tr("gui.gov.weapons.filter.active")),
            caps(tr("gui.gov.weapons.filter.revoked")), caps(tr("gui.gov.weapons.filter.lost"))
        };
        p.filterLabel = p.buttonFilter + " " + p.filterLabels[0];
        p.policyText = p.textPolicyLong;
        Arrays.fill(p.topBars, EMPTY);
        Arrays.fill(p.bottoms, EMPTY);
        Arrays.fill(p.statuses, EMPTY);
        Arrays.fill(p.bodies, EMPTY);
        Arrays.fill(p.actions, EMPTY);
        Arrays.fill(p.lists, EMPTY);
        Arrays.fill(p.listBodies, EMPTY);
        Arrays.fill(p.details, EMPTY);
        Arrays.fill(p.summaries, EMPTY);
    }

    private static String tr(final String key, final Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static String caps(final String text) {
        return text == null ? "" : text.toUpperCase(Locale.ROOT);
    }

    private static String buttonLabel(final String key) {
        return caps(tr(key)).replace("...", "").strip();
    }
}
