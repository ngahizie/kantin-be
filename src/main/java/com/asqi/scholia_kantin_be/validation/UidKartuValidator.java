package com.asqi.scholia_kantin_be.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * Implementasi {@link UidKartuValid}.
 *
 * <p>Menerima {@code null} <b>dan string kosong</b>: keberadaan UID bukan lagi
 * urusan validator format (sejak C.2a pembayaran tunai sah tanpa kartu).
 * Kewajiban UID untuk pembayaran saldo ditegakkan {@code TapRequest} lewat
 * {@code @AssertTrue}. Bila diisi, menerima UID heksadesimal (dengan/tanpa pemisah
 * {@code :}/{@code -}/{@code .}), keluaran pembaca USB desimal (panjang ganjil/genap
 * seperti 9–10 digit), maupun kode kartu alfanumerik aman (4–64 karakter).
 */
public class UidKartuValidator implements ConstraintValidator<UidKartuValid, String> {

    /** 4–64 karakter alfanumerik aman (hex, desimal reader, atau pemisah . : - _ di tengah). */
    private static final Pattern POLA = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9.:_-]{2,62}[A-Za-z0-9]$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        String v = value.trim();
        if (v.isEmpty()) {
            return true;
        }
        return POLA.matcher(v).matches();
    }
}
