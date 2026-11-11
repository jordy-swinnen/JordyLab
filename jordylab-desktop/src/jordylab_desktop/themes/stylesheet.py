"""Qt stylesheets for the six JordyLab themes.

Colour values mirror `jordylab-fe/themes.js` (the single source of truth on the
web side); keep them in sync. The desktop app renders the same themes with the
same tokens so the two surfaces feel like one product.
"""

TOKENS = {
    "content": {
        "ground": "#131314", "surface": "#1b1b1d", "line": "#2c2c2f",
        "text": "#f1f0ec", "text2": "#b0afab", "accent": "#ece8dd",
        "accent_ink": "#141414", "installed": "#8fd9b0",
    },
    "oxblood": {
        "ground": "#140d0e", "surface": "#1d1315", "line": "#36242a",
        "text": "#f0e7de", "text2": "#bcaaa4", "accent": "#ee5a70",
        "accent_ink": "#2a0509", "installed": "#6fd3a0",
    },
    "flare": {
        "ground": "#0d0b13", "surface": "#161320", "line": "#2a2537",
        "text": "#f2ede3", "text2": "#a8a1b5", "accent": "#ff6a3d",
        "accent_ink": "#1b0a04", "installed": "#2dd4bf",
    },
    "paper": {
        "ground": "#f2eee5", "surface": "#faf7f1", "line": "#d8d1c0",
        "text": "#1b1a1f", "text2": "#4a4952", "accent": "#2a43d6",
        "accent_ink": "#ffffff", "installed": "#1f7a4d",
    },
    "glasshouse": {
        "ground": "#eef3ee", "surface": "#f8fbf7", "line": "#cfdccf",
        "text": "#14231a", "text2": "#3d5245", "accent": "#1d6b4a",
        "accent_ink": "#ffffff", "installed": "#1d6b4a",
    },
    "heather": {
        "ground": "#f0eef5", "surface": "#faf9fd", "line": "#d9d4e6",
        "text": "#1d1830", "text2": "#4a4562", "accent": "#7a2e8e",
        "accent_ink": "#ffffff", "installed": "#1d7a52",
    },
}

_STYLESHEET = """
QWidget {{
    background-color: {ground};
    color: {text};
    font-family: "Helvetica Neue", "Segoe UI", "Inter", sans-serif;
    font-size: 13px;
}}
QLabel#Title {{
    font-size: 26px;
    font-weight: 700;
    color: {text};
}}
QLabel#Section {{
    font-size: 11px;
    font-weight: 600;
    letter-spacing: 1px;
    color: {text2};
    text-transform: uppercase;
}}
QLabel#Muted {{ color: {text2}; }}
QLabel#Badge {{
    color: {installed};
    border: 1px solid {installed};
    border-radius: 10px;
    padding: 2px 10px;
    font-size: 11px;
}}
QLabel#Error {{ color: {accent}; }}
QPushButton {{
    background-color: {surface};
    color: {text};
    border: 1px solid {line};
    border-radius: 10px;
    padding: 10px 14px;
    text-align: left;
}}
QPushButton:hover {{ border-color: {accent}; }}
QPushButton#Primary {{
    background-color: {accent};
    color: {accent_ink};
    border: none;
    font-weight: 600;
    text-align: center;
}}
QPushButton:disabled {{ color: {text2}; }}
QFrame#Card {{
    background-color: {surface};
    border: 1px solid {line};
    border-radius: 14px;
}}
QListWidget {{
    background-color: {surface};
    border: 1px solid {line};
    border-radius: 14px;
    padding: 6px;
}}
QListWidget::item {{ padding: 8px; border-radius: 8px; }}
QListWidget::item:selected {{ background-color: {line}; }}
QLineEdit {{
    background-color: {surface};
    color: {text};
    border: 1px solid {line};
    border-radius: 8px;
    padding: 8px;
}}
QProgressBar {{
    background-color: {surface};
    border: 1px solid {line};
    border-radius: 6px;
    height: 10px;
    text-align: center;
}}
QProgressBar::chunk {{ background-color: {installed}; border-radius: 6px; }}
QRadioButton {{ padding: 6px 0; }}
"""


def stylesheet(theme_id):
    tokens = TOKENS.get(theme_id, TOKENS["content"])

    return _STYLESHEET.format(**tokens)
