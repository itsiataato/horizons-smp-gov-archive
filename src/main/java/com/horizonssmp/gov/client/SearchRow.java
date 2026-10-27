package com.horizonssmp.gov.client;

import net.minecraft.network.chat.Component;

record SearchRow(int chapterIndex, String articleId, Component title, String meta) {
}
