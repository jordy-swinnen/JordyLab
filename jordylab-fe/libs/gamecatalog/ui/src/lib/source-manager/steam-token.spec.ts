import { readSteamToken } from './steam-token';

// Built at run time: a literal JWT-shaped string is what the secret scan is there to catch.
const TOKEN = ['eyJfake', 'x'.repeat(24), 'y'.repeat(24)].join('.');

describe('readSteamToken', () => {
  it('accepts a bare token and trims it', () => {
    expect(readSteamToken(`  ${TOKEN}\n`)).toEqual({ token: TOKEN });
  });

  it('takes the webapi_token out of the whole Steam config page', () => {
    const page = JSON.stringify({
      success: 1,
      data: { steamid: '1', webapi_token: TOKEN },
    });

    expect(readSteamToken(page)).toEqual({ token: TOKEN });
  });

  it('takes the token out of the Steam community client token page', () => {
    expect(
      readSteamToken(JSON.stringify({ logged_in: true, token: TOKEN })),
    ).toEqual({ token: TOKEN });
  });

  it('removes quotes and an access_token= prefix that came along with the copy', () => {
    expect(readSteamToken(`"${TOKEN}"`)).toEqual({ token: TOKEN });
    expect(readSteamToken(`access_token=${TOKEN}`)).toEqual({ token: TOKEN });
  });

  it('explains the empty page of a browser that is not signed in to the Steam store', () => {
    const result = readSteamToken('{"success":1,"data":[]}');

    expect(result).toEqual({
      problem: expect.stringContaining('not signed in to the Steam store'),
    });
  });

  it('refuses text that cannot be a token without sending it anywhere', () => {
    expect(readSteamToken('hello there')).toEqual({
      problem: expect.stringContaining('does not look like a Steam token'),
    });
    expect(readSteamToken('short')).toEqual({
      problem: expect.stringContaining('does not look like a Steam token'),
    });
    expect(readSteamToken('{not json')).toEqual({
      problem: expect.stringContaining('does not look like a Steam token'),
    });
  });
});
