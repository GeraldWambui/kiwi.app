import re
import json

def extract_colors(files):
    colors = set()
    for f in files:
        with open(f, 'r') as file:
            content = file.read()
            # Match hex colors
            hex_colors = re.findall(r'#[0-9A-Fa-f]{6}', content)
            colors.update(hex_colors)
            
            # Find rgba colors
            rgba_colors = re.findall(r'rgba\(.*?\)', content)
            colors.update(rgba_colors)
    return list(colors)

def extract_svgs(files):
    svgs = []
    for f in files:
        with open(f, 'r') as file:
            content = file.read()
            matches = re.findall(r'(<svg.*?</svg>)', content, re.DOTALL)
            svgs.extend(matches)
    
    # Simple deduplication based on content
    unique_svgs = list(set(svgs))
    return unique_svgs

files = ['library_light.html', 'library_dark.html', 'albums_light.html', 'albums_dark.html']

colors = extract_colors(files)
print("Extracted Colors:")
for c in sorted(colors):
    print(c)

print("\n---")
svgs = extract_svgs(files)
print(f"Extracted {len(svgs)} unique SVGs. Saving to svgs.txt")
with open('svgs.txt', 'w') as f:
    for i, svg in enumerate(svgs):
        f.write(f"\n--- SVG {i} ---\n")
        f.write(svg)
