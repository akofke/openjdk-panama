/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package test.java.time.format;

import java.text.DateFormatSymbols;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;

import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoField;
import java.time.temporal.Queries;
import java.time.format.DateTimeFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.TextStyle;
import java.time.zone.ZoneRulesProvider;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;

/**
 * Test ZoneTextPrinterParser
 */
@Test(groups={"implementation"})
public class TestZoneTextPrinterParser extends AbstractTestPrinterParser {

    protected static DateTimeFormatter getFormatter(Locale locale, TextStyle style) {
        return new DateTimeFormatterBuilder().appendZoneText(style)
                                             .toFormatter(locale)
                                             .withSymbols(DateTimeFormatSymbols.of(locale));
    }

    public void test_printText() {
        Random r = new Random();
        int N = 50;
        Locale[] locales = Locale.getAvailableLocales();
        Set<String> zids = ZoneRulesProvider.getAvailableZoneIds();
        ZonedDateTime zdt = ZonedDateTime.now();

        //System.out.printf("locale==%d, timezone=%d%n", locales.length, zids.size());
        while (N-- > 0) {
            zdt = zdt.withDayOfYear(r.nextInt(365) + 1)
                     .with(ChronoField.SECOND_OF_DAY, r.nextInt(86400));
            for (String zid : zids) {
                if (zid.equals("ROC") ||
                    zid.startsWith("UTC") ||
                    zid.startsWith("GMT") || zid.startsWith("Etc/GMT")) {
                    // UTC, GMT are treated as zone offset
                    continue;      // TBD: match jdk behavior?
                }
                zdt = zdt.withZoneSameLocal(ZoneId.of(zid));
                TimeZone tz = TimeZone.getTimeZone(zid);
                boolean isDST = tz.inDaylightTime(new Date(zdt.toInstant().toEpochMilli()));
                for (Locale locale : locales) {
                    printText(locale, zdt, TextStyle.FULL,
                              tz.getDisplayName(isDST, TimeZone.LONG, locale));
                    printText(locale, zdt, TextStyle.SHORT,
                              tz.getDisplayName(isDST, TimeZone.SHORT, locale));
                }
            }
        }
    }

    private void printText(Locale locale, ZonedDateTime zdt, TextStyle style, String expected) {
        String result = getFormatter(locale, style).format(zdt);
        if (!result.equals(expected)) {
            if (result.equals("FooLocation")) { // from rules provider test if same vm
                return;
            }
            System.out.println("----------------");
            System.out.printf("tdz[%s]%n", zdt.toString());
            System.out.printf("[%-4s, %5s] :[%s]%n", locale.toString(), style.toString(),result);
            System.out.printf("%4s, %5s  :[%s]%n", "", "", expected);
        }
        assertEquals(result, expected);
    }

    public void test_ParseText() {
        Locale[] locales = new Locale[] { Locale.ENGLISH, Locale.JAPANESE, Locale.FRENCH };
        Set<String> zids = ZoneRulesProvider.getAvailableZoneIds();
        for (Locale locale : locales) {
            parseText(zids, locale, TextStyle.FULL, false);
            parseText(zids, locale, TextStyle.FULL, true);
            parseText(zids, locale, TextStyle.SHORT, false);
            parseText(zids, locale, TextStyle.SHORT, true);
        }
    }

    private static Set<ZoneId> preferred = new HashSet<>(Arrays.asList(new ZoneId[] {
        ZoneId.of("EST"),
        ZoneId.of("Asia/Taipei"),
        ZoneId.of("CET"),
    }));

    private static Set<ZoneId> preferred_s = new HashSet<>(Arrays.asList(new ZoneId[] {
         ZoneId.of("EST"),
         ZoneId.of("CET"),
         ZoneId.of("Australia/South"),
         ZoneId.of("Australia/West"),
         ZoneId.of("Asia/Shanghai"),
    }));

    private static Set<ZoneId> none = new HashSet<>();

    @DataProvider(name="preferredZones")
    Object[][] data_preferredZones() {
        return new Object[][] {
            {"America/New_York", "Eastern Standard Time", none,      Locale.ENGLISH, TextStyle.FULL},
            {"EST",              "Eastern Standard Time", preferred, Locale.ENGLISH, TextStyle.FULL},
            {"Europe/Paris",     "Central European Time", none,      Locale.ENGLISH, TextStyle.FULL},
            {"CET",              "Central European Time", preferred, Locale.ENGLISH, TextStyle.FULL},
            {"Asia/Shanghai",    "China Standard Time",   none,      Locale.ENGLISH, TextStyle.FULL},
            {"Asia/Taipei",      "China Standard Time",   preferred, Locale.ENGLISH, TextStyle.FULL},
            {"America/Chicago",  "CST",                   none,      Locale.ENGLISH, TextStyle.SHORT},
            {"Asia/Taipei",      "CST",                   preferred, Locale.ENGLISH, TextStyle.SHORT},
            {"Australia/South",  "CST",                   preferred_s, Locale.ENGLISH, TextStyle.SHORT},
            {"America/Chicago",  "CDT",                   none,        Locale.ENGLISH, TextStyle.SHORT},
            {"Asia/Shanghai",    "CDT",                   preferred_s, Locale.ENGLISH, TextStyle.SHORT},
       };
    }

    @Test(dataProvider="preferredZones")
    public void test_ParseText(String expected, String text, Set<ZoneId> preferred, Locale locale, TextStyle style) {
        DateTimeFormatter fmt = new DateTimeFormatterBuilder().appendZoneText(style, preferred)
                                                              .toFormatter(locale)
                                                              .withSymbols(DateTimeFormatSymbols.of(locale));

        String ret = fmt.parse(text, Queries.zone()).getId();

        System.out.printf("[%-5s %s] %24s -> %s(%s)%n",
                          locale.toString(),
                          style == TextStyle.FULL ? " full" :"short",
                          text, ret, expected);

        assertEquals(ret, expected);

    }


    private void parseText(Set<String> zids, Locale locale, TextStyle style, boolean ci) {
        System.out.println("---------------------------------------");
        DateTimeFormatter fmt = getFormatter(locale, style, ci);
        for (String[] names : new DateFormatSymbols(locale).getZoneStrings()) {
            if (!zids.contains(names[0])) {
                continue;
            }
            String zid = names[0];
            String expected = ZoneName.toZid(zid, locale);

            parse(fmt, zid, expected, zid, locale, style, ci);
            int i = style == TextStyle.FULL ? 1 : 2;
            for (; i < names.length; i += 2) {
                parse(fmt, zid, expected, names[i], locale, style, ci);
            }
        }
    }

    private void parse(DateTimeFormatter fmt,
                       String zid, String expected, String text,
                       Locale locale, TextStyle style, boolean ci) {
        if (ci) {
            text = text.toUpperCase();
        }
        String ret = fmt.parse(text, Queries.zone()).getId();
        // TBD: need an excluding list
        // assertEquals(...);
        if (ret.equals(expected) ||
            ret.equals(zid) ||
            ret.equals(ZoneName.toZid(zid)) ||
            ret.equals(expected.replace("UTC", "UCT"))) {
            return;
        }
        System.out.printf("[%-5s %s %s %16s] %24s -> %s(%s)%n",
                          locale.toString(),
                          ci ? "ci" : "  ",
                          style == TextStyle.FULL ? " full" :"short",
                          zid, text, ret, expected);
    }

    private DateTimeFormatter getFormatter(Locale locale, TextStyle style, boolean ci) {
        DateTimeFormatterBuilder db = new DateTimeFormatterBuilder();
        if (ci) {
            db = db.parseCaseInsensitive();
        }
        return db.appendZoneText(style)
                 .toFormatter(locale)
                 .withSymbols(DateTimeFormatSymbols.of(locale));
    }

}
