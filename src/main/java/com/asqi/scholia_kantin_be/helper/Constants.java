package com.asqi.scholia_kantin_be.helper;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import java.util.HashMap;
import java.util.Map;
/**
 * Konstanta &amp; util bersama.
 *
 * <p>Generator ID entitas tidak lagi ada di sini — seluruh entitas ledger dan
 * transaksi memakai bean {@code IdGenerator} yang monoton dan anti-tabrakan
 * (lihat {@code docs/spesifikasi-fase4-ledger.md §5.5}).
 */
public final class Constants {
    private Constants() {
    }
    /** Konversi `PascalCase` → `Sentence case` (parity admin-be). */
    public static String pascalToSentenceCase(String pascalCaseString) {
        String result = pascalCaseString.replaceAll("([a-z])([A-Z])", "$1 $2");
        return result.substring(0, 1).toUpperCase() + result.substring(1);
    }
    /** Ubah {@link Errors} validasi menjadi map field → pesan. */
    public static Map<String, String> validateErrorMessage(Errors errors) {
        Map<String, String> messages = new HashMap<>();
        for (FieldError fieldError : errors.getFieldErrors()) {
            messages.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return messages;
    }
    /** Kategori Buku Kas untuk posting kantin (INTEGRATIONS.md §3.3). */
    public static final String KATEGORI_PENDAPATAN_KANTIN = "Pendapatan Kantin";
    public static final String KATEGORI_BELANJA_STOK_KANTIN = "Belanja Stok Kantin";
    public static final String KATEGORI_PENYESUAIAN_KANTIN = "Penyesuaian Kantin";
}
