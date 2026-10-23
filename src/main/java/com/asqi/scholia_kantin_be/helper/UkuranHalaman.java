package com.asqi.scholia_kantin_be.helper;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;

/** Util paginasi bersama (issue #257) — satu aturan untuk semua endpoint berhalaman. */
public final class UkuranHalaman {

    /** Batas maksimum ukuran halaman default untuk endpoint daftar. */
    public static final int UKURAN_MAKS = 200;

    private UkuranHalaman() {
    }

    /**
     * Validasi ukuran halaman <b>client-facing</b>: {@code ukuran < 1} atau
     * {@code ukuran > maks} → {@link InvalidOperationException} (HTTP 400),
     * BUKAN diam-diam di-clamp (PRD §6.2, issue #257).
     *
     * @param ukuran ukuran halaman dari client
     * @param maks   batas atas endpoint ini (umumnya {@link #UKURAN_MAKS} = 200)
     * @return ukuran yang sudah tervalidasi (melempar bila tidak valid)
     */
    public static int validasi(int ukuran, int maks) {
        if (ukuran < 1) {
            throw new InvalidOperationException("ukuran minimal 1");
        }
        if (ukuran > maks) {
            throw new InvalidOperationException("ukuran maksimal " + maks);
        }
        return ukuran;
    }
}
