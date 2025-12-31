package com.schoolproject;

import com.schoolproject.web.WebServer;
import com.schoolproject.db.MidiDBConnector;
import com.schoolproject.db.MidiDBOperations;

public class Main {

    public static void main(String[] args) {
        MidiDBConnector connector = new MidiDBConnector();
        MidiDBOperations ops = new MidiDBOperations(connector);

        WebServer server = new WebServer(ops);
        server.start();
    }
}
