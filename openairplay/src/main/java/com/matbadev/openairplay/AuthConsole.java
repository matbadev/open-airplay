package com.matbadev.openairplay;

import java.io.BufferedReader;
import java.io.InputStreamReader;

class AuthConsole implements Auth {

    public String getPassword(String hostname, String name) {
        String display = hostname != null && hostname.equals(name) ? hostname : name + " (" + hostname + ")";
        return waitforuser("Please input password for " + display);
    }

    private static String waitforuser() {
        return waitforuser("Press return to quit");
    }

    private static String waitforuser(String message) {
        System.out.println(message);
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        String s = null;
        try {
            while ((s = in.readLine()) != null && !(s.length() >= 0)) {
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return s;
    }

}
