package com.mits.EduAssign.Service;

import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.mits.EduAssign.Entity.AdminFaculty;

public class NaturalOrderComparator implements Comparator<AdminFaculty> {
    private static final Pattern PATTERN = Pattern.compile("(\\d+)|(\\D+)");

    @Override
    public int compare(AdminFaculty f1, AdminFaculty f2) {
        if (f1 == null && f2 == null) return 0;
        if (f1 == null) return -1;
        if (f2 == null) return 1;

        String s1 = f1.getId();
        String s2 = f2.getId();
        if (s1 == null && s2 == null) return 0;
        if (s1 == null) return -1;
        if (s2 == null) return 1;

        Matcher m1 = PATTERN.matcher(s1);
        Matcher m2 = PATTERN.matcher(s2);

        while (m1.find() && m2.find()) {
            String g1 = m1.group();
            String g2 = m2.group();

            if (Character.isDigit(g1.charAt(0)) && Character.isDigit(g2.charAt(0))) {
                // Remove leading zeros for numerical comparison
                java.math.BigInteger b1 = new java.math.BigInteger(g1);
                java.math.BigInteger b2 = new java.math.BigInteger(g2);
                int cmp = b1.compareTo(b2);
                if (cmp != 0) return cmp;
            } else {
                int cmp = g1.compareToIgnoreCase(g2);
                if (cmp != 0) return cmp;
            }
        }
        return s1.length() - s2.length();
    }
}
