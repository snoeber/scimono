package com.sap.scimono.scim.system.tests.util;

import com.mifmif.common.regex.Generex;
import java.util.ArrayList;

public class GlobalIdGenerator {

    private final String guidRegex = "[-+_:/.A-Za-z0-9]{1,36}";
    private final Generex generex = new Generex(guidRegex);

    private final ArrayList<String> usedGuids;

    public ArrayList<String> getUsedGuids() {
        return usedGuids;
    }

    public GlobalIdGenerator() {
        this.usedGuids = new ArrayList<>();
    }

    public void addToUsedGuids(ArrayList<String> additionalGuids) {
        usedGuids.addAll(additionalGuids);
    }

    public String getGuid(int minLength) {
        String guid = generex.random(minLength % 37);
        while (usedGuids.contains(guid)) {
            guid = generex.random(minLength);
        }
        usedGuids.add(guid);
        return guid;
    }

    public String getGuid() {
        int minLength = (int) (100 * Math.random() % 37);
        String guid = generex.random(minLength);
        while (usedGuids.contains(guid)) {
            guid = generex.random(minLength);
        }
        usedGuids.add(guid);
        return guid;
    }
}