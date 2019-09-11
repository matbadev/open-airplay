package com.matbadev.openairplay;

class Service {

    public String name;
    public String hostname;
    public int port;

    public Service(String hostname) {
        this(hostname, Config.PORT);
    }

    public Service(String hostname, int port) {
        this(hostname, port, hostname);
    }

    public Service(String hostname, int port, String name) {
        this.hostname = hostname;
        this.port = port;
        this.name = name;
    }

}
