package dev.jordy.jordylab.gamecatalog.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Where a game's maximum number of local players came from. Never an AI guess. */
@Getter
@RequiredArgsConstructor
public enum PlayerCountSource {
    IGDB("IGDB"),
    THEGAMESDB("TheGamesDB");

    private final String displayName;
}
