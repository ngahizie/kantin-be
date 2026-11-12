package com.asqi.scholia_kantin_be.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Saldo berjalan (cache) per siswa — <b>hot row</b> yang dikunci
 * ({@code SELECT ... FOR UPDATE}) saat debit (ADR-0003).
 *
 * <p>Turunan yang <b>selalu</b> dapat dihitung ulang dari {@link MutasiSaldo}
 * (PRD §11.1). Nilainya bukan sumber kebenaran — ledger-lah sumbernya.
 *
 * <p>PK gabungan {@code (sekolah_id, siswa_id)} via {@link IdClass}.
 * Entity ini <b>mutable</b> (satu-satunya kolom berubah: {@code saldo}) karena
 * memang cache, bukan ledger.
 */
@Entity
@Table(name = "saldo")
@IdClass(Saldo.SaldoId.class)
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Saldo {

    @Id
    @Column(name = "sekolah_id", nullable = false)
    private Long sekolahId;

    @Id
    @Column(name = "siswa_id", nullable = false)
    private Long siswaId;

    /** Saldo berjalan (rupiah integer). Tidak boleh minus (CHECK di DB). */
    @Column(name = "saldo", nullable = false)
    private Long saldo;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Kunci gabungan {@code (sekolah_id, siswa_id)}. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SaldoId implements Serializable {

        private Long sekolahId;

        private Long siswaId;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof SaldoId other)) {
                return false;
            }
            return Objects.equals(sekolahId, other.sekolahId) && Objects.equals(siswaId, other.siswaId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sekolahId, siswaId);
        }
    }
}
