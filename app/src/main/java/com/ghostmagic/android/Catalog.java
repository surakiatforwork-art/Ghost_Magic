package com.ghostmagic.android;

import java.util.List;

final class Catalog {
    record Capability(String type, String name, String description, String[] fields, String[] defaults) {}
    static final List<Capability> ALL = List.of(
        new Capability("trigger.manual", "Manual trigger", "Run explicitly while the editor is open.", new String[]{}, new String[]{}),
        new Capability("trigger.periodic", "Periodic trigger", "Deferrable background runs every 15 minutes or longer. Android may delay runs. Enable separately in Schedule; imports remain inactive.", new String[]{"intervalMinutes"}, new String[]{"15"}),
        new Capability("condition.variable.equals", "Variable equals", "Compare a run-local variable. TRUE and FALSE select different edges.", new String[]{"name", "value"}, new String[]{"mode", "home"}),
        new Capability("condition.battery.above", "Battery above", "Read the current battery percentage through the public Android API.", new String[]{"threshold"}, new String[]{"30"}),
        new Capability("action.variable.set", "Set variable", "Run-local string variable. Use ${name} in later action fields.", new String[]{"name", "value"}, new String[]{"mode", "home"}),
        new Capability("action.log", "Log message", "Write a message to this run's execution trace.", new String[]{"message"}, new String[]{"Hello from Ghost Magic"}),
        new Capability("action.delay", "Wait", "Cancellable delay in milliseconds, up to 60 seconds.", new String[]{"milliseconds"}, new String[]{"1000"}),
        new Capability("action.notification", "Show notification", "Android 13+ requires notification permission. Permission is checked at execution.", new String[]{"title", "message"}, new String[]{"Ghost Magic", "Flow completed"}),
        new Capability("action.brightness", "Set brightness", "Changes manual system brightness (0–100%). Requires Modify system settings access. Adaptive brightness must be off.", new String[]{"percent"}, new String[]{"50"}),
        new Capability("action.ocr.read", "OCR Text Reader", "Read Latin text offline into ${ocrText}. source: image or screen (Android 11+, enable screenshot access). region: full or x,y,width,height percentages after rotation. No Thai OCR in this version. Recognized text is saved in run history.", new String[]{"source","imageUri","outputVariable","script","region","rotation"}, new String[]{"screen","","ocrText","latin","full","0"}),
        new Capability("action.wifi.panel", "Open Wi-Fi controls", "User-assisted: opens Android's Wi-Fi panel. It does not toggle Wi-Fi or confirm a connection.", new String[]{}, new String[]{})
    );
    static Capability find(String type) {
        for (Capability c : ALL) if (c.type().equals(type)) return c;
        throw new IllegalArgumentException("Unsupported capability: " + type);
    }
    static String title(String type) { try { return find(type).name(); } catch (Exception e) { return type; } }
}
