package com.inventory.util;

import java.time.LocalDate;

public final class Fmt {
    private Fmt() {}
    public static String money(double v)  { return String.format("Rs.%,.2f", v); }
    public static String date(LocalDate d) { return d == null ? "-" : d.toString(); }
    public static String cut(String s, int n) { return s == null ? "" : (s.length() <= n ? s : s.substring(0, n - 1) + "~"); }
}
