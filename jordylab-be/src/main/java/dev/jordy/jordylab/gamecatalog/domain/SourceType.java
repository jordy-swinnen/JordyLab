package dev.jordy.jordylab.gamecatalog.domain;

/** The kinds of library a scan client reports. Consoles are not scan sources (spec 013 FR-045). */
public enum SourceType {

    STEAM {
        @Override
        public String platform() {
            return "Steam";
        }
    },
    EMUDECK {
        @Override
        public String platform() {
            return "EmuDeck";
        }
    };

    /**
     * Default platform name associated with this source type. Used as the scan_source.platform column default; an
     * emulation scan reports the real platform per game (a SNES ROM is "SNES", not "EmuDeck").
     */
    public abstract String platform();
}
