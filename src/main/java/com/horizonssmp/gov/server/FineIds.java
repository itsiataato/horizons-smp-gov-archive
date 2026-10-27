package com.horizonssmp.gov.server;

import java.util.UUID;

import com.horizonssmp.gov.common.records.FineRecord;

public final class FineIds {

    private FineIds() {
    }

    public static String newFineId(final GovSavedData data) {
        for (int attempt = 0; attempt < 8; attempt++) {
            final String id = FineConstants.FINE_ID_PREFIX
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            if (data.fine(id) == null) {
                return id;
            }
        }
        String fallback = FineConstants.FINE_ID_PREFIX + Long.toHexString(System.nanoTime());
        while (data.fine(fallback) != null) {
            fallback = fallback + "x";
        }
        return fallback;
    }

    public static String newReceiptRef(final GovSavedData data) {
        for (int attempt = 0; attempt < 16; attempt++) {
            final String ref = FineConstants.RECEIPT_PREFIX
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            boolean taken = false;
            for (final FineRecord fine : data.fines().values()) {
                if (ref.equals(fine.paidAtReceptionRef())) {
                    taken = true;
                    break;
                }
            }
            if (!taken) {
                return ref;
            }
        }
        String fallback = FineConstants.RECEIPT_PREFIX + Long.toHexString(System.nanoTime());
        while (receiptExists(data, fallback)) {
            fallback = fallback + "x";
        }
        return fallback;
    }

    private static boolean receiptExists(final GovSavedData data, final String ref) {
        for (final FineRecord fine : data.fines().values()) {
            if (ref.equals(fine.paidAtReceptionRef())) {
                return true;
            }
        }
        return false;
    }
}
