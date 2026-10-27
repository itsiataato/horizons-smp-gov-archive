package com.horizonssmp.gov.client;


import com.horizonssmp.gov.client.GovDashboardScreen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.common.BusinessKeys;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.network.GovActionResultPayload;
import com.horizonssmp.gov.network.GovNetwork;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import com.horizonssmp.gov.client.GovBusinessesTypes.*;
import static com.horizonssmp.gov.client.GovBusinessesTypes.*;

final class GovBusinessesUtil {

        static String tr(final String key, final Object... args) {
        return Component.translatable(key, args).getString();
    }

        static String upper(final String text) {
        return text == null ? "" : text.toUpperCase(Locale.ROOT);
    }

        static int intField(final Map<String, String> fields, final String key) {
        return GovFields.intField(fields, key);
    }

        static long longField(final Map<String, String> fields, final String key) {
        return GovFields.longField(fields, key);
    }

        static String dash(final String value) {
        return GovFields.dash(value);
    }

        static String when(final long epochMillis) {
        return GovFields.when(epochMillis);
    }

}
