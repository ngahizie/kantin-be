package com.asqi.scholia_kantin_be.dev;

import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.service.integrasi.CariSiswaPort;
import com.asqi.scholia_kantin_be.service.integrasi.InfoSiswa;
import com.asqi.scholia_kantin_be.service.kasir.LedgerSaldoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Implementasi {@link CariSiswaPort} untuk <b>profil demo</b> (issue #214).
 *
 * <p>Aktif hanya bila {@code kantin.demo.enabled=true}. Menyediakan data siswa
 * tiruan agar alur TU (top-up tunai §9.2, kontrol siswa §9.6, refund siswa
 * keluar §9.3) bisa diuji <b>tanpa</b> menunggu data/kontrak admin-be (Q7).
 *
 * <p>Saldo diambil dari <b>ledger lokal</b> kantin-be ({@link LedgerSaldoService})
 * — jadi angka yang tampil konsisten dengan data nyata hasil tap/top-up demo.
 * {@code @Primary} agar menang atas {@link com.asqi.scholia_kantin_be.service.integrasi.CariSiswaFallback}.
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "kantin.demo", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class CariSiswaDemo implements CariSiswaPort {

    /** Data siswa tiruan: id, NIS, nama, kelas. */
    private static final List<SiswaDemo> DATA = List.of(
            new SiswaDemo(101L, "2024001", "Budi Santoso", "7A", false),
            new SiswaDemo(102L, "2026002", "Siti Rahmawati", "XI IPS 2", false),
            new SiswaDemo(103L, "2024003", "Andi Wijaya", "8B", false),
            new SiswaDemo(104L, "2024004", "Dewi Lestari", "8B", false),
            new SiswaDemo(105L, "2024005", "Rizky Pratama", "9C", false),
            new SiswaDemo(106L, "2023006", "Maya Sari", "9C", true), // nonaktif (lulus)
            new SiswaDemo(107L, "2024007", "Abizard", "7A", false),
            new SiswaDemo(108L, "2024008", "Fajar Nugraha", "7B", false),
            new SiswaDemo(109L, "2024009", "Citra Kirana", "8A", false));

    private final LedgerSaldoService ledgerSaldo;
    private final JdbcTemplate jdbc;

    @Override
    public List<InfoSiswa> cari(Long sekolahId, String kataKunci, boolean hanyaTidakAktif, int batas) {
        String q = kataKunci == null ? "" : kataKunci.trim().toLowerCase(Locale.ROOT);

        List<InfoSiswa> hasil = DATA.stream()
                .filter(s -> !hanyaTidakAktif || s.tidakAktif())
                .filter(s -> q.isEmpty()
                        || s.nama().toLowerCase(Locale.ROOT).contains(q)
                        || s.nis().contains(q))
                .limit(Math.max(1, batas))
                .map(s -> InfoSiswa.builder()
                        .siswaId(s.id())
                        .nis(s.nis())
                        .nama(s.nama())
                        .kelas(s.kelas())
                        .tidakAktif(s.tidakAktif())
                        .saldo(ledgerSaldo.saldo(sekolahId, s.id()))
                        .build())
                .toList();

        log.debug("[DEMO] Cari siswa (Q7) sekolah={} q='{}' hanyaTidakAktif={} → {} hasil",
                sekolahId, kataKunci, hanyaTidakAktif, hasil.size());
        return hasil;
    }

    /** Baris data siswa tiruan. */
    private record SiswaDemo(Long id, String nis, String nama, String kelas, boolean tidakAktif) {
    }

    @Override
    public Optional<InfoSiswa> cariBerdasarkanId(Long sekolahId, Long siswaId) {
        if (siswaId == null) {
            return Optional.empty();
        }
        return DATA.stream()
                .filter(s -> s.id().equals(siswaId))
                .findFirst()
                .map(s -> InfoSiswa.builder()
                        .siswaId(s.id())
                        .nis(s.nis())
                        .nama(s.nama())
                        .kelas(s.kelas())
                        .tidakAktif(s.tidakAktif())
                        .saldo(ledgerSaldo.saldo(sekolahId, s.id()))
                        .build());
    }

    @Override
    public Map<Long, InfoSiswa> cariBanyak(Long sekolahId, Collection<Long> siswaIds) {
        if (siswaIds == null || siswaIds.isEmpty()) {
            return Map.of();
        }
        return DATA.stream()
                .filter(s -> siswaIds.contains(s.id()))
                .collect(Collectors.toMap(SiswaDemo::id, s -> InfoSiswa.builder()
                        .siswaId(s.id())
                        .nis(s.nis())
                        .nama(s.nama())
                        .kelas(s.kelas())
                        .tidakAktif(s.tidakAktif())
                        .saldo(ledgerSaldo.saldo(sekolahId, s.id()))
                        .build()));
    }

    /**
     * UID kartu siswa dari fixture demo {@code dev_siswa_rfid} (issue #301).
     *
     * <p>Kontrak sama dengan adapter REST: UID <b>tidak</b> ikut pada daftar
     * siswa ({@link #cari}/{@link #cariBanyak}), hanya diberikan lewat lookup
     * per siswa. Fixture demo adalah data seed lokal (bukan identitas
     * produksi), sehingga tidak ada UID yang dikarang.
     *
     * <p>Fail-open: tabel fixture belum dimuat / query gagal → {@link Optional#empty()}.
     */
    @Override
    public Optional<String> cariUidSiswa(Long sekolahId, Long siswaId) {
        if (sekolahId == null || siswaId == null) {
            return Optional.empty();
        }
        try {
            List<String> uid = jdbc.query(
                    "SELECT rfid_uid FROM dev_siswa_rfid WHERE sekolah_id = ? AND siswa_id = ?",
                    (rs, rowNum) -> rs.getString("rfid_uid"),
                    sekolahId, siswaId);
            if (uid.isEmpty()) {
                return Optional.empty();
            }
            String nilai = uid.get(0);
            return (nilai == null || nilai.isBlank()) ? Optional.empty() : Optional.of(nilai.trim());
        } catch (DataAccessException e) {
            log.debug("[DEMO] Fixture UID siswa tidak tersedia (sekolah={}, siswa={}): {}",
                    sekolahId, siswaId, e.getMessage());
            return Optional.empty();
        }
    }
}
