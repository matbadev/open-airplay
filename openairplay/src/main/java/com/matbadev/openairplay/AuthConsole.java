package com.matbadev.openairplay;

class AuthConsole implements Auth {

    public String getPassword(String hostname, String name) {
        String display = hostname == name ? hostname : name + " (" + hostname + ")";
        return AirPlay.waitforuser("Please input password for " + display);
    }

}
