"""Generate Kairo's SVG kit and Android vectors from one geometric master."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
BLUE = "#3367DB"
# Rounded ribbon K, with two open counters. Coordinates use a 100-unit canvas.
PATH = (
    "M53 24 L39 11 Q31 4 24 4 Q6 4 6 24 "
    "L6 77 Q6 96 25 96 L41 96 Q48 96 54 90 L62 82 "
    "L75 95 Q83 103 91 95 Q99 87 91 79 L62 50 "
    "L91 21 Q99 13 91 5 Q83 -3 75 5 Z "
    "M22 43 L22 24 Q22 19 27 21 L41 34 Z "
    "M27 72 L38 61 L57 80 L27 80 Q21 80 27 72 Z"
)

def svg(content, width=512, height=512):
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">{content}</svg>\n'

def mark(color, transform=""):
    return f'<path fill="{color}" fill-rule="evenodd" d="{PATH}" transform="{transform}"/>'

def write(name, content):
    (ROOT / "branding" / name).write_text(content)

def generate():
    for name, color in [("logo-blue", BLUE), ("logo-black", "#000000"), ("logo-white", "#FFFFFF")]:
        write(name + ".svg", svg(mark(color, "translate(52 40) scale(4.2)")))
    icon = f'<rect width="512" height="512" rx="112" fill="{BLUE}"/>'
    write("app-icon.svg", svg(icon + mark("#FFFFFF", "translate(118 108) scale(2.8)")))
    write("app-icon-circle.svg", svg(f'<circle cx="256" cy="256" r="256" fill="{BLUE}"/>' +
                                     mark("#FFFFFF", "translate(118 108) scale(2.8)")))
    for theme, foreground in [("light", "#182238"), ("dark", "#FFFFFF")]:
        header = mark(BLUE if theme == "light" else "#ADC6FF", "translate(28 34) scale(1.2)")
        header += f'<text x="182" y="105" font-family="sans-serif" font-size="72" font-weight="700" fill="{foreground}">Kairo</text>'
        header += f'<text x="185" y="144" font-family="sans-serif" font-size="23" fill="{foreground}">Recurring payments, in view.</text>'
        write(f"wordmark-{theme}.svg", svg(header, 760, 190))
    vector = (
        '<vector xmlns:android="http://schemas.android.com/apk/res/android" '
        'android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n'
        '    <group android:translateX="30" android:translateY="28" android:scaleX="0.5" android:scaleY="0.5">\n'
        f'        <path android:fillColor="#FFFFFF" android:fillType="evenOdd" android:pathData="{PATH}" />\n'
        '    </group>\n</vector>\n'
    )
    (ROOT / "app/src/main/res/drawable/ic_mark.xml").write_text(vector)
    (ROOT / "app/src/main/res/drawable/ic_notification.xml").write_text(vector)
    for name in ["app-icon", "app-icon-circle", "logo-blue", "logo-black", "logo-white"]:
        subprocess.run(["inkscape", str(ROOT / "branding" / f"{name}.svg"),
                        "-o", str(ROOT / "branding" / f"{name}.png")], check=True)

if __name__ == "__main__":
    generate()
