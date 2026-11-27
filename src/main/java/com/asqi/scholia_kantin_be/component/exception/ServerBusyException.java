package com.asqi.scholia_kantin_be.component.exception;

/**
 * 503 — server sibuk karena sumber daya terbatas (issue #452).
 *
 * <p>Berbeda dari {@link ConflictException} (409): kondisinya bukan kesalahan
 * data atau aturan bisnis, melainkan kapasitas. Contoh: batas jumlah ekspor
 * laporan bersamaan sudah tercapai. Aman diulang setelah jeda singkat, dan
 * responsnya membawa header {@code Retry-After}.
 */
public class ServerBusyException extends RuntimeException {
    public ServerBusyException(String message) {
        super(message);
    }
}
