from jordylab_scan import status


def test_write_and_read_last_run():
    status.write_last_run({"libraryType": "EMUDECK", "exitCode": 0, "outcome": "APPLIED"})

    run = status.read_last_run()

    assert run["libraryType"] == "EMUDECK"
    assert status.last_run_path().is_file()


def test_read_last_run_missing_returns_none():
    assert status.read_last_run() is None


def test_format_status_without_run():
    text = status.format_status(None, schedule_info="not installed", token_present=False)

    assert "Last run: never" in text
    assert "Automatic startup: not installed" in text
    assert "Session: not logged in" in text


def test_format_status_with_run_and_skipped_roots():
    run = {
        "libraryType": "STEAM",
        "finishedAt": "2026-01-01T00:00:00Z",
        "outcome": "PARTIAL",
        "exitCode": 5,
        "skippedRoots": ["/mnt/games"],
    }

    text = status.format_status(run, token_present=True, machine_id="m1")

    assert "outcome: PARTIAL" in text
    assert "partial result" in text
    assert "/mnt/games" in text
    assert "Machine id: m1" in text


def test_exit_message_unknown_code():
    assert status.exit_message(99).startswith("unknown result")
