package com.asqi.scholia_kantin_be.service.laporan;

import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.service.integrasi.InfoSiswa;
import com.asqi.scholia_kantin_be.support.EnabledIfDockerAvailable;
import com.asqi.scholia_kantin_be.support.FixtureInduk;
import com.asqi.scholia_kantin_be.support.TestcontainersConfig;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT khusus <b>jumlah query</b> rincian saldo mengendap (issue #413).
 *
 * <p>Kelas terpisah karena mengaktifkan {@code hibernate.generate_statistics}
 * lewat properti konteks — properti itu hanya dipakai untuk membuktikan klaim
 * "jumlah query konstan" (DoD #413), sehingga tidak perlu dibebankan ke seluruh
 * IT laporan lain.
 *
 * <p>Yang diuji: rincian untuk 1 siswa dan 4 siswa menempuh jumlah query JDBC
 * yang <b>sama</b>. Pola N+1 lama akan menghasilkan 1 + 3N query (13 untuk 4
 * siswa), jadi kesetaraan ini adalah bukti langsung bahwa loop query per siswa
 * sudah hilang.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
@DisplayName("LaporanSaldoService — jumlah query konstan (issue #413)")
class LaporanSaldoBatchQueryIT extends LaporanITSupport {

    @Autowired
    private LaporanSaldoService laporan;

    @Autowired
    private EntityManagerFactory emf;

    @Test
    @DisplayName("#413: jumlah query rincian saldo tidak tumbuh mengikuti jumlah siswa")
    void jumlahQueryKonstan() {
        long siswa3 = 300L;
        long siswa4 = 400L;
        FixtureInduk.siswa(jdbc, SEKOLAH, siswa3, siswa4);
        for (long id : new long[]{SISWA, SISWA2, siswa3, siswa4}) {
            topup(id, SubjekTipe.SISWA, 20_000, JenisMutasiSaldo.TOPUP_TUNAI);
        }

        org.mockito.Mockito.when(cariSiswaPort.cari(SEKOLAH, "satu", false, 20))
                .thenReturn(List.of(infoSiswa(SISWA, "2024001", "Budi Santoso")));
        org.mockito.Mockito.when(cariSiswaPort.cari(SEKOLAH, "banyak", false, 20))
                .thenReturn(List.of(
                        infoSiswa(SISWA, "2024001", "Budi Santoso"),
                        infoSiswa(SISWA2, "2024002", "Siswa Dua"),
                        infoSiswa(siswa3, "2024003", "Siswa Tiga"),
                        infoSiswa(siswa4, "2024004", "Siswa Empat")));

        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);

        long querySatuSiswa = jumlahQuery(stats, () ->
                assertThat(laporan.saldoMengendapSiswa(SEKOLAH, "satu", 20)).hasSize(1));
        long queryEmpatSiswa = jumlahQuery(stats, () ->
                assertThat(laporan.saldoMengendapSiswa(SEKOLAH, "banyak", 20)).hasSize(4));

        assertThat(queryEmpatSiswa)
                .as("query untuk 4 siswa harus sama dengan 1 siswa (pola N+1 = 1 + 3N)")
                .isGreaterThanOrEqualTo(3L)
                .isEqualTo(querySatuSiswa)
                .isLessThanOrEqualTo(6L);
    }

    /** Hitung prepared statement JDBC yang dijalankan selama {@code aksi}. */
    private long jumlahQuery(Statistics stats, Runnable aksi) {
        stats.clear();
        aksi.run();
        return stats.getPrepareStatementCount();
    }

    private static InfoSiswa infoSiswa(long id, String nis, String nama) {
        return InfoSiswa.builder().siswaId(id).nis(nis).nama(nama).kelas("7A").build();
    }
}
