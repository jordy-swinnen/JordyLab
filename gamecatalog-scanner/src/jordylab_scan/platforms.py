"""EmuDeck emulator-subfolder to platform-label mapping.

Verbatim port of ``EMUDECK_PLATFORM_LOOKUPS`` from the server's
``EmuDeckLibraryParser`` plus the capitalise-unknown fallback.
"""

EMUDECK_PLATFORM_LOOKUPS = {
    "snes": "SNES",
    "nes": "NES",
    "n64": "Nintendo 64",
    "gb": "Game Boy",
    "gbc": "Game Boy Color",
    "gba": "Game Boy Advance",
    "nds": "Nintendo DS",
    "3ds": "Nintendo 3DS",
    "gamecube": "GameCube",
    "gc": "GameCube",
    "wii": "Wii",
    "ps1": "PlayStation",
    "psx": "PlayStation",
    "psp": "PSP",
    "ps2": "PlayStation 2",
    "dreamcast": "Dreamcast",
    "dc": "Dreamcast",
    "saturn": "Saturn",
    "genesis": "Genesis",
    "megadrive": "Genesis",
    "sega32x": "Sega 32X",
    "gamegear": "Game Gear",
    "atari2600": "Atari 2600",
    "msu1": "SNES MSU-1",
}


def platform_for(emulator):
    """Display label for an EmuDeck subfolder name.

    Unmapped subfolders fall back to a capitalised version of the name so a
    non-standard layout still yields a readable label.
    """
    if not emulator:
        return emulator
    lowered = emulator.lower()
    mapped = EMUDECK_PLATFORM_LOOKUPS.get(lowered)
    if mapped is not None:
        return mapped

    return lowered[0].upper() + lowered[1:]
