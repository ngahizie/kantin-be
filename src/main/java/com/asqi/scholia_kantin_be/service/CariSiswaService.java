package com.asqi.scholia_kantin_be.service;

import com.asqi.scholia_kantin_be.service.integrasi.CariSiswaPort;
import com.asqi.scholia_kantin_be.service.integrasi.InfoSiswa;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pencarian siswa untuk layar TU/kantin (PRD §9.2) — issue #214.
 *
 * <p>Delegasi ke {@link CariSiswaPort}. Data siswa milik {@code admin-be};
 * kantin-be hanya memperkaya dengan saldo dari ledger lokal (dilakukan di
 * adapter port, lihat {@code CariSiswaDemo}).
 *
 * <p>Dipakai untuk: top-up tunai (memilih siswa), kontrol siswa (§9.6), dan
 * daftar kandidat refund siswa keluar (§9.3, {@code hanyaTidakAktif=true}).
 */
@Service
@RequiredArgsConstructor
public class CariSiswaService {

    /** Batas aman jumlah hasil agar satu permintaan tidak menarik ribuan baris. */
    public static final int BATAS_MAKS = 50;

    private final CariSiswaPort cariSiswaPort;

    /**
     * Cari siswa berdasarkan nama/NIS.
     *
     * @param sekolahId      tenant dari JWT
     * @param kataKunci      nama/NIS (boleh kosong)
     * @param hanyaTidakAktif hanya siswa nonaktif (untuk refund §9.3)
     * @param batas          jumlah maksimum (dikunci ke [1, {@value #BATAS_MAKS}])
     */
    public List<InfoSiswa> cari(Long sekolahId, String kataKunci, boolean hanyaTidakAktif, int batas) {
        int batasAman = Math.min(Math.max(batas, 1), BATAS_MAKS);
        return cariSiswaPort.cari(sekolahId, kataKunci, hanyaTidakAktif, batasAman);
    }

    /**
     * Identitas satu siswa berdasarkan ID (issue #297) — dipakai profil riwayat
     * saldo agar NIS/nama/kelas terisi tanpa memindai hasil pencarian.
     *
     * <p>Fail-open: ID kosong atau integrasi tidak tersedia → {@link Optional#empty()}.
     *
     * @param sekolahId tenant dari JWT
     * @param siswaId   ID siswa yang sudah terbukti milik tenant
     */
    public Optional<InfoSiswa> identitas(Long sekolahId, Long siswaId) {
        if (sekolahId == null || siswaId == null) {
            return Optional.empty();
        }
        return cariSiswaPort.cariBerdasarkanId(sekolahId, siswaId);
    }

    /**
     * Identitas banyak siswa dalam satu panggilan (issue #298) — dipakai daftar
     * kandidat refund agar tidak terjadi N+1 lintas layanan.
     *
     * @param sekolahId tenant dari JWT
     * @param siswaIds  ID siswa milik tenant (boleh kosong)
     * @return peta {@code siswaId → identitas}; kosong bila tidak ada data
     */
    public Map<Long, InfoSiswa> identitasBanyak(Long sekolahId, Collection<Long> siswaIds) {
        if (sekolahId == null || siswaIds == null || siswaIds.isEmpty()) {
            return Map.of();
        }
        return cariSiswaPort.cariBanyak(sekolahId, siswaIds);
    }

    /**
     * UID kartu RFID satu siswa (issue #301).
     *
     * <p>Hanya dipanggil dari endpoint berperan admin sekolah/TU — UID tidak
     * ikut pada daftar siswa. Fail-open: ID kosong, siswa tidak ditemukan, atau
     * integrasi gagal → {@link Optional#empty()} (bukan tebakan).
     *
     * @param sekolahId tenant dari JWT
     * @param siswaId   ID siswa yang sudah terbukti milik tenant
     */
    public Optional<String> uidKartu(Long sekolahId, Long siswaId) {
        if (sekolahId == null || siswaId == null) {
            return Optional.empty();
        }
        return cariSiswaPort.cariUidSiswa(sekolahId, siswaId);
    }
}
