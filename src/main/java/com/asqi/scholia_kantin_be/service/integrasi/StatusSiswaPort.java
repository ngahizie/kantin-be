package com.asqi.scholia_kantin_be.service.integrasi;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Port status siswa &amp; blokir kartu siswa ke admin-be (PRD §9.3, OPEN-QUESTIONS
 * <b>Q7</b>).
 *
 * <p><b>Kenapa port (interface)?</b> Sama seperti {@link KartuLookupPort}:
 * kantin-be <b>tidak</b> menyimpan data induk siswa (ADR-0002), sehingga status
 * keaktifan siswa (lulus/pindah/keluar) dan pemblokiran kartu siswa hanya bisa
 * diketahui/dilakukan lewat admin-be yang kontraknya belum final (Q7). Dengan
 * memisahkan port, alur refund/pindah saldo (PRD §9.3) bisa ditulis, diuji
 * (dengan fake), dan <b>tidak terblokir</b>. Saat Q7 terjawab, cukup tambahkan
 * implementasi nyata (mis. {@code SiswaKartuClient} berbasis REST) dan tandai
 * {@code @Primary} — tanpa menyentuh logika bisnis.
 *
 * <p><b>Wajib</b> mematuhi PRD §11.11: status blokir <b>tidak</b> boleh di-cache
 * — implementasi nyata harus memeriksa server setiap pemanggilan.
 */
public interface StatusSiswaPort {

    /**
     * Apakah siswa sudah tidak aktif (lulus/pindah/keluar)?
     *
     * @param sekolahId tenant pemanggil (PRD §11.4)
     * @param siswaId   ID siswa (lokal kantin / admin-be)
     * @return {@code TRUE} nonaktif, {@code FALSE} aktif, {@code null} bila
     *         status tidak diketahui (integrasi belum siap — fail-safe)
     */
    Boolean tidakAktif(Long sekolahId, Long siswaId);

    /**
     * Blokir kartu siswa (mis. setelah saldo direfund/dipindahkan saat siswa
     * keluar — PRD §9.3). Berlaku <b>instan</b> (tanpa cache, PRD §11.11).
     *
     * <p>Implementasi nyata <b>wajib</b> menangkap kegagalan jaringan dan
     * <b>tidak</b> melempar — pemblokiran bersifat <i>best-effort</i> dan tidak
     * boleh membatalkan perpindahan uang yang sudah tercatat di ledger.
     *
     * @param alasan alasan blokir (audit/log)
     */
    void blokirKartu(Long sekolahId, Long siswaId, String alasan);

    /**
     * Status keaktifan <b>banyak</b> siswa sekaligus (issue #298).
     *
     * <p>Default memanggil {@link #tidakAktif} per ID agar implementasi lama tetap
     * benar. Adapter REST nyata <b>menimpa</b> method ini dengan satu permintaan
     * batch, sehingga daftar kandidat refund tidak memicu N+1 panggilan.
     *
     * @param sekolahId tenant pemanggil
     * @param siswaIds  daftar ID siswa (boleh kosong)
     * @return peta {@code siswaId → tidakAktif}; nilai {@code null} = tidak diketahui
     */
    default Map<Long, Boolean> tidakAktifBanyak(Long sekolahId, Collection<Long> siswaIds) {
        Map<Long, Boolean> hasil = new LinkedHashMap<>();
        if (siswaIds == null) {
            return hasil;
        }
        for (Long id : siswaIds) {
            if (id != null) {
                hasil.put(id, tidakAktif(sekolahId, id));
            }
        }
        return hasil;
    }
}
