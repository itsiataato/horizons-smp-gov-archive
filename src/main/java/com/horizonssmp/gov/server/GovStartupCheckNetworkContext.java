package com.horizonssmp.gov.server;

import java.util.List;

import java.util.LinkedHashMap;
import java.util.Map;
import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.ActionResult;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.network.GovActionRequestPayload;
import com.horizonssmp.gov.network.GovActionResultPayload;
import com.horizonssmp.gov.network.GovHandshakePayload;
import com.horizonssmp.gov.network.GovNetwork;
import com.horizonssmp.gov.network.GovOpenScreenPayload;
import net.minecraft.network.protocol.PacketFlow;

final class GovStartupCheckNetworkContext {

        public int checks;
    public List<String> failures;
    public GovActionResultPayload refusal;
    public Map<String, String> samplePayload;

    GovStartupCheckNetworkContext(final List<String> failures) {
        this.failures = failures;
        this.checks = 0;
    }
}
