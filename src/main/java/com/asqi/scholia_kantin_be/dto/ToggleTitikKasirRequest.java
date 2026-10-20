package com.asqi.scholia_kantin_be.dto;

/**
 * Body opsional {@code PATCH /api/pengaturan-kantin/titik-kasir/{id}/toggle} (issue #228).
 *
 * <p>{@code aktif} boleh kosong: bila tidak dikirim (atau body kosong sama sekali),
 * status titik kasir dibalik dari nilai saat ini. Bila dikirim, nilainya dipakai
 * apa adanya sehingga pemanggilan berulang idempoten.
 */
public record ToggleTitikKasirRequest(Boolean aktif) {
}
