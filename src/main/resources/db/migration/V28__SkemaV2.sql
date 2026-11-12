-- =====================================================================
-- V28 — SKEMA V2 (23 tabel) : RESET + BANGUN ULANG
--
-- PERINGATAN: migrasi ini DESTRUKTIF.
--   * Semua tabel di schema `public` (kecuali flyway_schema_history)
--     dihapus, lalu skema v2 dibuat dari nol.
--   * Data lama TIDAK dimigrasikan. Ini keputusan sadar: skema v2 adalah
--     rancangan ulang total (23 tabel, PK/FK baru) dan belum ada data
--     produksi. Data contoh disediakan oleh db/seed/R__seed_dev.sql.
--
-- Kenapa reset, bukan rename?
--   Skema lama tidak pernah dibuat oleh migrasi: tabel seperti
--   `dev_pengguna` dan `dev_siswa_rfid` lahir dari Hibernate ddl-auto di
--   mesin developer, sehingga database bersih (CI/Testcontainers, mesin
--   baru, staging) tidak punya tabel itu dan `alter table ... rename`
--   gagal. Reset membuat hasil migrasi deterministik di semua lingkungan:
--   kosong maupun warisan.
--
-- Acuan: docs/skema-v2/ (ERD v2 + dokumentasi-skema.md).
-- =====================================================================

-- 1. Bersihkan skema lama (aman untuk database kosong maupun warisan).
do $$
declare
    r record;
begin
    for r in
        select tablename from pg_tables
        where schemaname = 'public' and tablename <> 'flyway_schema_history'
    loop
        execute format('drop table if exists public.%I cascade', r.tablename);
    end loop;

    -- fungsi trigger lama tidak ikut terhapus bersama tabel
    for r in
        select p.proname, pg_get_function_identity_arguments(p.oid) as args
        from pg_proc p
        join pg_namespace n on n.oid = p.pronamespace
        where n.nspname = 'public'
          -- jangan sentuh fungsi milik extension (mis. gen_random_uuid() pgcrypto)
          and not exists (
              select 1 from pg_depend d
              where d.objid = p.oid and d.deptype = 'e'
          )
    loop
        execute format('drop function if exists public.%I(%s) cascade', r.proname, r.args);
    end loop;
end $$;

-- 2. Skema v2: 23 tabel, constraint, index, trigger append-only & sinkron.

-- Dumped from database version 18.3
-- Dumped by pg_dump version 18.3

--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--

-- *not* creating schema, since initdb creates it

--
-- Name: SCHEMA public; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON SCHEMA public IS '';

--
-- Name: pgcrypto; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;

--
-- Name: EXTENSION pgcrypto; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION pgcrypto IS 'cryptographic functions';

--
-- Name: sinkron_saldo_dari_ledger(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.sinkron_saldo_dari_ledger() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
begin
    update saldo c
       set saldo      = greatest(coalesce((select sum(case when l.arah = 'KREDIT' then l.nominal
                                                            else -l.nominal end)
                                            from mutasi_saldo l
                                           where l.sekolah_id = NEW.sekolah_id
                                             and l.siswa_id   = NEW.siswa_id), 0), 0),
           updated_at = now()
     where c.siswa_id   = NEW.siswa_id
       and c.sekolah_id = NEW.sekolah_id;
    return NEW;
end;
$$;

--
-- Name: FUNCTION sinkron_saldo_dari_ledger(); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.sinkron_saldo_dari_ledger() IS 'Jaga saldo_cache.saldo = Σ (KREDIT−DEBIT) saldo_ledger per (sekolah, subjek) — PRD §11.1/§11.3, issue #255. Dipanggil trigger trg_saldo_ledger_sinkron (AFTER INSERT ON saldo_ledger).';

--
-- Name: sinkron_stok_dari_mutasi(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.sinkron_stok_dari_mutasi() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
begin
    update stok c
       set stok       = greatest(coalesce((select sum(case when m.arah = 'MASUK' then m.qty
                                                            else -m.qty end)
                                            from riwayat_stok m
                                           where m.menu_id    = NEW.menu_id
                                             and m.sekolah_id = NEW.sekolah_id), 0), 0),
           updated_at = now()
     where c.menu_id    = NEW.menu_id
       and c.sekolah_id = NEW.sekolah_id;
    return NEW;
end;
$$;

--
-- Name: FUNCTION sinkron_stok_dari_mutasi(); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.sinkron_stok_dari_mutasi() IS 'Jaga stok_cache.stok = Σ (MASUK−KELUAR) mutasi_stok per menu — PRD §11.1/§11.3, issue #255. Dipanggil trigger trg_mutasi_stok_sinkron (AFTER INSERT ON mutasi_stok).';

--
-- Name: sinkron_stok_minimum(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.sinkron_stok_minimum() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
begin
    insert into stok (menu_id, sekolah_id, stok, hpp, stok_minimum, updated_at)
    values (NEW.id, NEW.sekolah_id, 0, 0, coalesce(NEW.stok_minimum, 0), now())
    on conflict (menu_id) do update
        set stok_minimum = excluded.stok_minimum,
            updated_at   = now();
    return NEW;
end;
$$;

--
-- Name: FUNCTION sinkron_stok_minimum(); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.sinkron_stok_minimum() IS 'Jaga stok_cache.stok_minimum = menu.stok_minimum (PRD 7.5, issue #196). Dipanggil oleh trigger trg_menu_sinkron_stok_minimum pada menu (INSERT / UPDATE OF stok_minimum).';

--
-- Name: tolak_perubahan_ledger(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.tolak_perubahan_ledger() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE EXCEPTION 'Ledger append-only: operasi % ditolak pada tabel % (PRD 11.1)',
        TG_OP, TG_TABLE_NAME;
END;
$$;

--
-- Name: akun_buku_kas; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.akun_buku_kas (
    id bigint NOT NULL,
    sekolah_id bigint NOT NULL,
    nama character varying(80) NOT NULL,
    tipe character varying(10) NOT NULL,
    keterangan character varying(255),
    aktif boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_akun_buku_kas_tipe CHECK (((tipe)::text = ANY (ARRAY[('MASUK'::character varying)::text, ('KELUAR'::character varying)::text])))
);

--
-- Name: TABLE akun_buku_kas; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.akun_buku_kas IS 'Pos Buku Kas kantin (DEMO, Q8/#21). Dibuat otomatis per sekolah; fallback sampai admin-be mengonfirmasi.';

--
-- Name: catatan_audit; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.catatan_audit (
    id bigint NOT NULL,
    sekolah_id bigint NOT NULL,
    aktor_id bigint,
    aksi character varying(60) NOT NULL,
    entitas character varying(60) NOT NULL,
    entitas_id character varying(64),
    nilai_lama text,
    nilai_baru text,
    alasan character varying(500),
    waktu timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: TABLE catatan_audit; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.catatan_audit IS 'Jejak audit append-only untuk aksi sensitif (PRD 11.7). Hanya INSERT.';

--
-- Name: COLUMN catatan_audit.aktor_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.catatan_audit.aktor_id IS 'User pelaku; NULL berarti aksi sistem/scheduler (mis. auto-tutup kasir).';

--
-- Name: COLUMN catatan_audit.nilai_lama; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.catatan_audit.nilai_lama IS 'Nilai sebelum aksi (teks bebas) — untuk merekonstruksi perubahan.';

--
-- Name: jurnal_buku_kas; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.jurnal_buku_kas (
    id bigint NOT NULL,
    sekolah_id bigint NOT NULL,
    referensi_id character varying(120) NOT NULL,
    entitas character varying(40) NOT NULL,
    mutasi_id bigint,
    ref_buku_kas character varying(120),
    jumlah bigint NOT NULL,
    status character varying(20) NOT NULL,
    waktu timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: TABLE jurnal_buku_kas; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.jurnal_buku_kas IS 'Penanda posting Buku Kas per mutasi stok (PRD 5.1, 7.2). Idempotency: satu referensi per sekolah; retry tidak menggandakan entri.';

--
-- Name: COLUMN jurnal_buku_kas.referensi_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.jurnal_buku_kas.referensi_id IS 'refId deterministik entri Buku Kas (mis. KANTIN-BM-<bukti>-<menuId>) — kunci idempotency.';

--
-- Name: COLUMN jurnal_buku_kas.entitas; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.jurnal_buku_kas.entitas IS 'Jenis sumber posting: BARANG_MASUK | BARANG_MASUK_PEMBALIK | KOREKSI_SALDO.';

--
-- Name: COLUMN jurnal_buku_kas.mutasi_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.jurnal_buku_kas.mutasi_id IS 'ID entitas sumber (mutasi_stok atau saldo_ledger) — telusur. NULL bila sumber tidak ber-ID mutasi stok (mis. koreksi saldo).';

--
-- Name: COLUMN jurnal_buku_kas.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.jurnal_buku_kas.status IS 'Hasil posting. Baris hanya ditulis saat SUKSES; DILEWATI/GAGAL tidak menyimpan baris (bisa di-retry).';

--
-- Name: kartu; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kartu (
    uid character varying(50) NOT NULL,
    sekolah_id bigint NOT NULL,
    siswa_id bigint NOT NULL,
    nomor_kartu character varying(32) NOT NULL,
    aktif boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: kategori_menu; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kategori_menu (
    id bigint NOT NULL,
    sekolah_id bigint NOT NULL,
    nama character varying(100) NOT NULL,
    urutan integer DEFAULT 0 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: TABLE kategori_menu; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.kategori_menu IS 'Kategori item katalog per sekolah. Jadi dasar blokir per kategori oleh ortu (PRD 7.1, 8.3).';

--
-- Name: COLUMN kategori_menu.is_active; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kategori_menu.is_active IS 'Soft delete: FALSE = dinonaktifkan, riwayat tetap ada (PRD 7.1).';

--
-- Name: kejadian_offline; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kejadian_offline (
    id bigint NOT NULL,
    sekolah_id bigint NOT NULL,
    titik_kasir_id bigint,
    mulai timestamp with time zone NOT NULL,
    selesai timestamp with time zone,
    durasi_menit integer,
    keterangan character varying(500) NOT NULL,
    dilaporkan_oleh bigint,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_kejadian_offline_durasi CHECK (((durasi_menit IS NULL) OR (durasi_menit >= 0)))
);

--
-- Name: TABLE kejadian_offline; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.kejadian_offline IS 'Insiden offline kasir (DEMO, Q14/#23). Append-only; dasar keputusan memajukan mode offline.';

--
-- Name: menu; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.menu (
    id bigint NOT NULL,
    sekolah_id bigint NOT NULL,
    kategori_id bigint,
    nama character varying(150) NOT NULL,
    harga_jual bigint NOT NULL,
    satuan character varying(20) DEFAULT 'PCS'::character varying NOT NULL,
    foto_url character varying(500),
    stok_minimum integer DEFAULT 0 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    kode_barcode character varying(64),
    CONSTRAINT ck_menu_harga_tidak_negatif CHECK ((harga_jual >= 0)),
    CONSTRAINT ck_menu_satuan CHECK (((satuan)::text = ANY (ARRAY[('PCS'::character varying)::text, ('PORSI'::character varying)::text, ('BOTOL'::character varying)::text]))),
    CONSTRAINT ck_menu_stok_minimum_tidak_negatif CHECK ((stok_minimum >= 0))
);

--
-- Name: TABLE menu; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.menu IS 'Item katalog kantin. Harga jual rupiah integer; perubahan harga membuat snapshot transaksi lama tetap (PRD 7.1, 11.6).';

--
-- Name: COLUMN menu.stok_minimum; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.menu.stok_minimum IS 'Ambang peringatan restock — stok <= nilai ini dianggap menipis (PRD 7.1, 7.5).';

--
-- Name: COLUMN menu.is_active; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.menu.is_active IS 'Soft delete. Menu nonaktif tidak boleh dijual (PRD 7.1).';

--
-- Name: COLUMN menu.deleted_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.menu.deleted_at IS 'Timestamp soft-delete permanen dari katalog: NULL = masih terdaftar di katalog, NOT NULL = dihapus dari daftar katalog namun riwayat tetap tersimpan.';

--
-- Name: COLUMN menu.kode_barcode; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.menu.kode_barcode IS 'Kode barcode produk opsional (EAN-13, Code 128, atau kode internal); unik per sekolah untuk item aktif/nonaktif yang belum dihapus.';

--
-- Name: mutasi_saldo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mutasi_saldo (
    id bigint NOT NULL,
    sekolah_id bigint NOT NULL,
    arah character varying(10) NOT NULL,
    jenis character varying(40) NOT NULL,
    nominal bigint NOT NULL,
    saldo_setelah bigint NOT NULL,
    transaksi_id bigint,
    idempotency_key character varying(64),
    referensi_tipe character varying(40),
    referensi_id character varying(64),
    keterangan character varying(255),
    aktor_id bigint,
    waktu timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    siswa_id bigint NOT NULL,
    CONSTRAINT ck_mutasi_saldo_arah CHECK (((arah)::text = ANY (ARRAY[('KREDIT'::character varying)::text, ('DEBIT'::character varying)::text]))),
    CONSTRAINT ck_mutasi_saldo_nominal_positif CHECK ((nominal > 0)),
    CONSTRAINT ck_mutasi_saldo_saldo_tidak_minus CHECK ((saldo_setelah >= 0))
);

--
-- Name: TABLE mutasi_saldo; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.mutasi_saldo IS 'Ledger saldo append-only (PRD 11.1). Sumber kebenaran saldo; hanya INSERT.';

--
-- Name: COLUMN mutasi_saldo.saldo_setelah; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.mutasi_saldo.saldo_setelah IS 'Snapshot saldo setelah mutasi, untuk audit & deteksi drift.';

--
-- Name: COLUMN mutasi_saldo.idempotency_key; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.mutasi_saldo.idempotency_key IS 'Key dari klien kasir/callback PG; UNIQUE agar retry tidak memotong dua kali.';

--
-- Name: orang_tua; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.orang_tua (
    id bigint NOT NULL,
    sekolah_id bigint NOT NULL,
    siswa_id bigint NOT NULL,
    nama character varying(100) NOT NULL,
    telepon character varying(30),
    hubungan character varying(20) DEFAULT 'WALI'::character varying NOT NULL,
    punya_aplikasi boolean DEFAULT false NOT NULL,
    aktif boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_orang_tua_hubungan CHECK (((hubungan)::text = ANY ((ARRAY['AYAH'::character varying, 'IBU'::character varying, 'WALI'::character varying])::text[])))
);

--
-- Name: TABLE orang_tua; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.orang_tua IS 'Wali siswa. Satu siswa boleh punya lebih dari satu baris (ayah, ibu, wali).';

--
-- Name: orang_tua_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.orang_tua ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.orang_tua_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: pembatasan; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pembatasan (
    id bigint NOT NULL,
    sekolah_id bigint NOT NULL,
    siswa_id bigint NOT NULL,
    jenis character varying(20) NOT NULL,
    menu_id bigint,
    kategori_id bigint,
    nominal bigint,
    alasan character varying(500),
    aktif boolean DEFAULT true NOT NULL,
    diubah_oleh bigint,
    waktu timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_pembatasan_jenis CHECK (((jenis)::text = ANY ((ARRAY['BLOKIR_SISWA'::character varying, 'BLOKIR_ITEM'::character varying, 'LIMIT_HARIAN'::character varying])::text[]))),
    CONSTRAINT ck_pembatasan_nominal CHECK ((((jenis)::text <> 'LIMIT_HARIAN'::text) OR (nominal IS NOT NULL))),
    CONSTRAINT ck_pembatasan_target CHECK ((NOT ((menu_id IS NOT NULL) AND (kategori_id IS NOT NULL))))
);

--
-- Name: TABLE pembatasan; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.pembatasan IS 'Satu tabel untuk semua pembatasan siswa: blokir siswa, blokir menu/kategori, dan limit belanja harian.';

--
-- Name: pembatasan_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.pembatasan ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.pembatasan_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: pengaturan_kantin; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pengaturan_kantin (
    sekolah_id bigint NOT NULL,
    nama_kantin character varying(150),
    jam_tutup_otomatis time without time zone DEFAULT '23:59:00'::time without time zone NOT NULL,
    konfirmasi_manual boolean DEFAULT false NOT NULL,
    durasi_foto_detik integer DEFAULT 3 NOT NULL,
    min_topup bigint,
    maks_topup bigint,
    batas_saldo_siswa bigint,
    diperbarui_oleh bigint,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    kebijakan_saldo_mengendap character varying(30) DEFAULT 'REFUND'::character varying NOT NULL,
    ambang_hari integer DEFAULT 90 NOT NULL,
    CONSTRAINT ck_config_batas_saldo_siswa CHECK (((batas_saldo_siswa IS NULL) OR (batas_saldo_siswa >= 0))),
    CONSTRAINT ck_config_durasi_foto CHECK ((durasi_foto_detik >= 0)),
    CONSTRAINT ck_config_maks_topup CHECK (((maks_topup IS NULL) OR (maks_topup >= 0))),
    CONSTRAINT ck_config_min_topup CHECK (((min_topup IS NULL) OR (min_topup >= 0))),
    CONSTRAINT ck_config_topup_urut CHECK (((min_topup IS NULL) OR (maks_topup IS NULL) OR (min_topup <= maks_topup))),
    CONSTRAINT ck_pengaturan_ambang CHECK ((ambang_hari >= 0)),
    CONSTRAINT ck_pengaturan_kebijakan CHECK (((kebijakan_saldo_mengendap)::text = ANY ((ARRAY['REFUND'::character varying, 'PINDAH_SAUDARA'::character varying, 'TETAP_MENGENDAP'::character varying])::text[])))
);

--
-- Name: TABLE pengaturan_kantin; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.pengaturan_kantin IS 'Pengaturan kantin per sekolah (PRD 9.1). PK = sekolah_id (satu baris per sekolah).';

--
-- Name: COLUMN pengaturan_kantin.jam_tutup_otomatis; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.pengaturan_kantin.jam_tutup_otomatis IS 'Jam tutup kasir otomatis (zona sekolah) — dipakai penjadwal auto-close (PRD 6.4).';

--
-- Name: COLUMN pengaturan_kantin.min_topup; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.pengaturan_kantin.min_topup IS 'Minimum per top-up (rupiah); NULL = tanpa batas minimum (PRD 8.2).';

--
-- Name: COLUMN pengaturan_kantin.maks_topup; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.pengaturan_kantin.maks_topup IS 'Maksimum per top-up (rupiah); NULL = tanpa batas maksimum (PRD 8.2).';

--
-- Name: COLUMN pengaturan_kantin.batas_saldo_siswa; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.pengaturan_kantin.batas_saldo_siswa IS 'Batas saldo maksimum per siswa (rupiah); NULL = tanpa batas (PRD 8.2).';

--
-- Name: pengguna; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pengguna (
    id bigint NOT NULL,
    sekolah_id bigint NOT NULL,
    username text NOT NULL,
    nama text NOT NULL,
    role text NOT NULL,
    password_hash text NOT NULL,
    aktif boolean DEFAULT true NOT NULL
);

--
-- Name: riwayat_stok; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.riwayat_stok (
    id bigint NOT NULL,
    sekolah_id bigint NOT NULL,
    menu_id bigint NOT NULL,
    arah character varying(10) NOT NULL,
    jenis character varying(40) NOT NULL,
    qty integer NOT NULL,
    stok_setelah integer NOT NULL,
    hpp_snapshot bigint,
    transaksi_id bigint,
    referensi_tipe character varying(40),
    referensi_id character varying(64),
    alasan character varying(255),
    aktor_id bigint,
    waktu timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    harga_beli_satuan bigint,
    mutasi_asal_id bigint,
    aktor_nama character varying(150),
    CONSTRAINT ck_riwayat_stok_arah CHECK (((arah)::text = ANY (ARRAY[('MASUK'::character varying)::text, ('KELUAR'::character varying)::text]))),
    CONSTRAINT ck_riwayat_stok_qty_positif CHECK ((qty > 0)),
    CONSTRAINT ck_riwayat_stok_tidak_minus CHECK ((stok_setelah >= 0))
);

--
-- Name: TABLE riwayat_stok; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.riwayat_stok IS 'Ledger stok append-only (PRD 11.1). Sumber kebenaran stok; hanya INSERT.';

--
-- Name: COLUMN riwayat_stok.stok_setelah; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.riwayat_stok.stok_setelah IS 'Snapshot stok setelah mutasi, untuk audit & deteksi drift.';

--
-- Name: COLUMN riwayat_stok.hpp_snapshot; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.riwayat_stok.hpp_snapshot IS 'HPP/unit saat mutasi (PRD 7.4). Void memakai snapshot ini untuk mengembalikan stok.';

--
-- Name: COLUMN riwayat_stok.harga_beli_satuan; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.riwayat_stok.harga_beli_satuan IS 'Harga beli/unit saat BARANG_MASUK (rupiah integer). Dasar hitung HPP saat pembalik.';

--
-- Name: COLUMN riwayat_stok.mutasi_asal_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.riwayat_stok.mutasi_asal_id IS 'Baris BARANG_MASUK yang dibalik (terisi hanya untuk jenis BARANG_MASUK_PEMBALIK).';

--
-- Name: COLUMN riwayat_stok.aktor_nama; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.riwayat_stok.aktor_nama IS 'Snapshot nama aktor saat mutasi dicatat (klaim JWT nama). NULL = aksi sistem/baris lama.';

--
-- Name: saldo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.saldo (
    sekolah_id bigint NOT NULL,
    saldo bigint DEFAULT 0 NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    siswa_id bigint NOT NULL,
    CONSTRAINT ck_saldo_tidak_minus CHECK ((saldo >= 0))
);

--
-- Name: TABLE saldo; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.saldo IS 'Cache saldo berjalan. Selalu dapat dihitung ulang dari saldo_ledger. Baris dikunci saat debit.';

--
-- Name: sekolah; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sekolah (
    id bigint NOT NULL,
    nama character varying(150) NOT NULL,
    npsn character varying(20),
    alamat character varying(255),
    aktif boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: TABLE sekolah; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.sekolah IS 'Induk semua data kantin. Satu baris = satu sekolah.';

--
-- Name: sesi_kasir; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sesi_kasir (
    id bigint NOT NULL,
    sekolah_id bigint NOT NULL,
    titik_kasir_id bigint NOT NULL,
    tanggal date NOT NULL,
    status character varying(15) DEFAULT 'TERBUKA'::character varying NOT NULL,
    total_bruto bigint DEFAULT 0 NOT NULL,
    total_void bigint DEFAULT 0 NOT NULL,
    total_bersih bigint DEFAULT 0 NOT NULL,
    dibuka_at timestamp with time zone DEFAULT now() NOT NULL,
    ditutup_at timestamp with time zone,
    ditutup_oleh bigint,
    auto_tutup boolean DEFAULT false NOT NULL,
    jurnal_buku_kas boolean DEFAULT false NOT NULL,
    referensi_buku_kas character varying(64),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    uang_fisik bigint,
    selisih bigint,
    catatan character varying(500),
    CONSTRAINT ck_sesi_kasir_status CHECK (((status)::text = ANY (ARRAY[('TERBUKA'::character varying)::text, ('DITUTUP'::character varying)::text]))),
    CONSTRAINT ck_sesi_kasir_total_tidak_negatif CHECK (((total_bruto >= 0) AND (total_void >= 0) AND (total_bersih >= 0))),
    CONSTRAINT ck_sesi_kasir_uang_fisik CHECK (((uang_fisik IS NULL) OR (uang_fisik >= 0)))
);

--
-- Name: TABLE sesi_kasir; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.sesi_kasir IS 'Sesi kasir per titik per hari. Total bersih diposting ke Buku Kas saat tutup (PRD 6.4, 5.1).';

--
-- Name: setoran; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.setoran (
    id bigint NOT NULL,
    sekolah_id bigint NOT NULL,
    tanggal date NOT NULL,
    petugas_id bigint NOT NULL,
    total_topup bigint NOT NULL,
    jumlah_disetor bigint NOT NULL,
    selisih bigint GENERATED ALWAYS AS ((total_topup - jumlah_disetor)) STORED,
    referensi_id character varying(60) NOT NULL,
    catatan character varying(500),
    dikonfirmasi_oleh bigint NOT NULL,
    waktu timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    status character varying(20) DEFAULT 'DIKONFIRMASI'::character varying NOT NULL,
    CONSTRAINT ck_setoran_status CHECK (((status)::text = ANY ((ARRAY['MENUNGGU'::character varying, 'DIKONFIRMASI'::character varying, 'DITOLAK'::character varying])::text[]))),
    CONSTRAINT ck_setoran_total_tidak_negatif CHECK (((total_topup >= 0) AND (jumlah_disetor >= 0)))
);

--
-- Name: TABLE setoran; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.setoran IS 'Setoran kas TU harian (PRD 9.2, issue #39). Append-only; selisih kas dicatat, tidak dihapus.';

--
-- Name: COLUMN setoran.total_topup; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.setoran.total_topup IS 'Rekap top-up tunai petugas pada tanggal tsb, dihitung dari saldo_ledger.';

--
-- Name: COLUMN setoran.selisih; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.setoran.selisih IS 'total_topup - jumlah_disetor (GENERATED). Positif = kurang setor, negatif = lebih setor.';

--
-- Name: COLUMN setoran.referensi_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.setoran.referensi_id IS 'Nomor berita acara setoran; UNIQUE per sekolah untuk idempotency.';

--
-- Name: siswa; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.siswa (
    id bigint NOT NULL,
    sekolah_id bigint NOT NULL,
    nis character varying(30) NOT NULL,
    nisn character varying(30),
    nama character varying(100) NOT NULL,
    kelas character varying(30),
    foto_url character varying(255),
    aktif boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: siswa_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.siswa ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.siswa_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: stok; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stok (
    menu_id bigint NOT NULL,
    sekolah_id bigint NOT NULL,
    stok integer DEFAULT 0 NOT NULL,
    hpp bigint DEFAULT 0 NOT NULL,
    stok_minimum integer DEFAULT 0 NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_stok_tidak_minus CHECK ((stok >= 0))
);

--
-- Name: TABLE stok; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.stok IS 'Cache stok & HPP berjalan per menu. Selalu dapat dihitung ulang dari mutasi_stok. Baris dikunci saat penjualan.';

--
-- Name: COLUMN stok.stok_minimum; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.stok.stok_minimum IS 'Salinan ambang dari menu.stok_minimum, dijaga trigger trg_menu_sinkron_stok_minimum (issue #196). Sumber kebenaran tetap menu.stok_minimum.';

--
-- Name: titik_kasir; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.titik_kasir (
    id bigint NOT NULL,
    sekolah_id bigint NOT NULL,
    nama character varying(100) NOT NULL,
    kode character varying(30),
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: TABLE titik_kasir; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.titik_kasir IS 'Perangkat kasir. Satu kantin bisa punya lebih dari satu titik (PRD 6.6).';

--
-- Name: transaksi; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.transaksi (
    id bigint NOT NULL,
    idempotency_key character varying(64) NOT NULL,
    sekolah_id bigint NOT NULL,
    sesi_kasir_id bigint NOT NULL,
    titik_kasir_id bigint NOT NULL,
    kartu_uid character varying(64),
    petugas_id bigint NOT NULL,
    total bigint NOT NULL,
    total_hpp bigint DEFAULT 0 NOT NULL,
    status character varying(10) DEFAULT 'SUKSES'::character varying NOT NULL,
    alasan_void character varying(255),
    void_at timestamp with time zone,
    void_oleh bigint,
    waktu timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    siswa_id bigint NOT NULL,
    metode_bayar character varying(20) DEFAULT 'SALDO'::character varying NOT NULL,
    CONSTRAINT ck_transaksi_metode_bayar CHECK (((metode_bayar)::text = ANY ((ARRAY['SALDO'::character varying, 'TUNAI'::character varying])::text[]))),
    CONSTRAINT ck_transaksi_status CHECK (((status)::text = ANY (ARRAY[('SUKSES'::character varying)::text, ('VOID'::character varying)::text]))),
    CONSTRAINT ck_transaksi_total_tidak_negatif CHECK (((total >= 0) AND (total_hpp >= 0))),
    CONSTRAINT ck_transaksi_void_beralasan CHECK ((((status)::text <> 'VOID'::text) OR ((alasan_void IS NOT NULL) AND (void_at IS NOT NULL))))
);

--
-- Name: TABLE transaksi; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.transaksi IS 'Transaksi kasir. idempotency_key UNIQUE mencegah tap ganda memotong saldo dua kali (PRD 11.3).';

--
-- Name: COLUMN transaksi.total_hpp; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.transaksi.total_hpp IS 'Total HPP snapshot item saat transaksi — dipakai menghitung laba kotor (PRD 5).';

--
-- Name: transaksi_item; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.transaksi_item (
    id bigint NOT NULL,
    transaksi_id bigint NOT NULL,
    menu_id bigint NOT NULL,
    nama_menu character varying(150) NOT NULL,
    kategori_id bigint,
    harga_jual bigint NOT NULL,
    qty integer NOT NULL,
    hpp_snapshot bigint NOT NULL,
    subtotal bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_transaksi_item_harga_tidak_negatif CHECK (((harga_jual >= 0) AND (hpp_snapshot >= 0) AND (subtotal >= 0))),
    CONSTRAINT ck_transaksi_item_qty_positif CHECK ((qty > 0))
);

--
-- Name: TABLE transaksi_item; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.transaksi_item IS 'Rincian item transaksi dengan snapshot harga/HPP agar laba lama tidak berubah (PRD 6.2, 7.4).';

--
-- Name: transaksi_tertunda; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.transaksi_tertunda (
    id bigint NOT NULL,
    sekolah_id bigint NOT NULL,
    idempotency_key character varying(64) NOT NULL,
    rfid_uid character varying(64) NOT NULL,
    titik_kasir_id bigint NOT NULL,
    petugas_id bigint NOT NULL,
    total bigint DEFAULT 0 NOT NULL,
    items_json text NOT NULL,
    status character varying(15) DEFAULT 'MENUNGGU'::character varying NOT NULL,
    transaksi_id bigint,
    dibuat_at timestamp with time zone DEFAULT now() NOT NULL,
    kedaluwarsa_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    siswa_id bigint NOT NULL,
    CONSTRAINT ck_pending_status CHECK (((status)::text = ANY (ARRAY[('MENUNGGU'::character varying)::text, ('DIKONFIRMASI'::character varying)::text, ('DIBATALKAN'::character varying)::text])))
);

--
-- Name: TABLE transaksi_tertunda; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.transaksi_tertunda IS 'Tap yang menunggu konfirmasi manual petugas (PRD 6.1/9.1). Bukan ledger.';

--
-- Name: COLUMN transaksi_tertunda.items_json; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.transaksi_tertunda.items_json IS 'Snapshot item {menuId,qty} untuk divalidasi ulang saat konfirmasi.';

--
-- Name: COLUMN transaksi_tertunda.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.transaksi_tertunda.status IS 'MENUNGGU | DIKONFIRMASI (sudah jadi transaksi) | DIBATALKAN.';

--
-- Name: akun_buku_kas akun_buku_kas_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.akun_buku_kas
    ADD CONSTRAINT akun_buku_kas_pkey PRIMARY KEY (id);

--
-- Name: catatan_audit catatan_audit_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.catatan_audit
    ADD CONSTRAINT catatan_audit_pkey PRIMARY KEY (id);

--
-- Name: jurnal_buku_kas jurnal_buku_kas_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.jurnal_buku_kas
    ADD CONSTRAINT jurnal_buku_kas_pkey PRIMARY KEY (id);

--
-- Name: kartu kartu_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kartu
    ADD CONSTRAINT kartu_pkey PRIMARY KEY (uid);

--
-- Name: kategori_menu kategori_menu_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kategori_menu
    ADD CONSTRAINT kategori_menu_pkey PRIMARY KEY (id);

--
-- Name: kejadian_offline kejadian_offline_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kejadian_offline
    ADD CONSTRAINT kejadian_offline_pkey PRIMARY KEY (id);

--
-- Name: menu menu_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.menu
    ADD CONSTRAINT menu_pkey PRIMARY KEY (id);

--
-- Name: mutasi_saldo mutasi_saldo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mutasi_saldo
    ADD CONSTRAINT mutasi_saldo_pkey PRIMARY KEY (id);

--
-- Name: orang_tua orang_tua_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orang_tua
    ADD CONSTRAINT orang_tua_pkey PRIMARY KEY (id);

--
-- Name: pembatasan pembatasan_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pembatasan
    ADD CONSTRAINT pembatasan_pkey PRIMARY KEY (id);

--
-- Name: pengaturan_kantin pengaturan_kantin_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pengaturan_kantin
    ADD CONSTRAINT pengaturan_kantin_pkey PRIMARY KEY (sekolah_id);

--
-- Name: pengguna pengguna_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pengguna
    ADD CONSTRAINT pengguna_pkey PRIMARY KEY (id);

--
-- Name: saldo pk_saldo; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.saldo
    ADD CONSTRAINT pk_saldo PRIMARY KEY (sekolah_id, siswa_id);

--
-- Name: riwayat_stok riwayat_stok_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.riwayat_stok
    ADD CONSTRAINT riwayat_stok_pkey PRIMARY KEY (id);

--
-- Name: sekolah sekolah_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sekolah
    ADD CONSTRAINT sekolah_pkey PRIMARY KEY (id);

--
-- Name: sesi_kasir sesi_kasir_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sesi_kasir
    ADD CONSTRAINT sesi_kasir_pkey PRIMARY KEY (id);

--
-- Name: setoran setoran_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.setoran
    ADD CONSTRAINT setoran_pkey PRIMARY KEY (id);

--
-- Name: siswa siswa_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.siswa
    ADD CONSTRAINT siswa_pkey PRIMARY KEY (id);

--
-- Name: stok stok_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stok
    ADD CONSTRAINT stok_pkey PRIMARY KEY (menu_id);

--
-- Name: titik_kasir titik_kasir_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.titik_kasir
    ADD CONSTRAINT titik_kasir_pkey PRIMARY KEY (id);

--
-- Name: transaksi_item transaksi_item_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaksi_item
    ADD CONSTRAINT transaksi_item_pkey PRIMARY KEY (id);

--
-- Name: transaksi transaksi_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaksi
    ADD CONSTRAINT transaksi_pkey PRIMARY KEY (id);

--
-- Name: transaksi_tertunda transaksi_tertunda_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaksi_tertunda
    ADD CONSTRAINT transaksi_tertunda_pkey PRIMARY KEY (id);

--
-- Name: transaksi_tertunda uk_pending_sekolah_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaksi_tertunda
    ADD CONSTRAINT uk_pending_sekolah_key UNIQUE (sekolah_id, idempotency_key);

--
-- Name: akun_buku_kas uq_akun_buku_kas_sekolah_nama; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.akun_buku_kas
    ADD CONSTRAINT uq_akun_buku_kas_sekolah_nama UNIQUE (sekolah_id, nama);

--
-- Name: kartu uq_kartu_sekolah_nomor; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kartu
    ADD CONSTRAINT uq_kartu_sekolah_nomor UNIQUE (sekolah_id, nomor_kartu);

--
-- Name: kategori_menu uq_kategori_menu_sekolah_nama; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kategori_menu
    ADD CONSTRAINT uq_kategori_menu_sekolah_nama UNIQUE (sekolah_id, nama);

--
-- Name: kategori_menu uq_kategori_menu_sekolah_urutan; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kategori_menu
    ADD CONSTRAINT uq_kategori_menu_sekolah_urutan UNIQUE (sekolah_id, urutan);

--
-- Name: CONSTRAINT uq_kategori_menu_sekolah_urutan ON kategori_menu; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON CONSTRAINT uq_kategori_menu_sekolah_urutan ON public.kategori_menu IS 'Satu nomor urut hanya boleh dipakai satu kategori per sekolah (review 2026-11-09).';

--
-- Name: pengguna uq_pengguna_sekolah_username; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pengguna
    ADD CONSTRAINT uq_pengguna_sekolah_username UNIQUE (sekolah_id, username);

--
-- Name: sesi_kasir uq_sesi_kasir_titik_tanggal; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sesi_kasir
    ADD CONSTRAINT uq_sesi_kasir_titik_tanggal UNIQUE (titik_kasir_id, tanggal);

--
-- Name: setoran uq_setoran_sekolah_referensi; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.setoran
    ADD CONSTRAINT uq_setoran_sekolah_referensi UNIQUE (sekolah_id, referensi_id);

--
-- Name: siswa uq_siswa_sekolah_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.siswa
    ADD CONSTRAINT uq_siswa_sekolah_id UNIQUE (sekolah_id, id);

--
-- Name: siswa uq_siswa_sekolah_nis; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.siswa
    ADD CONSTRAINT uq_siswa_sekolah_nis UNIQUE (sekolah_id, nis);

--
-- Name: titik_kasir uq_titik_kasir_kode; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.titik_kasir
    ADD CONSTRAINT uq_titik_kasir_kode UNIQUE (sekolah_id, kode);

--
-- Name: transaksi uq_transaksi_idempotency_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaksi
    ADD CONSTRAINT uq_transaksi_idempotency_key UNIQUE (idempotency_key);

--
-- Name: idx_akun_buku_kas_sekolah; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_akun_buku_kas_sekolah ON public.akun_buku_kas USING btree (sekolah_id, aktif);

--
-- Name: idx_catatan_audit_aksi; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_catatan_audit_aksi ON public.catatan_audit USING btree (sekolah_id, aksi, waktu DESC);

--
-- Name: idx_catatan_audit_entitas; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_catatan_audit_entitas ON public.catatan_audit USING btree (sekolah_id, entitas, entitas_id);

--
-- Name: idx_catatan_audit_sekolah_waktu; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_catatan_audit_sekolah_waktu ON public.catatan_audit USING btree (sekolah_id, waktu DESC);

--
-- Name: idx_jurnal_buku_kas_mutasi; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_jurnal_buku_kas_mutasi ON public.jurnal_buku_kas USING btree (sekolah_id, mutasi_id);

--
-- Name: idx_jurnal_buku_kas_waktu; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_jurnal_buku_kas_waktu ON public.jurnal_buku_kas USING btree (sekolah_id, waktu DESC);

--
-- Name: idx_kategori_menu_sekolah; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_kategori_menu_sekolah ON public.kategori_menu USING btree (sekolah_id, is_active, urutan);

--
-- Name: idx_kejadian_offline_sekolah; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_kejadian_offline_sekolah ON public.kejadian_offline USING btree (sekolah_id, mulai DESC);

--
-- Name: idx_menu_kategori; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_menu_kategori ON public.menu USING btree (kategori_id);

--
-- Name: idx_menu_sekolah_aktif; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_menu_sekolah_aktif ON public.menu USING btree (sekolah_id, is_active);

--
-- Name: idx_menu_sekolah_aktif_tersedia; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_menu_sekolah_aktif_tersedia ON public.menu USING btree (sekolah_id, is_active) WHERE (deleted_at IS NULL);

--
-- Name: idx_mutasi_saldo_siswa; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mutasi_saldo_siswa ON public.mutasi_saldo USING btree (sekolah_id, siswa_id, waktu DESC);

--
-- Name: idx_mutasi_saldo_transaksi; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mutasi_saldo_transaksi ON public.mutasi_saldo USING btree (transaksi_id) WHERE (transaksi_id IS NOT NULL);

--
-- Name: idx_mutasi_saldo_waktu; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mutasi_saldo_waktu ON public.mutasi_saldo USING btree (sekolah_id, waktu);

--
-- Name: idx_pending_sekolah_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pending_sekolah_status ON public.transaksi_tertunda USING btree (sekolah_id, status);

--
-- Name: idx_riwayat_stok_asal; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_riwayat_stok_asal ON public.riwayat_stok USING btree (sekolah_id, mutasi_asal_id) WHERE (mutasi_asal_id IS NOT NULL);

--
-- Name: idx_riwayat_stok_menu; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_riwayat_stok_menu ON public.riwayat_stok USING btree (sekolah_id, menu_id, id);

--
-- Name: idx_riwayat_stok_transaksi; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_riwayat_stok_transaksi ON public.riwayat_stok USING btree (transaksi_id) WHERE (transaksi_id IS NOT NULL);

--
-- Name: idx_riwayat_stok_waktu; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_riwayat_stok_waktu ON public.riwayat_stok USING btree (sekolah_id, waktu);

--
-- Name: idx_sesi_kasir_sekolah_tanggal; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sesi_kasir_sekolah_tanggal ON public.sesi_kasir USING btree (sekolah_id, tanggal);

--
-- Name: idx_setoran_sekolah_petugas; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_setoran_sekolah_petugas ON public.setoran USING btree (sekolah_id, petugas_id, tanggal DESC);

--
-- Name: idx_setoran_sekolah_tanggal; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_setoran_sekolah_tanggal ON public.setoran USING btree (sekolah_id, tanggal DESC);

--
-- Name: idx_stok_sekolah; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stok_sekolah ON public.stok USING btree (sekolah_id);

--
-- Name: idx_titik_kasir_sekolah; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_titik_kasir_sekolah ON public.titik_kasir USING btree (sekolah_id);

--
-- Name: idx_transaksi_item_menu; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_transaksi_item_menu ON public.transaksi_item USING btree (menu_id);

--
-- Name: idx_transaksi_item_transaksi; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_transaksi_item_transaksi ON public.transaksi_item USING btree (transaksi_id);

--
-- Name: idx_transaksi_sekolah_waktu; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_transaksi_sekolah_waktu ON public.transaksi USING btree (sekolah_id, waktu);

--
-- Name: idx_transaksi_sesi; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_transaksi_sesi ON public.transaksi USING btree (sesi_kasir_id, status);

--
-- Name: idx_transaksi_siswa; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_transaksi_siswa ON public.transaksi USING btree (sekolah_id, siswa_id, waktu DESC);

--
-- Name: uq_jurnal_buku_kas_sekolah_referensi; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_jurnal_buku_kas_sekolah_referensi ON public.jurnal_buku_kas USING btree (sekolah_id, referensi_id);

--
-- Name: uq_kartu_siswa_aktif; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_kartu_siswa_aktif ON public.kartu USING btree (sekolah_id, siswa_id) WHERE aktif;

--
-- Name: uq_menu_sekolah_kode_barcode; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_menu_sekolah_kode_barcode ON public.menu USING btree (sekolah_id, kode_barcode) WHERE ((kode_barcode IS NOT NULL) AND (deleted_at IS NULL));

--
-- Name: uq_mutasi_saldo_sekolah_idempotency; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_mutasi_saldo_sekolah_idempotency ON public.mutasi_saldo USING btree (sekolah_id, idempotency_key) WHERE (idempotency_key IS NOT NULL);

--
-- Name: INDEX uq_mutasi_saldo_sekolah_idempotency; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON INDEX public.uq_mutasi_saldo_sekolah_idempotency IS 'Idempotency saldo per tenant: satu key per sekolah. Retry tidak memotong dua kali; nomor bukti boleh sama antar sekolah (PRD 11.3, 11.4).';

--
-- Name: uq_pembatasan_subjek_jenis; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_pembatasan_subjek_jenis ON public.pembatasan USING btree (sekolah_id, siswa_id, jenis, COALESCE(menu_id, (0)::bigint), COALESCE(kategori_id, (0)::bigint));

--
-- Name: uq_riwayat_stok_barang_masuk_referensi; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_riwayat_stok_barang_masuk_referensi ON public.riwayat_stok USING btree (sekolah_id, referensi_id, menu_id) WHERE (((jenis)::text = 'BARANG_MASUK'::text) AND (referensi_id IS NOT NULL));

--
-- Name: INDEX uq_riwayat_stok_barang_masuk_referensi; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON INDEX public.uq_riwayat_stok_barang_masuk_referensi IS 'Idempotency barang masuk: satu nomor bukti per (sekolah, menu). Retry tidak menggandakan stok (PRD 7.2, 11.3).';

--
-- Name: uq_riwayat_stok_opname_batch_referensi; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_riwayat_stok_opname_batch_referensi ON public.riwayat_stok USING btree (sekolah_id, referensi_id, menu_id) WHERE (((referensi_tipe)::text = 'OPNAME_BATCH'::text) AND (referensi_id IS NOT NULL));

--
-- Name: INDEX uq_riwayat_stok_opname_batch_referensi; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON INDEX public.uq_riwayat_stok_opname_batch_referensi IS 'Idempotency batch opname: satu nomor berita acara per (sekolah, menu). Retry tidak menerapkan penyesuaian dua kali (PRD 7.3, 11.3).';

--
-- Name: uq_riwayat_stok_pembalik_referensi; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_riwayat_stok_pembalik_referensi ON public.riwayat_stok USING btree (sekolah_id, referensi_id) WHERE (((referensi_tipe)::text = 'BARANG_MASUK_PEMBALIK'::text) AND (referensi_id IS NOT NULL));

--
-- Name: uq_sesi_kasir_referensi_buku_kas; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_sesi_kasir_referensi_buku_kas ON public.sesi_kasir USING btree (referensi_buku_kas) WHERE (referensi_buku_kas IS NOT NULL);

--
-- Name: catatan_audit trg_catatan_audit_append_only; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_catatan_audit_append_only BEFORE DELETE OR UPDATE ON public.catatan_audit FOR EACH ROW EXECUTE FUNCTION public.tolak_perubahan_ledger();

--
-- Name: kejadian_offline trg_kejadian_offline_append_only; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_kejadian_offline_append_only BEFORE DELETE OR UPDATE ON public.kejadian_offline FOR EACH ROW EXECUTE FUNCTION public.tolak_perubahan_ledger();

--
-- Name: menu trg_menu_sinkron_stok_minimum; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_menu_sinkron_stok_minimum AFTER INSERT OR UPDATE OF stok_minimum ON public.menu FOR EACH ROW EXECUTE FUNCTION public.sinkron_stok_minimum();

--
-- Name: mutasi_saldo trg_mutasi_saldo_append_only; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_mutasi_saldo_append_only BEFORE DELETE OR UPDATE ON public.mutasi_saldo FOR EACH ROW EXECUTE FUNCTION public.tolak_perubahan_ledger();

--
-- Name: mutasi_saldo trg_mutasi_saldo_sinkron; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_mutasi_saldo_sinkron AFTER INSERT ON public.mutasi_saldo FOR EACH ROW EXECUTE FUNCTION public.sinkron_saldo_dari_ledger();

--
-- Name: riwayat_stok trg_riwayat_stok_append_only; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_riwayat_stok_append_only BEFORE DELETE OR UPDATE ON public.riwayat_stok FOR EACH ROW EXECUTE FUNCTION public.tolak_perubahan_ledger();

--
-- Name: riwayat_stok trg_riwayat_stok_sinkron; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_riwayat_stok_sinkron AFTER INSERT ON public.riwayat_stok FOR EACH ROW EXECUTE FUNCTION public.sinkron_stok_dari_mutasi();

--
-- Name: setoran trg_setoran_append_only; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_setoran_append_only BEFORE DELETE OR UPDATE ON public.setoran FOR EACH ROW EXECUTE FUNCTION public.tolak_perubahan_ledger();

--
-- Name: akun_buku_kas fk_akun_buku_kas_sekolah; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.akun_buku_kas
    ADD CONSTRAINT fk_akun_buku_kas_sekolah FOREIGN KEY (sekolah_id) REFERENCES public.sekolah(id);

--
-- Name: catatan_audit fk_catatan_audit_sekolah; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.catatan_audit
    ADD CONSTRAINT fk_catatan_audit_sekolah FOREIGN KEY (sekolah_id) REFERENCES public.sekolah(id);

--
-- Name: jurnal_buku_kas fk_jurnal_buku_kas_mutasi; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.jurnal_buku_kas
    ADD CONSTRAINT fk_jurnal_buku_kas_mutasi FOREIGN KEY (mutasi_id) REFERENCES public.mutasi_saldo(id);

--
-- Name: jurnal_buku_kas fk_jurnal_buku_kas_sekolah; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.jurnal_buku_kas
    ADD CONSTRAINT fk_jurnal_buku_kas_sekolah FOREIGN KEY (sekolah_id) REFERENCES public.sekolah(id);

--
-- Name: kartu fk_kartu_sekolah; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kartu
    ADD CONSTRAINT fk_kartu_sekolah FOREIGN KEY (sekolah_id) REFERENCES public.sekolah(id);

--
-- Name: kartu fk_kartu_siswa; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kartu
    ADD CONSTRAINT fk_kartu_siswa FOREIGN KEY (sekolah_id, siswa_id) REFERENCES public.siswa(sekolah_id, id) ON DELETE CASCADE;

--
-- Name: kategori_menu fk_kategori_menu_sekolah; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kategori_menu
    ADD CONSTRAINT fk_kategori_menu_sekolah FOREIGN KEY (sekolah_id) REFERENCES public.sekolah(id);

--
-- Name: kejadian_offline fk_kejadian_offline_sekolah; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kejadian_offline
    ADD CONSTRAINT fk_kejadian_offline_sekolah FOREIGN KEY (sekolah_id) REFERENCES public.sekolah(id);

--
-- Name: menu fk_menu_kategori; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.menu
    ADD CONSTRAINT fk_menu_kategori FOREIGN KEY (kategori_id) REFERENCES public.kategori_menu(id) ON DELETE RESTRICT;

--
-- Name: menu fk_menu_sekolah; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.menu
    ADD CONSTRAINT fk_menu_sekolah FOREIGN KEY (sekolah_id) REFERENCES public.sekolah(id);

--
-- Name: mutasi_saldo fk_mutasi_saldo_aktor; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mutasi_saldo
    ADD CONSTRAINT fk_mutasi_saldo_aktor FOREIGN KEY (aktor_id) REFERENCES public.pengguna(id);

--
-- Name: mutasi_saldo fk_mutasi_saldo_sekolah; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mutasi_saldo
    ADD CONSTRAINT fk_mutasi_saldo_sekolah FOREIGN KEY (sekolah_id) REFERENCES public.sekolah(id);

--
-- Name: mutasi_saldo fk_mutasi_saldo_siswa; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mutasi_saldo
    ADD CONSTRAINT fk_mutasi_saldo_siswa FOREIGN KEY (sekolah_id, siswa_id) REFERENCES public.siswa(sekolah_id, id) ON DELETE CASCADE;

--
-- Name: mutasi_saldo fk_mutasi_saldo_transaksi; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mutasi_saldo
    ADD CONSTRAINT fk_mutasi_saldo_transaksi FOREIGN KEY (transaksi_id) REFERENCES public.transaksi(id) ON DELETE SET NULL;

--
-- Name: orang_tua fk_orang_tua_sekolah; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orang_tua
    ADD CONSTRAINT fk_orang_tua_sekolah FOREIGN KEY (sekolah_id) REFERENCES public.sekolah(id);

--
-- Name: orang_tua fk_orang_tua_siswa; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orang_tua
    ADD CONSTRAINT fk_orang_tua_siswa FOREIGN KEY (sekolah_id, siswa_id) REFERENCES public.siswa(sekolah_id, id) ON DELETE CASCADE;

--
-- Name: pembatasan fk_pembatasan_diubah_oleh; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pembatasan
    ADD CONSTRAINT fk_pembatasan_diubah_oleh FOREIGN KEY (diubah_oleh) REFERENCES public.pengguna(id);

--
-- Name: pembatasan fk_pembatasan_kategori; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pembatasan
    ADD CONSTRAINT fk_pembatasan_kategori FOREIGN KEY (kategori_id) REFERENCES public.kategori_menu(id);

--
-- Name: pembatasan fk_pembatasan_menu; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pembatasan
    ADD CONSTRAINT fk_pembatasan_menu FOREIGN KEY (menu_id) REFERENCES public.menu(id);

--
-- Name: pembatasan fk_pembatasan_sekolah; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pembatasan
    ADD CONSTRAINT fk_pembatasan_sekolah FOREIGN KEY (sekolah_id) REFERENCES public.sekolah(id);

--
-- Name: pembatasan fk_pembatasan_siswa; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pembatasan
    ADD CONSTRAINT fk_pembatasan_siswa FOREIGN KEY (sekolah_id, siswa_id) REFERENCES public.siswa(sekolah_id, id) ON DELETE CASCADE;

--
-- Name: pengaturan_kantin fk_pengaturan_kantin_sekolah; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pengaturan_kantin
    ADD CONSTRAINT fk_pengaturan_kantin_sekolah FOREIGN KEY (sekolah_id) REFERENCES public.sekolah(id);

--
-- Name: pengguna fk_pengguna_sekolah; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pengguna
    ADD CONSTRAINT fk_pengguna_sekolah FOREIGN KEY (sekolah_id) REFERENCES public.sekolah(id);

--
-- Name: riwayat_stok fk_riwayat_stok_aktor; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.riwayat_stok
    ADD CONSTRAINT fk_riwayat_stok_aktor FOREIGN KEY (aktor_id) REFERENCES public.pengguna(id);

--
-- Name: riwayat_stok fk_riwayat_stok_menu; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.riwayat_stok
    ADD CONSTRAINT fk_riwayat_stok_menu FOREIGN KEY (menu_id) REFERENCES public.menu(id);

--
-- Name: riwayat_stok fk_riwayat_stok_sekolah; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.riwayat_stok
    ADD CONSTRAINT fk_riwayat_stok_sekolah FOREIGN KEY (sekolah_id) REFERENCES public.sekolah(id);

--
-- Name: riwayat_stok fk_riwayat_stok_transaksi; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.riwayat_stok
    ADD CONSTRAINT fk_riwayat_stok_transaksi FOREIGN KEY (transaksi_id) REFERENCES public.transaksi(id) ON DELETE SET NULL;

--
-- Name: saldo fk_saldo_sekolah; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.saldo
    ADD CONSTRAINT fk_saldo_sekolah FOREIGN KEY (sekolah_id) REFERENCES public.sekolah(id);

--
-- Name: saldo fk_saldo_siswa; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.saldo
    ADD CONSTRAINT fk_saldo_siswa FOREIGN KEY (sekolah_id, siswa_id) REFERENCES public.siswa(sekolah_id, id) ON DELETE CASCADE;

--
-- Name: sesi_kasir fk_sesi_kasir_ditutup_oleh; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sesi_kasir
    ADD CONSTRAINT fk_sesi_kasir_ditutup_oleh FOREIGN KEY (ditutup_oleh) REFERENCES public.pengguna(id);

--
-- Name: sesi_kasir fk_sesi_kasir_sekolah; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sesi_kasir
    ADD CONSTRAINT fk_sesi_kasir_sekolah FOREIGN KEY (sekolah_id) REFERENCES public.sekolah(id);

--
-- Name: sesi_kasir fk_sesi_kasir_titik; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sesi_kasir
    ADD CONSTRAINT fk_sesi_kasir_titik FOREIGN KEY (titik_kasir_id) REFERENCES public.titik_kasir(id) ON DELETE RESTRICT;

--
-- Name: setoran fk_setoran_dikonfirmasi_oleh; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.setoran
    ADD CONSTRAINT fk_setoran_dikonfirmasi_oleh FOREIGN KEY (dikonfirmasi_oleh) REFERENCES public.pengguna(id);

--
-- Name: setoran fk_setoran_petugas; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.setoran
    ADD CONSTRAINT fk_setoran_petugas FOREIGN KEY (petugas_id) REFERENCES public.pengguna(id);

--
-- Name: setoran fk_setoran_sekolah; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.setoran
    ADD CONSTRAINT fk_setoran_sekolah FOREIGN KEY (sekolah_id) REFERENCES public.sekolah(id);

--
-- Name: siswa fk_siswa_sekolah; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.siswa
    ADD CONSTRAINT fk_siswa_sekolah FOREIGN KEY (sekolah_id) REFERENCES public.sekolah(id);

--
-- Name: stok fk_stok_menu; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stok
    ADD CONSTRAINT fk_stok_menu FOREIGN KEY (menu_id) REFERENCES public.menu(id) ON DELETE CASCADE;

--
-- Name: stok fk_stok_sekolah; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stok
    ADD CONSTRAINT fk_stok_sekolah FOREIGN KEY (sekolah_id) REFERENCES public.sekolah(id);

--
-- Name: titik_kasir fk_titik_kasir_sekolah; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.titik_kasir
    ADD CONSTRAINT fk_titik_kasir_sekolah FOREIGN KEY (sekolah_id) REFERENCES public.sekolah(id);

--
-- Name: transaksi_item fk_transaksi_item_kategori; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaksi_item
    ADD CONSTRAINT fk_transaksi_item_kategori FOREIGN KEY (kategori_id) REFERENCES public.kategori_menu(id);

--
-- Name: transaksi_item fk_transaksi_item_menu; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaksi_item
    ADD CONSTRAINT fk_transaksi_item_menu FOREIGN KEY (menu_id) REFERENCES public.menu(id);

--
-- Name: transaksi_item fk_transaksi_item_transaksi; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaksi_item
    ADD CONSTRAINT fk_transaksi_item_transaksi FOREIGN KEY (transaksi_id) REFERENCES public.transaksi(id) ON DELETE CASCADE;

--
-- Name: transaksi fk_transaksi_kartu; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaksi
    ADD CONSTRAINT fk_transaksi_kartu FOREIGN KEY (kartu_uid) REFERENCES public.kartu(uid);

--
-- Name: transaksi fk_transaksi_petugas; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaksi
    ADD CONSTRAINT fk_transaksi_petugas FOREIGN KEY (petugas_id) REFERENCES public.pengguna(id);

--
-- Name: transaksi fk_transaksi_sekolah; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaksi
    ADD CONSTRAINT fk_transaksi_sekolah FOREIGN KEY (sekolah_id) REFERENCES public.sekolah(id);

--
-- Name: transaksi fk_transaksi_sesi_kasir; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaksi
    ADD CONSTRAINT fk_transaksi_sesi_kasir FOREIGN KEY (sesi_kasir_id) REFERENCES public.sesi_kasir(id) ON DELETE RESTRICT;

--
-- Name: transaksi fk_transaksi_siswa; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaksi
    ADD CONSTRAINT fk_transaksi_siswa FOREIGN KEY (sekolah_id, siswa_id) REFERENCES public.siswa(sekolah_id, id);

--
-- Name: transaksi_tertunda fk_transaksi_tertunda_kartu; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaksi_tertunda
    ADD CONSTRAINT fk_transaksi_tertunda_kartu FOREIGN KEY (rfid_uid) REFERENCES public.kartu(uid);

--
-- Name: transaksi_tertunda fk_transaksi_tertunda_petugas; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaksi_tertunda
    ADD CONSTRAINT fk_transaksi_tertunda_petugas FOREIGN KEY (petugas_id) REFERENCES public.pengguna(id);

--
-- Name: transaksi_tertunda fk_transaksi_tertunda_siswa; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaksi_tertunda
    ADD CONSTRAINT fk_transaksi_tertunda_siswa FOREIGN KEY (sekolah_id, siswa_id) REFERENCES public.siswa(sekolah_id, id);

--
-- Name: transaksi_tertunda fk_transaksi_tertunda_titik; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaksi_tertunda
    ADD CONSTRAINT fk_transaksi_tertunda_titik FOREIGN KEY (titik_kasir_id) REFERENCES public.titik_kasir(id);

--
-- Name: transaksi_tertunda fk_transaksi_tertunda_transaksi; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaksi_tertunda
    ADD CONSTRAINT fk_transaksi_tertunda_transaksi FOREIGN KEY (transaksi_id) REFERENCES public.transaksi(id);

--
-- Name: transaksi fk_transaksi_titik_kasir; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaksi
    ADD CONSTRAINT fk_transaksi_titik_kasir FOREIGN KEY (titik_kasir_id) REFERENCES public.titik_kasir(id) ON DELETE RESTRICT;

--
-- Name: transaksi fk_transaksi_void_oleh; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaksi
    ADD CONSTRAINT fk_transaksi_void_oleh FOREIGN KEY (void_oleh) REFERENCES public.pengguna(id);
