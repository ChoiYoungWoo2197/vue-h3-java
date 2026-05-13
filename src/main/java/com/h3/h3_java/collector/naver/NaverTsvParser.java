package com.h3.h3_java.collector.naver;

import java.util.*;

public class NaverTsvParser {

    private static final Map<String, Map<Integer, String>> COLUMN_SETS = new HashMap<>();

    static {
        COLUMN_SETS.put("Campaign", map(
            0, "advkey", 1, "campaignid", 2, "campaignname", 3, "campaigntype",
            4, "deliverymethod", 5, "usingperiod", 6, "periostartdate", 7, "perioenddate",
            8, "regtm", 9, "deltm", 10, "onoff"
        ));
        COLUMN_SETS.put("CampaignBudget", map(
            0, "advkey", 1, "campaignid", 2, "usingdailybudget", 3, "dailybudget",
            4, "regtm", 5, "deltm"
        ));
        COLUMN_SETS.put("Adgroup", map(
            0, "advkey", 1, "gid", 2, "cid", 3, "gname", 4, "bidamount", 5, "onoff",
            14, "regtm", 15, "deltm"
        ));
        COLUMN_SETS.put("AdgroupBudget", map(
            0, "advkey", 1, "gid", 2, "usingdailybudget", 3, "dailybudget",
            4, "regtm", 5, "deltm"
        ));
        COLUMN_SETS.put("Keyword", map(
            0, "advkey", 1, "adgroupid", 2, "kwid", 3, "kword",
            7, "onoff", 10, "regtm", 11, "deltm"
        ));
        COLUMN_SETS.put("Ad", map(
            0, "advkey", 1, "groupid", 2, "adid", 3, "creativeinspect",
            4, "subject", 5, "description", 6, "plandingurl", 7, "mlandingurl",
            8, "onoff", 9, "deltm", 10, "regtm"
        ));
        COLUMN_SETS.put("RsaAd", map(
            0, "advkey", 1, "groupid", 2, "adid", 3, "creativeinspect",
            4, "plandingurl", 5, "mlandingurl", 6, "onoff", 7, "regtm", 8, "deltm"
        ));
        COLUMN_SETS.put("ContentsAd", map(
            0, "advkey", 1, "groupid", 2, "adid", 3, "creativeinspect",
            4, "subject", 5, "description", 6, "plandingurl", 7, "mlandingurl",
            8, "imgurl", 9, "companyname", 10, "cissuedate", 11, "rdate",
            12, "onoff", 13, "regtm", 14, "deltm"
        ));
        COLUMN_SETS.put("AdExtension", map(
            0, "advkey", 1, "extid", 2, "type", 3, "ownerid",
            4, "pbizchn", 5, "mbizchn", 6, "tmonday", 7, "ttuesday", 8, "twednesday",
            9, "tthursday", 10, "tfriday", 11, "tsaturday", 12, "tsunday",
            13, "onoff", 14, "inspect", 15, "regtm", 16, "deltm"
        ));
        COLUMN_SETS.put("ShoppingProduct", map(
            0, "advkey", 1, "groupid", 2, "adid", 3, "creativelnspect",
            4, "onoff", 5, "pname", 6, "imageurl", 7, "bid", 8, "usingbid",
            9, "linkstatus", 10, "regtm", 11, "deltm",
            12, "pid", 13, "pidofmall", 14, "productname", 15, "pimageurl",
            16, "plandingurl", 17, "mlandingurl", 18, "pprice", 19, "mprice",
            20, "deliveryfee", 21, "cname1", 22, "cname2", 23, "cname3", 24, "cname4",
            25, "categoryid1", 26, "categoryid2", 27, "categoryid3", 28, "categoryid4",
            29, "cnameofmall"
        ));
    }

    private static Map<Integer, String> map(Object... args) {
        Map<Integer, String> m = new HashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            m.put((Integer) args[i], (String) args[i + 1]);
        }
        return m;
    }

    public static List<Map<String, String>> parse(byte[] tsvBytes, String spec) {
        List<Map<String, String>> rows = new ArrayList<>();
        Map<Integer, String> colMap = COLUMN_SETS.get(spec);
        if (colMap == null) return rows;

        String content = new String(tsvBytes);
        for (String line : content.split("\n")) {
            if (line.isBlank()) continue;
            String[] cols = line.split("\t", -1);
            Map<String, String> row = new HashMap<>();
            for (Map.Entry<Integer, String> entry : colMap.entrySet()) {
                int idx = entry.getKey();
                row.put(entry.getValue(), idx < cols.length ? cols[idx].trim() : "");
            }
            if (!row.isEmpty()) rows.add(row);
        }
        return rows;
    }
}