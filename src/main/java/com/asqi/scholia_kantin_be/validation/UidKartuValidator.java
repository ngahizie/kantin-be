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
 * {@code @AssertTrue}. Kalau diisi, bentuknya wajib heksadesimal 4–64 digit.
 */
public class UidKartuValidator implements ConstraintValidator<UidKartuValid, String> {

    /** 4–64 karakter heksadesimal, pemisah titik dua/strip opsional. */
    private static final Pattern POLA = Pattern.compile("(?i)^[0-9a-f]{2}([.:-]?[0-9a-f]{2}){1,31}$");

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
