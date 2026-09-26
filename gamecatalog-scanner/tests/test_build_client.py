import importlib.util
from pathlib import Path

TOOL = Path(__file__).resolve().parents[1] / "tools" / "build_client.py"

_spec = importlib.util.spec_from_file_location("build_client", TOOL)
build_client = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(build_client)


def test_generate_is_deterministic():
    assert build_client.generate() == build_client.generate()


def test_generated_file_begins_with_constants_header():
    text = build_client.generate()

    assert text.startswith("# Rendered by jordylab-be ClientService. Do not edit by hand.")
    assert 'LIBRARY_TYPE = "${LIBRARY_TYPE}"' in text


def test_no_placeholders_outside_header():
    assert build_client.placeholder_outside_header(build_client.generate()) is False


def test_strip_module_drops_intra_package_imports():
    source = (
        "import os\n"
        "from .config import thing\n"
        "from .config import (\n"
        "    one,\n"
        "    two,\n"
        ")\n"
        "from jordylab_scan import other\n"
        "x = 1\n"
    )

    stripped = build_client.strip_module(source)

    assert "from .config" not in stripped
    assert "from jordylab_scan" not in stripped
    assert "import os" in stripped
    assert stripped.endswith("x = 1")


def test_generated_file_compiles():
    compile(build_client.generate(), "jordylab-scan-template.py", "exec")


def test_local_render_replaces_placeholders():
    text = build_client.generate(local=True)

    assert 'KEYCLOAK_URL = "http://localhost:8180"' in text
    assert "${" not in text
