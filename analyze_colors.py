import re
import json

def analyze_html(filename):
    print(f"--- {filename} ---")
    with open(filename, 'r') as f:
        content = f.read()
        
    colors = set(re.findall(r'#[0-9A-Fa-f]{6}', content))
    for c in colors:
        # Find where this color is used
        matches = re.finditer(r'([a-z\-]+):\s*' + c, content, re.IGNORECASE)
        usages = set([m.group(1) for m in matches])
        if usages:
            print(f"{c}: {', '.join(usages)}")

files = ['library_light.html', 'library_dark.html', 'albums_light.html', 'albums_dark.html']
for file in files:
    analyze_html(file)
