package fr.orgpro.api.orgzly.utils;


import fr.orgpro.api.orgzly.OrgStringUtils;

import java.util.ArrayList;

public class ArrayListSpaceSeparated extends ArrayList<String> {
    private static final String DELIMITER = " ";

    public ArrayListSpaceSeparated() {
        super();
    }

    public ArrayListSpaceSeparated(String str) {
        for (String s: str.split(DELIMITER)) {
            String st = s.trim();
            if (st.length() > 0) {
                add(st);
            }
        }
    }

    public String toString() {
        return OrgStringUtils.join(this, DELIMITER);
    }
}
