package dev.jordy.jordylab.gamecatalog.domain;

import lombok.experimental.UtilityClass;

import java.util.UUID;

@UtilityClass
class HostTestBuilder {

    public static final UUID DEFAULT_ID = UUID.fromString("5b8e2f4a-9c1d-4e7f-a3b6-0d2c4e6f8a1b");
    public static final String DEFAULT_HOSTNAME = "cachyos-htpc";
    public static final String DEFAULT_DISPLAY_NAME = "Living room PC";

    public static Host aDefaultHost() {
        return aHost().build();
    }

    public static Host.HostBuilder aHost() {
        return Host.builder()
                .id(DEFAULT_ID)
                .hostname(DEFAULT_HOSTNAME);
    }
}
